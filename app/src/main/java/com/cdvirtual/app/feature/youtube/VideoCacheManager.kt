package com.cdvirtual.app.feature.youtube

import com.cdvirtual.app.core.database.VideoCacheEntity
import com.cdvirtual.app.core.database.VideoCacheQuality
import com.cdvirtual.app.core.database.VideoCacheStatus
import com.cdvirtual.app.core.storage.VideoCacheFileStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface VideoCacheReserveResult {
    data class Reserved(val item: VideoCacheEntity) : VideoCacheReserveResult
    data class AlreadyExists(val item: VideoCacheEntity) : VideoCacheReserveResult
    data class LimitReached(val current: Int, val limit: Int) : VideoCacheReserveResult
}

data class VideoCacheCapacity(
    val currentItems: Int,
    val maxItems: Int,
    val usedBytes: Long,
    val availableBytes: Long
)

/**
 * Coordena vagas, estados e arquivos do cache.
 *
 * Não resolve URLs nem baixa conteúdo. Um provedor separado deverá entregar somente
 * arquivos de fontes que autorizem armazenamento offline.
 */
class VideoCacheManager(
    private val repository: VideoCacheRepository,
    private val fileStore: VideoCacheFileStore,
    private val settings: VideoCacheSettingsStore
) {
    val items: Flow<List<VideoCacheEntity>> = repository.items
    private val reservationMutex = Mutex()

    suspend fun reserve(
        sourceId: String,
        sourceVideoId: String,
        title: String,
        uploader: String?,
        thumbnailUrl: String?,
        durationMs: Long,
        qualityHeight: Int = VideoCacheQuality.DEFAULT
    ): VideoCacheReserveResult = reservationMutex.withLock {
        require(sourceId.isNotBlank() && sourceVideoId.isNotBlank()) { "Fonte ou vídeo inválido." }
        require(title.isNotBlank()) { "O vídeo precisa ter um título." }
        require(durationMs >= 0L) { "Duração inválida." }
        require(qualityHeight in VideoCacheQuality.supportedHeights) { "Escolha 240p, 360p ou 480p." }

        val existing = repository.findBySource(sourceId, sourceVideoId)
        if (existing != null) return@withLock VideoCacheReserveResult.AlreadyExists(existing)

        val limit = settings.maxItems()
        val current = repository.countActive()
        if (current >= limit) return@withLock VideoCacheReserveResult.LimitReached(current, limit)

        val now = System.currentTimeMillis()
        val item = VideoCacheEntity(
            cacheId = UUID.randomUUID().toString(),
            sourceId = sourceId,
            sourceVideoId = sourceVideoId,
            title = title,
            uploader = uploader,
            thumbnailUrl = thumbnailUrl,
            durationMs = durationMs,
            qualityHeight = qualityHeight,
            status = VideoCacheStatus.QUEUED,
            createdAt = now,
            updatedAt = now
        )
        repository.save(item)
        VideoCacheReserveResult.Reserved(item)
    }

    suspend fun startDownload(cacheId: String) =
        repository.updateStatus(cacheId, VideoCacheStatus.DOWNLOADING)

    suspend fun markInterrupted(cacheId: String) =
        repository.updateStatus(cacheId, VideoCacheStatus.INTERRUPTED)

    suspend fun publishDownloadedFile(
        cacheId: String,
        partialFile: File,
        extension: String,
        expectedBytes: Long = 0L
    ): VideoCacheEntity {
        val published = fileStore.promotePartialFile(partialFile, extension, expectedBytes)
        try {
            // Depois do rename, concluir a gravação do estado sem deixar uma janela
            // de cancelamento entre o arquivo publicado e o registro READY.
            withContext(NonCancellable) {
                repository.markReadyAfterPromotion(cacheId, published)
            }
        } catch (failure: Throwable) {
            fileStore.deleteManagedFile(published.absolutePath)
            throw failure
        }
        return repository.find(cacheId) ?: throw IllegalStateException("Registro do cache desapareceu.")
    }

    suspend fun startPlayback(cacheId: String) =
        repository.updateStatus(cacheId, VideoCacheStatus.PLAYING)

    suspend fun pausePlayback(cacheId: String) =
        repository.updateStatus(cacheId, VideoCacheStatus.PAUSED)

    /** Chamado somente pelo player ao receber conclusão natural, nunca por seek manual. */
    suspend fun finishNaturally(cacheId: String): Boolean {
        val current = repository.find(cacheId) ?: return false
        require(current.status == VideoCacheStatus.PLAYING) {
            "Somente um vídeo em reprodução pode ser concluído."
        }
        repository.updateStatus(cacheId, VideoCacheStatus.COMPLETED)
        val completed = repository.find(cacheId) ?: return false
        val fileRemoved = completed.filePath.isNullOrBlank() ||
            fileStore.deleteManagedFile(completed.filePath)
        if (fileRemoved) repository.deleteRecordAfterFileRemoval(cacheId)
        return fileRemoved
    }

    /** Exclusão explícita do usuário, mesmo que o vídeo ainda não tenha sido assistido. */
    suspend fun removeManually(cacheId: String): Boolean {
        val item = repository.find(cacheId) ?: return false
        val finalRemoved = item.filePath.isNullOrBlank() || fileStore.deleteManagedFile(item.filePath)
        val partialRemoved = fileStore.deletePartialFile(cacheId)
        if (finalRemoved && partialRemoved) {
            repository.deleteRecordAfterFileRemoval(cacheId)
            return true
        }
        return false
    }

    fun setMaxItems(value: Int) = settings.setMaxItems(value)

    fun maxItems(): Int = settings.maxItems()

    suspend fun capacity(): VideoCacheCapacity {
        val directory = fileStore.cacheDirectory()
        if (!directory.exists()) directory.mkdirs()
        return VideoCacheCapacity(
            currentItems = repository.countActive(),
            maxItems = settings.maxItems(),
            usedBytes = repository.totalBytes(),
            availableBytes = directory.usableSpace.coerceAtLeast(0L)
        )
    }
}
