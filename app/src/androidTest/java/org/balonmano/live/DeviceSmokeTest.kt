package org.balonmano.live

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpStream
import org.balonmano.live.data.CredentialsStore
import org.balonmano.live.data.StreamCredentials
import org.balonmano.live.domain.*
import org.balonmano.live.stream.ScoreboardFilter
import org.balonmano.live.ui.ScoreboardRenderer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun credentialsRoundTripThroughRealAndroidKeystore() {
        val store = CredentialsStore(context)
        // Synthetic, intentionally invalid YouTube credentials; this test never starts a network stream.
        val secret = "test-only-never-broadcast-1234"
        try {
            store.save(StreamCredentials("rtmps://a.rtmps.youtube.com:443/live2", secret))
            assertEquals(secret, store.load()!!.key)
            val disk = File(context.noBackupFilesDir, "youtube-credentials.enc").readText()
            assertFalse(disk.contains(secret)); assertFalse(disk.contains("youtube.com"))
            store.clear(); assertNull(store.load())
        } finally { store.clear() }
    }

    @Test fun renderFullScoreboardAndLongNamesWithoutOverflow() {
        val state = MatchState(teams = listOf(Team("ÁGUILAS DE COSTA RICA", 0xFF369EE8.toInt(), 27, 2), Team("BALONMANO UNIVERSITARIO", 0xFFE87848.toInt(), 26, 1)),
            period = 1, elapsedMs = 19 * 60_000 + 23_000, exclusions = listOf(Exclusion(1, 0, "14", 72_000), Exclusion(2, 1, "6", 45_000)),
            timeout = TeamTimeout(1, 42_000))
        val bitmap = ScoreboardRenderer().render(state)
        assertEquals(560, bitmap.width); assertEquals(272, bitmap.height)
        assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(559, 271)))
        File(context.filesDir, "scoreboard-proof.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun cameraAndPublicOverlayEncodeTo720pWithAudio() {
        val activity = instrumentation.startActivitySync(Intent(context, CaptureTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CaptureTestActivity
        val file = File(context.filesDir, "capture-proof.mp4")
        var encoder: RtmpStream? = null
        var filter: ScoreboardFilter? = null
        val state = MatchState(teams = listOf(Team("LOCAL", 0xFF369EE8.toInt(), 12), Team("VISITA", 0xFFE87848.toInt(), 9)), elapsedMs = 65_000)
        try {
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (!activity.surface.holder.surface.isValid && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            assertTrue(activity.surface.holder.surface.isValid)
            instrumentation.runOnMainSync {
                val callback = object : ConnectChecker {
                    override fun onConnectionStarted(url: String) = Unit
                    override fun onConnectionSuccess() = Unit
                    override fun onConnectionFailed(reason: String) = Unit
                    override fun onDisconnect() = Unit
                    override fun onAuthError() = Unit
                    override fun onAuthSuccess() = Unit
                }
                encoder = RtmpStream(context, callback)
                assertTrue(encoder!!.prepareVideo(1280, 720, 4_000_000, fps = 30, rotation = 0))
                assertTrue(encoder!!.prepareAudio(44100, false, 128_000))
                filter = ScoreboardFilter().apply { setScale(43.75f, 272 * 100f / 720); setPosition(2f, 3f); submit(ScoreboardRenderer().render(state)) }
                encoder!!.getGlInterface().setFilter(filter!!)
                encoder!!.startPreview(activity.surface)
                encoder!!.startRecord(file.absolutePath) { }
            }
            SystemClock.sleep(3500)
            instrumentation.runOnMainSync { filter!!.submit(ScoreboardRenderer().render(state.copy(teams = state.teams.mapIndexed { index, team -> if (index == 0) team.copy(goals = 13) else team }))) }
            SystemClock.sleep(3500)
            instrumentation.runOnMainSync { encoder!!.stopRecord() }
            assertTrue(file.length() > 10_000)
            val extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)
            val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
            val video = formats.first { it.getString(MediaFormat.KEY_MIME)!!.startsWith("video/") }
            assertEquals(1280, video.getInteger(MediaFormat.KEY_WIDTH)); assertEquals(720, video.getInteger(MediaFormat.KEY_HEIGHT))
            assertTrue(formats.any { it.getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm" })
            extractor.release()
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(5_000_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
            // An opaque scoreboard background must be present at the configured top-left position.
            File(context.filesDir, "encoded-proof.png").outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val pixel = frame.getPixel(40, 60)
            assertTrue("Background RGB=${android.graphics.Color.red(pixel)},${android.graphics.Color.green(pixel)},${android.graphics.Color.blue(pixel)}",
                android.graphics.Color.red(pixel) < 65 && android.graphics.Color.green(pixel) < 75)
            frame.recycle(); retriever.release()
        } finally {
            instrumentation.runOnMainSync { encoder?.release(); activity.finish() }
        }
    }
}
