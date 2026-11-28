package com.cdvirtual.app.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoCacheQualityTest {
    @Test
    fun keepsExactRequestedQualityWhenAvailable() {
        assertEquals(360, VideoCacheQuality.selectFallback(360, setOf(240, 360, 480)))
    }

    @Test
    fun choosesSmallestAvailableQualityAboveRequest() {
        assertEquals(480, VideoCacheQuality.selectFallback(360, setOf(240, 480)))
    }

    @Test
    fun choosesSmallestAvailableWhenRequestIsBelowAllStreams() {
        assertEquals(480, VideoCacheQuality.selectFallback(240, setOf(480, 720, 1080)))
    }

    @Test
    fun choosesHighestAvailableBelowRequestWhenNoHigherStreamExists() {
        assertEquals(360, VideoCacheQuality.selectFallback(480, setOf(240, 360)))
    }

    @Test
    fun returnsNullWhenNoQualityIsAvailable() {
        assertNull(VideoCacheQuality.selectFallback(360, emptySet()))
    }
}
