package com.cdvirtual.app.feature.youtube

import com.cdvirtual.app.core.database.VideoCacheDao
import com.cdvirtual.app.core.database.VideoCacheEntity
import com.cdvirtual.app.core.database.VideoCacheQuality
import com.cdvirtual.app.core.database.VideoCacheStatus
import kotlinx.coroutines.flow.Flow

/**
 * Fachada de persistência do Cache Offline.
 *
 * Não baixa, resolve ou remove arquivos. O gerenciador de cache é o único componente
 * autorizado a manipular os arquivos físicos; este repositório mantém somente estado e metadados.
 */
class VideoCacheRepository(private val dao: VideoCacheDao) {
    val items: Flow<List<VideoCacheEntity>> = dao.observeAll()

    suspend fun find(cacheId: String): VideoCacheEntity? = dao.find(cacheId)

    suspend fun findBySource(sourceId: String, sourceVideoId: String): VideoCacheEntity? =
        dao.findBySource(sourceId, sourceVideoId)

    suspend fun save(item: VideoCacheEntity) {
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

    suspend fun updateStatus(cacheId: String, status: String) {
        require(status in setOf(
            VideoCacheStatus.QUEUED, VideoCacheStatus.DOWNLOADING, VideoCacheStatus.INTERRUPTED,
            VideoCacheStatus.READY, VideoCacheStatus.PLAYING, VideoCacheStatus.PAUSED,
            VideoCacheStatus.COMPLETED, VideoCacheStatus.REMOVED
        )) { "Estado de cache desconhecido." }
        dao.updateStatus(cacheId, status, System.currentTimeMillis())
    }

    suspend fun updatePosition(cacheId: String, positionMs: Long) {
        dao.updatePosition(cacheId, positionMs.coerceAtLeast(0L), System.currentTimeMillis())
    }

    suspend fun countActive(): Int = dao.countActive()

    suspend fun totalBytes(): Long = dao.totalBytes()

    /** Chamar somente depois que o gerenciador confirmar a remoção do arquivo físico. */
    suspend fun deleteRecordAfterFileRemoval(cacheId: String) {
        dao.delete(cacheId)
    }
}
