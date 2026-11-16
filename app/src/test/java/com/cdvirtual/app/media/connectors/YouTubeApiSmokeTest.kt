package com.cdvirtual.app.media.connectors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URL

/**
 * Testes unitários determinísticos do reconhecimento de URLs do YouTube.
 *
 * A extração real depende dos servidores do YouTube e pode ser bloqueada por
 * verificações anti-bot (SignInConfirmNotBotException). Por isso, chamadas de
 * rede não devem fazer parte da suíte unitária executada em todo push.
 */
class YouTubeApiSmokeTest {

    @Test
    fun extractsVideoIdFromWatchUrl() {
        assertEquals("aqz-KE-bpKQ", PlatformDetector.videoIdOf(URL("https://www.youtube.com/watch?v=aqz-KE-bpKQ")))
    }

    @Test
    fun extractsVideoIdFromShortUrl() {
        assertEquals("aqz-KE-bpKQ", PlatformDetector.videoIdOf(URL("https://youtu.be/aqz-KE-bpKQ")))
    }

    @Test
    fun extractsVideoIdFromShortsUrl() {
        assertEquals("aqz-KE-bpKQ", PlatformDetector.videoIdOf(URL("https://www.youtube.com/shorts/aqz-KE-bpKQ")))
    }

    @Test
    fun rejectsChannelAndPlaylistUrls() {
        assertNull(PlatformDetector.videoIdOf(URL("https://www.youtube.com/@canal")))
        assertNull(PlatformDetector.videoIdOf(URL("https://www.youtube.com/playlist?list=PL123456")))
    }
}
