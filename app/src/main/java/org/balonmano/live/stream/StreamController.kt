package org.balonmano.live.stream

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.CodecErrorCallback
import com.pedro.encoder.utils.CodecUtil
import android.media.MediaCodec
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.video.CameraCallbacks
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpStream
import org.balonmano.live.data.StreamCredentials
import org.balonmano.live.domain.MatchState
import org.balonmano.live.domain.clockText
import org.balonmano.live.ui.ScoreboardRenderer

enum class StreamPhase { IDLE, PREVIEW, CONNECTING, SENDING, RETRYING, ERROR }
data class StreamStatus(val phase: StreamPhase = StreamPhase.IDLE, val message: String = "Cámara sin iniciar", val bitrate: Long = 0)

class StreamController(private val context: Context, private val changed: () -> Unit, private val failed: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var stream: RtmpStream? = null
    private var filter: ScoreboardFilter? = null
    private val renderer = ScoreboardRenderer()
    private var match = MatchState()
    private var lastOverlay = ""
    private var epoch = 0
    private var attempts = 0
    private var surface: SurfaceView? = null
    var desired = false
        private set
    var status = StreamStatus()
        private set

    private fun publish(phase: StreamPhase, message: String, bitrate: Long = 0) {
        status = StreamStatus(phase, message, bitrate); changed()
    }

    private fun prepare(): RtmpStream {
        stream?.let { return it }
        val generation = ++epoch
        val checker = object : ConnectChecker {
            private fun current(block: () -> Unit) { main.post { if (epoch == generation && desired) block() } }
            override fun onConnectionStarted(url: String) = current { publish(if (attempts == 0) StreamPhase.CONNECTING else StreamPhase.RETRYING, if (attempts == 0) "Conectando con YouTube…" else "Reconectando · intento $attempts") }
            override fun onConnectionSuccess() = current { attempts = 0; publish(StreamPhase.SENDING, "Enviando a YouTube") }
            override fun onConnectionFailed(reason: String) = current { reconnect(reason) }
            override fun onDisconnect() = current { if (status.phase != StreamPhase.RETRYING) reconnect("Conexión interrumpida") }
            override fun onAuthError() = current { fatal("YouTube rechazó la conexión. Revisa tu clave y el directo.") }
            override fun onAuthSuccess() = Unit
            override fun onNewBitrate(bitrate: Long) = current {
                if (status.phase == StreamPhase.SENDING) { status = status.copy(bitrate = bitrate); changed() }
            }
        }
        val instance = RtmpStream(context, checker)
        instance.getGlInterface().autoHandleOrientation = true
        stream = instance
        try {
            instance.setVideoCodec(VideoCodec.H264)
            instance.setAudioCodec(AudioCodec.AAC)
            instance.setEncoderErrorCallback(object : CodecErrorCallback {
                override fun onCodecError(type: CodecUtil.CodecTypeError, e: MediaCodec.CodecException) {
                    main.post { if (epoch == generation) fatal("El teléfono interrumpió la codificación. El marcador se conserva; vuelve a iniciar el envío.") }
                }
                override fun onEncodeError(type: CodecUtil.CodecTypeError, e: IllegalStateException): Boolean {
                    main.post { if (epoch == generation) fatal("Se interrumpió la codificación. Vuelve a iniciar el envío.") }
                    return false
                }
            })
            instance.getStreamClient().apply {
                setLogs(false); setTlsHostVerification(true); setReTries(Int.MAX_VALUE)
                setCheckServerAlive(true); shouldFailOnRead(true); setSocketTimeout(10_000)
            }
            check(instance.prepareVideo(1280, 720, 4_000_000, fps = 30, iFrameInterval = 2, rotation = 0)
                && instance.prepareAudio(44100, false, 128_000))
            (instance.videoSource as Camera2Source).setCameraCallback(object : CameraCallbacks {
                override fun onCameraChanged(facing: CameraHelper.Facing) = Unit
                override fun onCameraOpened() = Unit
                override fun onCameraError(error: String) { main.post { if (epoch == generation) fatal("No se pudo abrir la cámara. Cierra otras apps que la estén usando.") } }
                override fun onCameraDisconnected() { main.post { if (epoch == generation) fatal("La cámara se desconectó. El marcador sigue guardado.") } }
            })
            filter = ScoreboardFilter().apply {
                // Pixel dimensions at 1280x720. A transparent lower area is reserved for penalties.
                setScale(ScoreboardRenderer.WIDTH * 100f / 1280, ScoreboardRenderer.HEIGHT * 100f / 720)
                setPosition(2f, 3f)
                submit(renderer.render(match))
            }
            instance.getGlInterface().setFilter(filter!!)
            return instance
        } catch (e: Exception) {
            destroyPipeline()
            throw IllegalStateException("No se pudo preparar cámara y audio a 720p. Revisa permisos y compatibilidad del teléfono.", e)
        }
    }

    fun attach(view: SurfaceView) {
        surface = view
        if (!view.holder.surface.isValid) return
        try {
            val encoder = prepare()
            if (!encoder.isOnPreview) encoder.startPreview(view)
            encoder.getGlInterface().setPreviewResolution(view.width, view.height)
            if (!desired) publish(StreamPhase.PREVIEW, "Vista previa · sin transmitir")
        } catch (_: Exception) { fatal("No se pudo iniciar la cámara. Revisa permisos y compatibilidad con 720p.") }
    }
    fun resize(width: Int, height: Int) { stream?.getGlInterface()?.setPreviewResolution(width, height) }
    fun detach() {
        surface = null
        try { stream?.let { if (it.isOnPreview) it.stopPreview() } }
        catch (_: Exception) { if (desired) fatal("Se interrumpió la cámara. Vuelve a iniciar la transmisión.") }
        if (!desired) destroyPipeline()
    }
    fun start(credentials: StreamCredentials) {
        if (desired) return
        val url = YouTubeEndpoint.endpoint(credentials.server, credentials.key)
        try {
            val encoder = prepare()
            desired = true; attempts = 0
            publish(StreamPhase.CONNECTING, "Conectando con YouTube…")
            encoder.startStream(url)
        } catch (_: Exception) { fatal("No se pudo iniciar la transmisión. Revisa la cámara, el micrófono y la conexión.") }
    }
    private fun reconnect(reason: String) {
        if (!desired) return
        attempts++
        val delay = (2_000L shl (attempts - 1).coerceAtMost(4)).coerceAtMost(30_000)
        publish(StreamPhase.RETRYING, "Sin conexión · reintento $attempts en ${delay / 1000}s")
        if (stream?.getStreamClient()?.reTry(delay, reason) != true) fatal("No se pudo reconectar. Revisa el directo y vuelve a iniciar.")
    }
    fun stop() {
        desired = false; attempts = 0
        val preview = surface
        destroyPipeline()
        publish(StreamPhase.IDLE, "Transmisión detenida")
        if (preview?.holder?.surface?.isValid == true) attach(preview)
    }
    private fun fatal(message: String) {
        desired = false
        destroyPipeline()
        publish(StreamPhase.ERROR, message)
        failed()
    }
    private fun destroyPipeline() {
        ++epoch // Queued callbacks from old camera/socket sessions are ignored.
        val old = stream
        stream = null; filter = null; lastOverlay = ""
        runCatching { old?.release() }
    }
    fun release() { desired = false; surface = null; destroyPipeline() }
    fun updateMatch(state: MatchState) {
        match = state
        val target = filter ?: return
        val signature = "${state.teams}|${state.period}|${clockText(state.totalElapsedMs)}|${state.running}|${state.periodEnded}|" +
            state.exclusions.joinToString { "${it.id}:${it.shirt}:${clockText(it.remainingMs, true)}" } +
            "|${state.timeout?.team}:${state.timeout?.let { clockText(it.remainingMs, true) }}"
        if (signature != lastOverlay) { target.submit(renderer.render(state)); lastOverlay = signature }
    }
    fun zoom(scale: Float) {
        (stream?.videoSource as? Camera2Source)?.let { source ->
            runCatching { val range = source.getZoomRange(); source.setZoom((source.getZoom() * scale).coerceIn(range.lower, range.upper)) }
        }
    }
}
