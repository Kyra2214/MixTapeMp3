package com.cdvirtual.app.feature.youtube

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Orquestra exclusivamente downloads temporários da Central de Vídeos.
 * Falhas e cancelamentos preservam o parcial e deixam o item retomável.
 */
class VideoCacheDownloadCoordinator(
    private val manager: VideoCacheManager,
    private val transfer: VideoCacheTransfer
) {
    suspend fun download(
        cacheId: String,
        request: VideoCacheTransferRequest,
        onProgress: (VideoCacheTransferProgress) -> Unit = {}
    ): Unit {
        manager.startDownload(cacheId)
        try {
            val partial = transfer.download(cacheId, request, onProgress)
            manager.publishDownloadedFile(
                cacheId = cacheId,
                partialFile = partial,
                extension = request.extension,
                expectedBytes = request.expectedBytes
            )
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                runCatching { manager.markInterrupted(cacheId) }
            }
            throw cancelled
        } catch (failure: Exception) {
            withContext(NonCancellable) {
                runCatching { manager.markInterrupted(cacheId) }
            }
            throw failure
        }
    }
}
