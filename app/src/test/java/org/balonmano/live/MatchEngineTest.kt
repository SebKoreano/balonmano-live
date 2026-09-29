package org.balonmano.live

import org.balonmano.live.domain.*
import org.junit.Assert.*
import org.junit.Test

class MatchEngineTest {
    private var now = 40_000L
    private fun engine(initial: MatchState = MatchState()) = MatchEngine(initial) { now }
    private fun advance(engine: MatchEngine, ms: Long) { now += ms; engine.tick() }

    @Test fun scoresAreIndependentAndNeverNegative() {
        val e = engine()
        e.dispatch(MatchAction.Goal(0, -1)); assertEquals(0, e.state.teams[0].goals)
        repeat(27) { e.dispatch(MatchAction.Goal(1, 1)) }
        e.dispatch(MatchAction.Goal(1, -1))
        assertEquals(26, e.state.teams[1].goals); assertEquals(0, e.state.teams[0].goals)
    }
    @Test fun clockUsesElapsedTimeInsteadOfCountingTicks() {
        val e = engine(); e.dispatch(MatchAction.ToggleClock)
        listOf(100L, 800L, 1999L, 12_345L).forEach { advance(e, it) }
        assertEquals(15_244L, e.state.elapsedMs)
        e.dispatch(MatchAction.ToggleClock); advance(e, 900_000)
        assertEquals(15_244L, e.state.elapsedMs)
    }
    @Test fun periodBoundaryStopsAtExactlyThirtyMinutesEvenAfterLateTick() {
        val e = engine(); e.dispatch(MatchAction.ToggleClock)
        advance(e, 31 * 60_000)
        assertEquals(30 * 60_000L, e.state.totalElapsedMs); assertFalse(e.state.running)
        e.dispatch(MatchAction.NextPeriod)
        assertEquals(30 * 60_000L, e.state.totalElapsedMs); assertFalse(e.state.running); assertEquals("2T", e.state.periodLabel)
    }
    @Test fun overlappingExclusionsOnlyConsumePlayedTime() {
        val e = engine(); e.dispatch(MatchAction.AddExclusion(0, "7")); e.dispatch(MatchAction.ToggleClock)
        advance(e, 30_000); e.dispatch(MatchAction.AddExclusion(1, "12")); advance(e, 20_000)
        assertEquals(listOf(70_000L, 100_000L), e.state.exclusions.map { it.remainingMs })
        e.dispatch(MatchAction.ToggleClock); advance(e, 200_000)
        assertEquals(listOf(70_000L, 100_000L), e.state.exclusions.map { it.remainingMs })
        e.dispatch(MatchAction.ToggleClock); advance(e, 70_000)
        assertEquals(1, e.state.exclusions.size); assertEquals("12", e.state.exclusions[0].shirt)
        assertEquals(30_000L, e.state.exclusions[0].remainingMs)
    }
    @Test fun exclusionSurvivesHalfTimeWithoutCountingTheBreak() {
        val e = engine(); e.dispatch(MatchAction.CorrectClock(29 * 60_000)); e.dispatch(MatchAction.AddExclusion(0, "9"))
        e.dispatch(MatchAction.ToggleClock); advance(e, 100_000)
        assertEquals(60_000L, e.state.exclusions[0].remainingMs)
        e.dispatch(MatchAction.NextPeriod); advance(e, 15 * 60_000)
        assertEquals(60_000L, e.state.exclusions[0].remainingMs)
        e.dispatch(MatchAction.ToggleClock); advance(e, 60_000)
        assertTrue(e.state.exclusions.isEmpty())
    }
    @Test fun correctingMainClockDoesNotRewritePenaltyRemainingTime() {
        val e = engine(); e.dispatch(MatchAction.AddExclusion(0)); e.dispatch(MatchAction.ToggleClock); advance(e, 10_000)
        e.dispatch(MatchAction.CorrectClock(500_000))
        assertEquals(110_000L, e.state.exclusions[0].remainingMs); assertFalse(e.state.running)
        e.dispatch(MatchAction.CorrectClock(1_000)); assertEquals(110_000L, e.state.exclusions[0].remainingMs)
    }
    @Test fun timeoutPausesGameAndExclusionsAndNeverAutomaticallyResumes() {
        val e = engine(); e.dispatch(MatchAction.AddExclusion(0)); e.dispatch(MatchAction.ToggleClock); advance(e, 25_000)
        e.dispatch(MatchAction.StartTimeout(1)); advance(e, 30_000)
        assertEquals(25_000L, e.state.elapsedMs); assertEquals(95_000L, e.state.exclusions[0].remainingMs)
        assertEquals(30_000L, e.state.timeout!!.remainingMs); assertEquals(1, e.state.teams[1].timeouts)
        advance(e, 60_000)
        assertNull(e.state.timeout); assertFalse(e.state.running); assertEquals(25_000L, e.state.elapsedMs)
    }
    @Test fun earlyTimeoutEndAndCounterCorrectionAreSeparate() {
        val e = engine(); e.dispatch(MatchAction.StartTimeout(0)); advance(e, 3_000)
        e.dispatch(MatchAction.EndTimeout); assertNull(e.state.timeout); assertEquals(1, e.state.teams[0].timeouts)
        e.dispatch(MatchAction.CorrectTimeoutCount(0, -1)); assertEquals(0, e.state.teams[0].timeouts)
    }
    @Test fun recoveryPausesEverythingWithoutAddingOfflineTime() {
        val initial = MatchState(elapsedMs = 30_000, running = true, exclusions = listOf(Exclusion(1, 1, "3", 66_000)), nextId = 2,
            timeout = TeamTimeout(0, 41_000))
        val e = engine(initial); advance(e, 24 * 60 * 60_000)
        assertEquals(30_000L, e.state.elapsedMs); assertEquals(41_000L, e.state.timeout!!.remainingMs)
        assertFalse(e.state.running); assertFalse(e.state.timeout!!.running)
        e.dispatch(MatchAction.ResumeTimeout); advance(e, 1000); assertEquals(40_000L, e.state.timeout!!.remainingMs)
    }
    @Test fun individualPenaltyCanBeEditedOrRemoved() {
        val e = engine(); e.dispatch(MatchAction.AddExclusion(0)); e.dispatch(MatchAction.AddExclusion(0))
        e.dispatch(MatchAction.EditExclusion(1, 75_000, "88"))
        assertEquals("88", e.state.exclusions[0].shirt); assertEquals(120_000L, e.state.exclusions[1].remainingMs)
        e.dispatch(MatchAction.RemoveExclusion(1)); assertEquals(2L, e.state.exclusions.single().id)
        e.dispatch(MatchAction.EditExclusion(2, 0, "")); assertTrue(e.state.exclusions.isEmpty())
    }
    @Test fun extraPeriodsKeepScoreAndCarryPenalty() {
        val e = engine(MatchState(period = 1, elapsedMs = 30 * 60_000, exclusions = listOf(Exclusion(1, 1, "4", 55_000)), nextId = 2,
            teams = listOf(Team("A", 1, 25), Team("B", 2, 25))))
        e.dispatch(MatchAction.NextPeriod)
        assertEquals("P1", e.state.periodLabel); assertEquals(60 * 60_000L, e.state.totalElapsedMs)
        assertEquals(5 * 60_000L, e.state.periods.last()); assertEquals(25, e.state.teams[1].goals)
        assertEquals(55_000L, e.state.exclusions[0].remainingMs)
    }
    @Test fun resettingPreservesSetupButClearsTheMatch() {
        val e = engine(); e.dispatch(MatchAction.Configure(listOf("Club azul", "Club rojo"), listOf(1, 2), 25 * 60_000, 45_000, 3 * 60_000))
        e.dispatch(MatchAction.Goal(0, 1)); e.dispatch(MatchAction.AddExclusion(0)); e.dispatch(MatchAction.StartTimeout(1)); e.dispatch(MatchAction.Reset)
        assertEquals("Club azul", e.state.teams[0].name); assertEquals(0, e.state.teams[0].goals)
        assertEquals(25 * 60_000L, e.state.periods[0]); assertEquals(45_000L, e.state.timeoutDurationMs)
        assertTrue(e.state.exclusions.isEmpty()); assertNull(e.state.timeout)
    }
    @Test fun fullNinetyMinuteSimulationHasNoAccumulatedTickDrift() {
        val e = engine()
        repeat(2) { half ->
            e.dispatch(MatchAction.ToggleClock)
            repeat(18_000) { advance(e, 100) }
            assertEquals((half + 1) * 30 * 60_000L, e.state.totalElapsedMs)
            if (half == 0) { advance(e, 15 * 60_000); e.dispatch(MatchAction.NextPeriod) }
        }
        e.dispatch(MatchAction.NextPeriod); e.dispatch(MatchAction.ToggleClock); advance(e, 300_000)
        e.dispatch(MatchAction.NextPeriod); e.dispatch(MatchAction.ToggleClock); advance(e, 300_000)
        advance(e, 5 * 60_000)
        assertEquals(70 * 60_000L, e.state.totalElapsedMs); assertEquals(90 * 60_000L + 40_000, now)
    }
    @Test fun countdownRoundsUpButGameClockRoundsDown() {
        assertEquals("02:00", clockText(119_999, true)); assertEquals("01:59", clockText(119_999))
        assertEquals("00:01", clockText(1, true)); assertEquals("00:00", clockText(0, true))
    }
    @Test(expected = IllegalArgumentException::class) fun cannotChangeHalfDurationAfterKickoff() {
        val e = engine(); e.dispatch(MatchAction.ToggleClock); advance(e, 1000)
        e.dispatch(MatchAction.Configure(listOf("A", "B"), listOf(1, 2), 20 * 60_000, 60_000, 300_000))
    }
    @Test(expected = IllegalArgumentException::class) fun cannotSkipAnUnfinishedPeriod() { engine().dispatch(MatchAction.NextPeriod) }
    @Test(expected = IllegalArgumentException::class) fun clockCorrectionCannotCrossPeriodBoundary() { engine().dispatch(MatchAction.CorrectClock(31 * 60_000)) }
    @Test(expected = IllegalArgumentException::class) fun duplicateTimeoutDoesNotIncrementCounter() {
        val e = engine(); e.dispatch(MatchAction.StartTimeout(0)); e.dispatch(MatchAction.StartTimeout(1))
    }
}
