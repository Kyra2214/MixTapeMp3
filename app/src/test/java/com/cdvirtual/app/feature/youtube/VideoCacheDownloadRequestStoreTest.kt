package com.cdvirtual.app.feature.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VideoCacheDownloadRequestStoreTest {
    private fun source(id: Int) = VideoCacheResolvedSource(
        sourceId = "test",
        sourceVideoId = "video-$id",
        title = "Video $id",
        uploader = null,
        thumbnailUrl = null,
        durationMs = 1_000L,
        qualityHeight = 360,
        transferRequest = VideoCacheTransferRequest(
            mediaUrl = "https://example.com/video-$id.mp4",
            mimeType = "video/mp4",
            extension = "mp4"
        )
    )

    @Test
    fun pendingRequestsNeverExceedConfiguredLimit() {
        VideoCacheDownloadRequestStore.clear()
        repeat(VideoCacheDownloadRequestStore.MAX_PENDING_REQUESTS + 8) {
            VideoCacheDownloadRequestStore.put(source(it))
        }
        assertEquals(VideoCacheDownloadRequestStore.MAX_PENDING_REQUESTS, VideoCacheDownloadRequestStore.sizeForTest())
        VideoCacheDownloadRequestStore.clear()
    }

    @Test
    fun tokenIsConsumedOnlyOnce() {
        VideoCacheDownloadRequestStore.clear()
        val token = VideoCacheDownloadRequestStore.put(source(1))
        assertNotNull(VideoCacheDownloadRequestStore.take(token))
        assertNull(VideoCacheDownloadRequestStore.take(token))
        VideoCacheDownloadRequestStore.clear()
    }

    @Test
    fun lifecycleCleanupRemovesUnconsumedTokens() {
        VideoCacheDownloadRequestStore.clear()
        VideoCacheDownloadRequestStore.put(source(2))
        assertEquals(1, VideoCacheDownloadRequestStore.sizeForTest())
        VideoCacheDownloadRequestStore.clear()
        assertEquals(0, VideoCacheDownloadRequestStore.sizeForTest())
    }
}
