package com.cdvirtual.app.feature.youtube

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.cdvirtual.app.core.database.VideoCacheEntity
import com.cdvirtual.app.core.database.VideoCacheStatus

/**
 * Orquestra exclusivamente downloads temporários da Central de Vídeos.
 * Falhas e cancelamentos preservam o parcial e deixam o item retomável.
 */
class VideoCacheDownloadCoordinator(
    private val manager: VideoCacheManager,
    private val transfer: VideoCacheTransfer
) {
    suspend fun prepare(
        source: VideoCacheResolvedSource,
        onProgress: (VideoCacheTransferProgress) -> Unit = {}
    ): VideoCacheEntity {
        val item = when (val reservation = manager.reserve(
            sourceId = source.sourceId,
            sourceVideoId = source.sourceVideoId,
            title = source.title,
            uploader = source.uploader,
            thumbnailUrl = source.thumbnailUrl,
            durationMs = source.durationMs,
            qualityHeight = source.qualityHeight
        )) {
            is VideoCacheReserveResult.Reserved -> reservation.item
            is VideoCacheReserveResult.AlreadyExists -> reservation.item
            is VideoCacheReserveResult.LimitReached ->
                throw IllegalStateException("Limite do cache atingido (${reservation.current}/${reservation.limit}). Remova um item manualmente para continuar.")
        }

        when (item.status) {
            VideoCacheStatus.READY, VideoCacheStatus.PAUSED, VideoCacheStatus.PLAYING -> return item
            VideoCacheStatus.DOWNLOADING ->
                throw IllegalStateException("Este vídeo já está sendo preparado.")
            VideoCacheStatus.QUEUED, VideoCacheStatus.INTERRUPTED -> Unit
            else -> throw IllegalStateException("Este item não pode ser preparado novamente.")
        }

        val requiredBytes = transfer.requiredFreeBytes(item.cacheId, source.transferRequest.expectedBytes)
        val availableBytes = manager.capacity().availableBytes
        if (requiredBytes > availableBytes) {
            throw IllegalStateException("Espaço insuficiente: faltam ${requiredBytes - availableBytes} bytes para preparar este vídeo.")
        }

        download(item.cacheId, source.transferRequest, onProgress)
        return manager.find(item.cacheId)
            ?: throw IllegalStateException("O item desapareceu após a preparação.")
    }

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
