package com.cdvirtual.app.feature.browser

import org.junit.Assert.assertEquals
import org.junit.AssertNull
import org.junit.Test

class BrowserYouTubeUrlTest {
    @Test
    fun extractsVideoIdFromStandardWatchUrl() {
        assertEquals("dQw4w9WgXcQ", BrowserYouTubeUrl.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123"))
    }

    @Test
    fun extractsVideoIdFromShortUrl() {
        assertEquals("dQw4w9WgXcQ", BrowserYouTubeUrl.extractVideoId("https://youtu.be/dQw4w9WgXcQ?t=12"))
    }

    @Test
    fun acceptsMobileYoutubeHost() {
        assertEquals("dQw4w9WgXcQ", BrowserYouTubeUrl.extractVideoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun rejectsNonYoutubeHosts() {
        assertNull(BrowserYouTubeUrl.extractVideoId("https://example.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun rejectsMalformedVideoIdsAndUrls() {
        assertNull(BrowserYouTubeUrl.extractVideoId("https://youtube.com/watch?v=short"))
        assertNull(BrowserYouTubeUrl.extractVideoId("not a url"))
        assertNull(BrowserYouTubeUrl.extractVideoId(null))
    }
}
