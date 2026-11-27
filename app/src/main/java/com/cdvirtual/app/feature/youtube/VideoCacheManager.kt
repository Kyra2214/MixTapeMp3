package com.cdvirtual.app.feature.youtube

import com.cdvirtual.app.core.database.VideoCacheEntity
import com.cdvirtual.app.core.database.VideoCacheQuality
import com.cdvirtual.app.core.database.VideoCacheStatus
import com.cdvirtual.app.core.storage.VideoCacheFileStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
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
    private val deletionMutex = Mutex()

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

    suspend fun find(cacheId: String): VideoCacheEntity? = repository.find(cacheId)

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
        val published = withContext(Dispatchers.IO) {
            fileStore.promotePartialFile(partialFile, extension, expectedBytes)
        }
        try {
            // Depois do rename, concluir a gravação do estado sem deixar uma janela
            // de cancelamento entre o arquivo publicado e o registro READY.
            withContext(NonCancellable + Dispatchers.IO) {
                repository.markReadyAfterPromotion(cacheId, published)
            }
        } catch (failure: Throwable) {
            // Diferenciar falha anterior ao commit de cancelamento ocorrido depois
            // que Room já gravou READY. Nunca mover um arquivo que o banco referencia.
            val readyWasPersisted = withContext(NonCancellable) {
                runCatching { repository.find(cacheId)?.status == VideoCacheStatus.READY }
                    .getOrDefault(false)
            }
            if (!readyWasPersisted) {
                withContext(Dispatchers.IO) {
                    runCatching { fileStore.rollbackPromotion(published, cacheId) }
                }
            }
            throw failure
        }
        return repository.find(cacheId) ?: throw IllegalStateException("Registro do cache desapareceu.")
    }

    suspend fun startPlayback(cacheId: String) =
        repository.updateStatus(cacheId, VideoCacheStatus.PLAYING)

    suspend fun pausePlayback(cacheId: String) =
        repository.updateStatus(cacheId, VideoCacheStatus.PAUSED)

    suspend fun updatePosition(cacheId: String, positionMs: Long) =
        repository.updatePosition(cacheId, positionMs)

    /** Chamado somente pelo player ao receber conclusão natural, nunca por seek manual. */
    suspend fun finishNaturally(cacheId: String): Boolean = deletionMutex.withLock {
        val current = repository.find(cacheId) ?: return@withLock false
        if (current.status != VideoCacheStatus.PLAYING) return@withLock false

        repository.updateStatus(cacheId, VideoCacheStatus.COMPLETED)
        val completed = repository.find(cacheId) ?: return@withLock false

        // Remove arquivo publicado e parcial residual apenas deste item.
        val (finalRemoved, partialRemoved) = withContext(Dispatchers.IO) {
            val final = completed.filePath.isNullOrBlank() ||
                fileStore.deleteManagedFile(completed.filePath)
            val partial = fileStore.deletePartialFile(cacheId)
            final to partial
        }
        if (finalRemoved && partialRemoved) {
            repository.deleteRecordAfterFileRemoval(cacheId)
            true
        } else {
            // Mantém o registro COMPLETED para recuperação ou nova tentativa manual.
            false
        }
    }

    /** Exclusão explícita do usuário, mesmo que o vídeo ainda não tenha sido assistido. */
    suspend fun removeManually(cacheId: String): Boolean = deletionMutex.withLock {
        val item = repository.find(cacheId) ?: return@withLock false
        // Registrar a intenção antes de remover bytes. Se o processo cair durante a
        // exclusão, a recuperação reconhecerá REMOVED e repetirá a limpeza com segurança.
        if (item.status != VideoCacheStatus.REMOVED) {
            repository.updateStatus(cacheId, VideoCacheStatus.REMOVED)
        }
        val (finalRemoved, partialRemoved) = withContext(Dispatchers.IO) {
            val final = item.filePath.isNullOrBlank() ||
                fileStore.deleteManagedFile(item.filePath)
            val partial = fileStore.deletePartialFile(cacheId)
            final to partial
        }
        if (!finalRemoved || !partialRemoved) return@withLock false
        repository.deleteRecordAfterFileRemoval(cacheId)
        true
    }

    fun setMaxItems(value: Int) = settings.setMaxItems(value)

    fun maxItems(): Int = settings.maxItems()

    suspend fun capacity(): VideoCacheCapacity {
        val availableBytes = withContext(Dispatchers.IO) {
            val directory = fileStore.cacheDirectory()
            if (!directory.exists()) directory.mkdirs()
            directory.usableSpace.coerceAtLeast(0L)
        }
        return VideoCacheCapacity(
            currentItems = repository.countActive(),
            maxItems = settings.maxItems(),
            usedBytes = repository.totalBytes(),
            availableBytes = availableBytes
        )
    }
}
