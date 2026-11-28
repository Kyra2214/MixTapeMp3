package com.cdvirtual.app.feature.youtube

import com.cdvirtual.app.core.database.VideoCacheEntity
import com.cdvirtual.app.core.diagnostics.AppDiagnostics
import com.cdvirtual.app.core.database.VideoCacheStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val preparingCacheIds = ConcurrentHashMap.newKeySet<String>()
    private val spaceMutex = Mutex()
    private val reservedSpaceBytes = ConcurrentHashMap<String, AtomicLong>()

    suspend fun prepare(
        source: VideoCacheResolvedSource,
        onProgress: (VideoCacheTransferProgress) -> Unit = {}
    ): VideoCacheEntity {
        val startedAt = System.currentTimeMillis()
        AppDiagnostics.record("VIDEO_CACHE", "Preparação solicitada", "videoId=${source.sourceVideoId} quality=${source.qualityHeight}p expectedBytes=${source.transferRequest.expectedBytes} mime=${source.transferRequest.mimeType}")
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
        AppDiagnostics.record("VIDEO_CACHE", "Reserva de cache definida", "cacheId=${item.cacheId} status=${item.status} new=$isNewReservation quality=${item.qualityHeight}p")
        require(item.qualityHeight == source.qualityHeight) {
            "Este vídeo já está reservado em ${item.qualityHeight}p. Remova o item para escolher outra qualidade."
        }
        if (item.status == VideoCacheStatus.INTERRUPTED) {
            val restored = manager.restoreReadyIfValid(item.cacheId)
            if (restored != null) return restored
            if (manager.find(item.cacheId)?.filePath != null) {
                throw IllegalStateException("O arquivo associado está corrompido. Apague este item manualmente antes de preparar outra cópia.")
            }
        }

        when (item.status) {
            VideoCacheStatus.READY, VideoCacheStatus.PAUSED, VideoCacheStatus.PLAYING -> return item
            VideoCacheStatus.DOWNLOADING ->
                throw IllegalStateException("Este vídeo já está sendo preparado.")
            VideoCacheStatus.QUEUED, VideoCacheStatus.INTERRUPTED -> Unit
            else -> throw IllegalStateException("Este item não pode ser preparado novamente.")
        }

        check(preparingCacheIds.add(item.cacheId)) { "Este vídeo já está na fila de preparação." }
        try {
            var missingBytes = 0L
            val hasCapacity = spaceMutex.withLock {
                val required = transfer.requiredFreeBytes(item.cacheId, source.transferRequest)
                val available = manager.capacity().availableBytes
                val reservedByOthers = reservedSpaceBytes.values.sumOf { it.get() }
                val availableAfterReservations = (available - reservedByOthers).coerceAtLeast(0L)
                missingBytes = (required - availableAfterReservations).coerceAtLeast(0L)
                if (missingBytes == 0L) {
                    reservedSpaceBytes[item.cacheId] = AtomicLong(required)
                    true
                } else false
            }
            if (!hasCapacity) {
                if (isNewReservation) manager.removeManually(item.cacheId)
                throw IllegalStateException("Espaço insuficiente: faltam $missingBytes bytes para preparar este vídeo.")
            }

            try {
                download(item.cacheId, source.transferRequest) { progress ->
                    val expectedTotal = source.transferRequest.expectedBytes +
                        (source.transferRequest.audioTrack?.expectedBytes ?: 0L)
                    if (expectedTotal > 0L) {
                        reservedSpaceBytes[item.cacheId]?.set(
                            (expectedTotal - progress.downloadedBytes).coerceAtLeast(0L)
                        )
                    }
                    onProgress(progress)
                }
            } finally {
                reservedSpaceBytes.remove(item.cacheId)
            }
            val completed = manager.find(item.cacheId)
                ?: throw IllegalStateException("O item desapareceu após a preparação.")
            AppDiagnostics.record("VIDEO_CACHE", "Preparação concluída", "cacheId=${item.cacheId} status=${completed.status} bytes=${completed.sizeBytes} elapsedMs=${System.currentTimeMillis() - startedAt}")
            return completed
        } catch (cancelled: CancellationException) {
            AppDiagnostics.record("WARN", "Preparação cancelada", "cacheId=${item.cacheId} elapsedMs=${System.currentTimeMillis() - startedAt}")
            // Também cobre cancelamento durante reserva de espaço ou resolução do fluxo,
            // antes de a transferência HTTP entrar no coordenador.
            withContext(NonCancellable) {
                runCatching { manager.markInterrupted(item.cacheId) }
            }
            throw cancelled
        } catch (failure: Exception) {
            AppDiagnostics.record("ERROR", "Falha na preparação do cache", "cacheId=${item.cacheId} exception=${failure.javaClass.name} message=${failure.message ?: "sem mensagem"}\n${failure.stackTraceToString()}")
            throw failure
        } finally {
            preparingCacheIds.remove(item.cacheId)
            reservedSpaceBytes.remove(item.cacheId)
        }
    }

    suspend fun download(
        cacheId: String,
        request: VideoCacheTransferRequest,
        onProgress: (VideoCacheTransferProgress) -> Unit = {}
    ) {
        check(activeCacheIds.add(cacheId)) { "Este vídeo já possui uma transferência ativa." }
        val startedAt = System.currentTimeMillis()
        AppDiagnostics.record("VIDEO_DOWNLOAD", "Download aguardando vaga", "cacheId=$cacheId expectedBytes=${request.expectedBytes} mime=${request.mimeType}")
        try {
            transferSemaphore.withPermit {
                AppDiagnostics.record("VIDEO_DOWNLOAD", "Download iniciado", "cacheId=$cacheId")
                manager.startDownload(cacheId)
                val partial = transfer.download(cacheId, request, onProgress)
                val published = manager.publishDownloadedFile(
                    cacheId = cacheId,
                    partialFile = partial,
                    extension = request.extension,
                    expectedBytes = if (request.audioTrack == null) request.expectedBytes else 0L
                )
                AppDiagnostics.record("VIDEO_DOWNLOAD", "Download publicado no cache", "cacheId=$cacheId bytes=${published.sizeBytes} status=${published.status} elapsedMs=${System.currentTimeMillis() - startedAt}")
            }
        } catch (cancelled: CancellationException) {
            AppDiagnostics.record("WARN", "Download cancelado", "cacheId=$cacheId elapsedMs=${System.currentTimeMillis() - startedAt}")
            // Também cobre cancelamento enquanto aguarda uma das três vagas.
            withContext(NonCancellable) {
                runCatching { manager.markInterrupted(cacheId) }
            }
            throw cancelled
        } catch (failure: Exception) {
            AppDiagnostics.record("ERROR", "Falha no download", "cacheId=$cacheId exception=${failure.javaClass.name} message=${failure.message ?: "sem mensagem"} elapsedMs=${System.currentTimeMillis() - startedAt}\n${failure.stackTraceToString()}")
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
