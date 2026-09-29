package org.balonmano.live.domain

import java.util.Locale

data class Team(val name: String, val color: Int, val goals: Int = 0, val timeouts: Int = 0)
data class Exclusion(val id: Long, val team: Int, val shirt: String, val remainingMs: Long)
data class TeamTimeout(val team: Int, val remainingMs: Long, val running: Boolean = true)

data class MatchState(
    val teams: List<Team> = listOf(Team("LOCAL", 0xFF369EE8.toInt()), Team("VISITA", 0xFFE87848.toInt())),
    val periods: List<Long> = listOf(30 * 60_000L, 30 * 60_000L),
    val period: Int = 0,
    val elapsedMs: Long = 0,
    val running: Boolean = false,
    val exclusions: List<Exclusion> = emptyList(),
    val timeout: TeamTimeout? = null,
    val timeoutDurationMs: Long = 60_000,
    val extraDurationMs: Long = 5 * 60_000,
    val nextId: Long = 1
) {
    val periodStartMs get() = periods.take(period).sum()
    val totalElapsedMs get() = periodStartMs + elapsedMs
    val periodEnded get() = elapsedMs >= periods[period]
    val periodLabel get() = if (period < 2) "${period + 1}T" else "P${period - 1}"
    fun pausedForRecovery() = copy(running = false, timeout = timeout?.copy(running = false))
}

sealed interface MatchAction {
    data class Goal(val team: Int, val delta: Int) : MatchAction
    data object ToggleClock : MatchAction
    data class CorrectClock(val totalMs: Long) : MatchAction
    data object NextPeriod : MatchAction
    data class AddExclusion(val team: Int, val shirt: String = "") : MatchAction
    data class EditExclusion(val id: Long, val remainingMs: Long, val shirt: String) : MatchAction
    data class RemoveExclusion(val id: Long) : MatchAction
    data class StartTimeout(val team: Int) : MatchAction
    data object ResumeTimeout : MatchAction
    data object EndTimeout : MatchAction
    data class CorrectTimeoutCount(val team: Int, val delta: Int) : MatchAction
    data class Configure(val names: List<String>, val colors: List<Int>, val periodMs: Long,
        val timeoutMs: Long, val extraMs: Long) : MatchAction
    data object Reset : MatchAction
}

/** All calls are serialized by the service's main looper. No network or wall-clock dependency. */
class MatchEngine(initial: MatchState = MatchState(), private val now: () -> Long) {
    var state = initial.pausedForRecovery()
        private set
    private var anchor = now()

    fun tick(): MatchState {
        val current = now()
        val delta = (current - anchor).coerceAtLeast(0)
        anchor = current
        val s = state
        state = when {
            s.timeout?.running == true -> {
                val remaining = (s.timeout.remainingMs - delta).coerceAtLeast(0)
                s.copy(timeout = if (remaining == 0L) null else s.timeout.copy(remainingMs = remaining))
            }
            s.running -> {
                val played = delta.coerceAtMost((s.periods[s.period] - s.elapsedMs).coerceAtLeast(0))
                val elapsed = s.elapsedMs + played
                s.copy(elapsedMs = elapsed, running = elapsed < s.periods[s.period],
                    exclusions = s.exclusions.map { it.copy(remainingMs = (it.remainingMs - played).coerceAtLeast(0)) }
                        .filter { it.remainingMs > 0 })
            }
            else -> s
        }
        return state
    }

    fun dispatch(action: MatchAction): MatchState {
        tick()
        val s = state
        state = when (action) {
            is MatchAction.Goal -> s.withTeam(action.team) { it.copy(goals = (it.goals + action.delta).coerceIn(0, 999)) }
            MatchAction.ToggleClock -> {
                require(s.timeout == null) { "Termina el tiempo muerto antes de reanudar." }
                require(!s.periodEnded) { "El período terminó. Pasa al siguiente." }
                s.copy(running = !s.running)
            }
            is MatchAction.CorrectClock -> {
                require(action.totalMs in s.periodStartMs..(s.periodStartMs + s.periods[s.period])) {
                    "El reloj debe quedar dentro del período actual."
                }
                // A correction is a display/game-clock correction, never elapsed playing time.
                s.copy(elapsedMs = action.totalMs - s.periodStartMs, running = false)
            }
            MatchAction.NextPeriod -> {
                require(s.periodEnded && s.timeout == null) { "Primero termina el período actual." }
                val periods = if (s.period + 1 == s.periods.size) s.periods + s.extraDurationMs else s.periods
                s.copy(periods = periods, period = s.period + 1, elapsedMs = 0, running = false)
            }
            is MatchAction.AddExclusion -> {
                require(action.team in 0..1)
                require(action.shirt.length <= 3) { "El dorsal admite hasta 3 caracteres." }
                s.copy(exclusions = s.exclusions + Exclusion(s.nextId, action.team, action.shirt, 120_000), nextId = s.nextId + 1)
            }
            is MatchAction.EditExclusion -> {
                require(action.remainingMs in 0..7_200_000) { "Duración no válida." }
                require(action.shirt.length <= 3) { "El dorsal admite hasta 3 caracteres." }
                s.copy(exclusions = s.exclusions.map {
                    if (it.id == action.id) it.copy(remainingMs = action.remainingMs, shirt = action.shirt) else it
                }.filter { it.remainingMs > 0 })
            }
            is MatchAction.RemoveExclusion -> s.copy(exclusions = s.exclusions.filterNot { it.id == action.id })
            is MatchAction.StartTimeout -> {
                require(s.timeout == null && !s.periodEnded) { "No se puede iniciar otro tiempo muerto ahora." }
                s.withTeam(action.team) { it.copy(timeouts = (it.timeouts + 1).coerceAtMost(99)) }
                    .copy(running = false, timeout = TeamTimeout(action.team, s.timeoutDurationMs))
            }
            MatchAction.ResumeTimeout -> s.copy(timeout = s.timeout?.copy(running = true))
            MatchAction.EndTimeout -> s.copy(timeout = null, running = false)
            is MatchAction.CorrectTimeoutCount -> s.withTeam(action.team) { it.copy(timeouts = (it.timeouts + action.delta).coerceIn(0, 99)) }
            is MatchAction.Configure -> {
                require(action.names.size == 2 && action.colors.size == 2)
                require(action.names.all { it.isNotBlank() && it.length <= 24 }) { "Cada equipo necesita un nombre de hasta 24 caracteres." }
                require(action.periodMs in 60_000..7_200_000 && action.timeoutMs in 1_000..600_000 && action.extraMs in 60_000..7_200_000) { "Revisa las duraciones." }
                val changeDuration = action.periodMs != s.periods[0]
                require(!changeDuration || (s.period == 0 && s.elapsedMs == 0L && !s.running && s.timeout == null)) { "La duración de los tiempos se cambia antes de empezar el partido." }
                s.copy(teams = s.teams.mapIndexed { index, team -> team.copy(name = action.names[index].trim(), color = action.colors[index] or 0xFF000000.toInt()) },
                    periods = if (changeDuration) listOf(action.periodMs, action.periodMs) else s.periods,
                    timeoutDurationMs = action.timeoutMs, extraDurationMs = action.extraMs)
            }
            MatchAction.Reset -> MatchState(teams = s.teams.map { it.copy(goals = 0, timeouts = 0) },
                periods = s.periods.take(2), timeoutDurationMs = s.timeoutDurationMs, extraDurationMs = s.extraDurationMs)
        }
        return state
    }

    private fun MatchState.withTeam(index: Int, change: (Team) -> Team): MatchState {
        require(index in 0..1)
        return copy(teams = teams.mapIndexed { i, team -> if (i == index) change(team) else team })
    }
}

fun clockText(ms: Long, countdown: Boolean = false): String {
    val seconds = ((ms.coerceAtLeast(0) + if (countdown) 999 else 0) / 1000)
    return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}
