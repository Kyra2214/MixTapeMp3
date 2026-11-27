package com.cdvirtual.app.feature.youtube

import com.cdvirtual.app.core.database.VideoCacheEntity
import com.cdvirtual.app.core.database.VideoCacheStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Orquestra exclusivamente downloads temporários da Central de Vídeos.
 * Falhas e cancelamentos preservam o parcial e deixam o item retomável.
 * O semáforo limita a três transferências simultâneas por padrão.
 */
class VideoCacheDownloadCoordinator(
    private val manager: VideoCacheManager,
    private val transfer: VideoCacheTransfer,
    maxConcurrentDownloads: Int = DEFAULT_MAX_CONCURRENT_DOWNLOADS
) {
    private val transferSemaphore = Semaphore(maxConcurrentDownloads.also { require(it > 0) })
    private val activeCacheIds = ConcurrentHashMap.newKeySet<String>()

    suspend fun prepare(
        source: VideoCacheResolvedSource,
        onProgress: (VideoCacheTransferProgress) -> Unit = {}
    ): VideoCacheEntity {
        val reservation = manager.reserve(
            sourceId = source.sourceId,
            sourceVideoId = source.sourceVideoId,
            title = source.title,
            uploader = source.uploader,
            thumbnailUrl = source.thumbnailUrl,
            durationMs = source.durationMs,
            qualityHeight = source.qualityHeight
        )
        val item = when (reservation) {
            is VideoCacheReserveResult.Reserved -> reservation.item
            is VideoCacheReserveResult.AlreadyExists -> reservation.item
            is VideoCacheReserveResult.LimitReached ->
                throw IllegalStateException("Limite do cache atingido (${reservation.current}/${reservation.limit}). Remova um item manualmente para continuar.")
        }
        val isNewReservation = reservation is VideoCacheReserveResult.Reserved

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
            if (isNewReservation) manager.removeManually(item.cacheId)
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
    ) {
        check(activeCacheIds.add(cacheId)) { "Este vídeo já possui uma transferência ativa." }
        try {
            transferSemaphore.withPermit {
                manager.startDownload(cacheId)
                val partial = transfer.download(cacheId, request, onProgress)
                manager.publishDownloadedFile(
                    cacheId = cacheId,
                    partialFile = partial,
                    extension = request.extension,
                    expectedBytes = request.expectedBytes
                )
            }
        } catch (cancelled: CancellationException) {
            // Também cobre cancelamento enquanto aguarda uma das três vagas.
            withContext(NonCancellable) {
                runCatching { manager.markInterrupted(cacheId) }
            }
            throw cancelled
        } catch (failure: Exception) {
            withContext(NonCancellable) {
                runCatching { manager.markInterrupted(cacheId) }
            }
            throw failure
        } finally {
            activeCacheIds.remove(cacheId)
        }
    }

    companion object {
        const val DEFAULT_MAX_CONCURRENT_DOWNLOADS = 3
    }
}
