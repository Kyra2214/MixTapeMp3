package com.cdvirtual.app.feature.youtube

import com.cdvirtual.app.core.database.VideoCacheStateMachine
import com.cdvirtual.app.core.database.VideoCacheStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCacheSafetyTest {
    @Test
    fun contentRangeParserAcceptsValidCompleteRange() {
        assertEquals(
            VideoCacheContentRange(start = 1024L, endInclusive = 4095L, totalBytes = 4096L),
            parseVideoCacheContentRange("bytes 1024-4095/4096")
        )
    }

    @Test
    fun contentRangeParserRejectsMissingMalformedAndIncompleteValues() {
        assertNull(parseVideoCacheContentRange(null))
        assertNull(parseVideoCacheContentRange("bytes */4096"))
        assertNull(parseVideoCacheContentRange("bytes 9-4/10"))
        assertNull(parseVideoCacheContentRange("bytes 0-10/10"))
        assertNull(parseVideoCacheContentRange("bytes 0-9/*"))
        assertNull(parseVideoCacheContentRange("items 0-9/10"))
    }

    @Test
    fun contentRangeParserRejectsOverflowingNumbers() {
        assertNull(parseVideoCacheContentRange("bytes 0-999999999999999999999/1000000000000000000000"))
    }

    @Test
    fun stateMachineAllowsOnlyExpectedCacheLifecycleTransitions() {
        assertTrue(VideoCacheStateMachine.canTransition(VideoCacheStatus.QUEUED, VideoCacheStatus.DOWNLOADING))
        assertTrue(VideoCacheStateMachine.canTransition(VideoCacheStatus.DOWNLOADING, VideoCacheStatus.INTERRUPTED))
        assertTrue(VideoCacheStateMachine.canTransition(VideoCacheStatus.PLAYING, VideoCacheStatus.PAUSED))
        assertTrue(VideoCacheStateMachine.canTransition(VideoCacheStatus.PLAYING, VideoCacheStatus.COMPLETED))
        assertFalse(VideoCacheStateMachine.canTransition(VideoCacheStatus.PAUSED, VideoCacheStatus.COMPLETED))
        assertFalse(VideoCacheStateMachine.canTransition(VideoCacheStatus.COMPLETED, VideoCacheStatus.PLAYING))
        assertFalse(VideoCacheStateMachine.canTransition("UNKNOWN", VideoCacheStatus.READY))
    }
}
