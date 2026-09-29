package org.balonmano.live.data

import android.content.Context
import android.util.AtomicFile
import org.balonmano.live.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

object MatchJson {
    fun encode(s: MatchState): String = JSONObject().apply {
        put("version", 1)
        put("teams", JSONArray().apply { s.teams.forEach { t -> put(JSONObject().apply {
            put("name", t.name); put("color", t.color); put("goals", t.goals); put("timeouts", t.timeouts)
        }) } })
        put("periods", JSONArray(s.periods)); put("period", s.period); put("elapsed", s.elapsedMs)
        put("timeoutDuration", s.timeoutDurationMs); put("extraDuration", s.extraDurationMs); put("nextId", s.nextId)
        put("exclusions", JSONArray().apply { s.exclusions.forEach { e -> put(JSONObject().apply {
            put("id", e.id); put("team", e.team); put("shirt", e.shirt); put("remaining", e.remainingMs)
        }) } })
        s.timeout?.let { put("timeout", JSONObject().apply { put("team", it.team); put("remaining", it.remainingMs) }) }
    }.toString()

    fun decode(text: String): MatchState {
        val json = JSONObject(text)
        require(json.getInt("version") == 1)
        val teamArray = json.getJSONArray("teams")
        require(teamArray.length() == 2)
        val teams = (0..1).map { i -> teamArray.getJSONObject(i).let {
            Team(it.getString("name"), it.getInt("color"), it.getInt("goals"), it.getInt("timeouts"))
        } }
        require(teams.all { it.name.isNotBlank() && it.name.length <= 24 && it.goals in 0..999 && it.timeouts in 0..99 })
        val array = json.getJSONArray("periods")
        require(array.length() in 2..1000)
        val periods = (0 until array.length()).map { array.getLong(it) }
        require(periods.all { it in 60_000..7_200_000 })
        val period = json.getInt("period")
        require(period in periods.indices)
        val elapsed = json.getLong("elapsed")
        require(elapsed in 0..periods[period])
        val exclusionArray = json.getJSONArray("exclusions")
        val exclusions = (0 until exclusionArray.length()).map { i -> exclusionArray.getJSONObject(i).let {
            Exclusion(it.getLong("id"), it.getInt("team"), it.getString("shirt"), it.getLong("remaining"))
        } }
        require(exclusions.all { it.id > 0 && it.team in 0..1 && it.shirt.length <= 3 && it.remainingMs in 1..7_200_000 })
        require(exclusions.map { it.id }.distinct().size == exclusions.size)
        val timeout = json.optJSONObject("timeout")?.let {
            TeamTimeout(it.getInt("team"), it.getLong("remaining"), running = false)
        }
        require(timeout == null || (timeout.team in 0..1 && timeout.remainingMs in 1..600_000))
        val timeoutDuration = json.getLong("timeoutDuration")
        val extraDuration = json.getLong("extraDuration")
        val nextId = json.getLong("nextId")
        require(timeoutDuration in 1_000..600_000 && extraDuration in 60_000..7_200_000)
        require(nextId > (exclusions.maxOfOrNull { it.id } ?: 0))
        return MatchState(teams, periods, period, elapsed, false, exclusions, timeout, timeoutDuration, extraDuration, nextId)
    }
}

class MatchStore(context: Context, private val onError: () -> Unit) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "match-v1.json"))
    private val writer = Executors.newSingleThreadExecutor()
    fun load(): MatchState {
        if (!file.baseFile.exists()) return MatchState()
        return try { MatchJson.decode(file.openRead().bufferedReader().use { it.readText() }) }
        catch (_: Exception) { onError(); MatchState() }
    }
    fun save(state: MatchState) {
        val bytes = MatchJson.encode(state).toByteArray(Charsets.UTF_8)
        writer.execute {
            var output: java.io.FileOutputStream? = null
            try { output = file.startWrite(); output.write(bytes); file.finishWrite(output) }
            catch (_: Exception) { file.failWrite(output); onError() }
        }
    }
    fun close() { writer.shutdown() }
}
