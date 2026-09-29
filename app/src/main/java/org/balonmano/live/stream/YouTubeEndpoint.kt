package org.balonmano.live.stream

import java.net.URI

object YouTubeEndpoint {
    const val DEFAULT_SERVER = "rtmps://a.rtmps.youtube.com:443/live2"
    fun server(input: String): String {
        val uri = try { URI(input.trim()) } catch (_: Exception) { throw IllegalArgumentException("Revisa el servidor de YouTube.") }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) { "Introduce solo el servidor de YouTube, sin clave." }
        require(uri.scheme in listOf("rtmp", "rtmps")) { "El servidor debe ser RTMP o RTMPS de YouTube." }
        val host = uri.host?.lowercase()
        require(host in listOf("a.rtmp.youtube.com", "b.rtmp.youtube.com", "a.rtmps.youtube.com", "b.rtmps.youtube.com")) { "Usa el servidor de emisión de YouTube Studio." }
        require(uri.path.trimEnd('/') == "/live2" && uri.port in listOf(-1, 443, 1935)) { "Introduce el servidor terminado en /live2, sin la clave." }
        return "rtmps://${host!!.replace(".rtmp.", ".rtmps.")}:443/live2"
    }
    fun endpoint(server: String, key: String): String {
        require(key.trim().matches(Regex("[A-Za-z0-9_-]{4,200}"))) { "Revisa la clave de transmisión de YouTube." }
        return "${server(server)}/${key.trim()}"
    }
}
