package org.balonmano.live

import org.balonmano.live.stream.YouTubeEndpoint
import org.junit.Assert.*
import org.junit.Test

class YouTubeEndpointTest {
    @Test fun upgradesCopiedYouTubeRtmpAddressToTls() {
        assertEquals("rtmps://a.rtmps.youtube.com:443/live2/abcd-efgh", YouTubeEndpoint.endpoint("rtmp://a.rtmp.youtube.com/live2", " abcd-efgh "))
    }
    @Test fun handlesSecureBackupAndTrailingSlash() {
        assertEquals("rtmps://b.rtmps.youtube.com:443/live2", YouTubeEndpoint.server("rtmps://b.rtmps.youtube.com/live2/"))
    }
    @Test fun refusesNonYouTubeHostsAndUrlCredentials() {
        listOf("https://a.rtmp.youtube.com/live2", "rtmps://a.rtmps.youtube.com.evil.example/live2",
            "rtmps://user:pass@a.rtmps.youtube.com/live2", "rtmps://localhost/live2", "rtmps://a.rtmps.youtube.com/live2/key",
            "rtmps://a.rtmps.youtube.com/live2?secret=key", "rtmps://a.rtmps.youtube.com:8000/live2").forEach {
            assertThrows(IllegalArgumentException::class.java) { YouTubeEndpoint.server(it) }
        }
    }
    @Test fun refusesKeysWithControlCharactersPathsAndWhitespace() {
        listOf("", "abcd\ndefg", "abcd/efgh", "abcd?key=secret", "ab cd", "x".repeat(201)).forEach {
            assertThrows(IllegalArgumentException::class.java) { YouTubeEndpoint.endpoint(YouTubeEndpoint.DEFAULT_SERVER, it) }
        }
    }
}
