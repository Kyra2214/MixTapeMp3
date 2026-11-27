package com.cdvirtual.app.feature.youtube

import com.cdvirtual.app.core.database.VideoCacheDao
import com.cdvirtual.app.core.database.VideoCacheEntity
import com.cdvirtual.app.core.database.VideoCacheQuality
import com.cdvirtual.app.core.database.VideoCacheStatus
import com.cdvirtual.app.core.database.VideoCacheStateMachine
import kotlinx.coroutines.flow.Flow
import java.io.File
import com.cdvirtual.app.core.storage.VideoCacheFileStore

/**
 * Fachada de persistência do Cache Offline.
 *
 * Não baixa, resolve ou remove arquivos. O gerenciador de cache é o único componente
 * autorizado a manipular os arquivos físicos; este repositório mantém somente estado e metadados.
 */
class VideoCacheRepository(
    private val dao: VideoCacheDao,
    private val fileStore: VideoCacheFileStore
) {
    val items: Flow<List<VideoCacheEntity>> = dao.observeAll()

    override suspend fun all(): List<VideoCacheEntity> = dao.getAll()

    suspend fun find(cacheId: String): VideoCacheEntity? = dao.find(cacheId)

    suspend fun findBySource(sourceId: String, sourceVideoId: String): VideoCacheEntity? =
        dao.findBySource(sourceId, sourceVideoId)

    override suspend fun save(item: VideoCacheEntity) {
        require(item.cacheId.isNotBlank()) { "Identificador do cache vazio." }
        require(item.sourceId.isNotBlank()) { "Identificador da fonte vazio." }
        require(item.sourceVideoId.isNotBlank()) { "Identificador do vídeo vazio." }
        require(item.title.isNotBlank()) { "Título do vídeo vazio." }
        require(item.qualityHeight in VideoCacheQuality.supportedHeights) {
            "Qualidade não suportada. Use 240p, 360p ou 480p."
        }
        require(item.status in setOf(
            VideoCacheStatus.QUEUED, VideoCacheStatus.DOWNLOADING, VideoCacheStatus.INTERRUPTED,
            VideoCacheStatus.READY, VideoCacheStatus.PLAYING, VideoCacheStatus.PAUSED,
            VideoCacheStatus.COMPLETED, VideoCacheStatus.REMOVED
        )) { "Estado de cache desconhecido." }
        require(item.durationMs >= 0L && item.sizeBytes >= 0L && item.lastPositionMs >= 0L) {
            "Duração, tamanho e posição não podem ser negativos."
        }
        dao.upsert(item)
    }

    override suspend fun updateStatus(cacheId: String, status: String) {
        require(status != VideoCacheStatus.READY) {
            "Use markReadyAfterPromotion para publicar somente arquivos completos e íntegros."
        }
        val current = dao.find(cacheId) ?: throw IllegalArgumentException("Item de cache não encontrado.")
        require(VideoCacheStateMachine.canTransition(current.status, status)) {
            "Transição de cache inválida: ${current.status} → $status."
        }
        dao.updateStatus(cacheId, status, System.currentTimeMillis())
    }

    suspend fun updatePosition(cacheId: String, positionMs: Long) {
        dao.updatePosition(cacheId, positionMs.coerceAtLeast(0L), System.currentTimeMillis())
    }


    /**
     * Marca o item como disponível somente depois de validar que o arquivo publicado
     * existe, não está vazio e pertence ao diretório exclusivo de vídeos.
     */
    override suspend fun markReadyAfterPromotion(cacheId: String, publishedFile: File) {
        val current = dao.find(cacheId) ?: throw IllegalArgumentException("Item de cache não encontrado.")
        require(VideoCacheStateMachine.canTransition(current.status, VideoCacheStatus.READY)) {
            "O item não pode passar de ${current.status} para READY."
        }
        val validFile = fileStore.resolveValidFile(publishedFile.absolutePath)
            ?: throw IllegalArgumentException("O arquivo publicado não passou pela validação do cache.")
        val now = System.currentTimeMillis()
        dao.upsert(
            current.copy(
                filePath = validFile.absolutePath,
                sizeBytes = validFile.length(),
                status = VideoCacheStatus.READY,
                updatedAt = now
            )
        )
    }

    suspend fun countActive(): Int = dao.countActive()

    suspend fun totalBytes(): Long = dao.totalBytes()

    /** Chamar somente depois que o gerenciador confirmar a remoção do arquivo físico. */
    override suspend fun deleteRecordAfterFileRemoval(cacheId: String) {
        dao.delete(cacheId)
    }
}
