package org.balonmano.live

import org.balonmano.live.data.MatchJson
import org.balonmano.live.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PersistenceTest {
    @Test fun roundTripKeepsAllMatchFieldsButPausesClocks() {
        val state = MatchState(teams = listOf(Team("Águilas", -1, 26, 2), Team("Costa Sur", -20, 21, 1)),
            periods = listOf(1_500_000, 1_500_000, 180_000), period = 2, elapsedMs = 90_000, running = true,
            exclusions = listOf(Exclusion(5, 1, "14", 93_500)), timeout = TeamTimeout(1, 18_000), nextId = 6,
            timeoutDurationMs = 45_000, extraDurationMs = 180_000)
        val serialized = MatchJson.encode(state)
        assertFalse(serialized.contains("key")); assertFalse(serialized.contains("server"))
        assertEquals(state.pausedForRecovery(), MatchJson.decode(serialized))
    }
    @Test fun corruptAndUnsupportedSnapshotsAreRejected() {
        assertThrows(Exception::class.java) { MatchJson.decode("not-json") }
        assertThrows(IllegalArgumentException::class.java) { MatchJson.decode(MatchJson.encode(MatchState()).replace("\"version\":1", "\"version\":999")) }
        assertThrows(IllegalArgumentException::class.java) { MatchJson.decode(MatchJson.encode(MatchState(period = 0)).replace("\"period\":0", "\"period\":999")) }
    }
}
