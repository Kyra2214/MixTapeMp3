package com.cdvirtual.app.feature.library

import com.cdvirtual.app.core.database.TrackDao
import com.cdvirtual.app.core.database.TrackEntity
import kotlinx.coroutines.flow.Flow

class TrackRepository(private val dao: TrackDao) {
    val tracks: Flow<List<TrackEntity>> = dao.observeAll()

    fun search(query: String): Flow<List<TrackEntity>> = dao.search(query)
    suspend fun coverUses(path: String): Int = dao.countByCover(path)

    suspend fun pendingAnalysis(): List<TrackEntity> = dao.pendingAnalysis()

    suspend fun pendingGenre(): List<TrackEntity> = dao.pendingGenre()

    /** Atualiza o registro lendo a versão mais recente antes de gravar. */
    suspend fun edit(id: Long, change: (TrackEntity) -> TrackEntity) {
        dao.getById(id)?.let { dao.update(change(it)) }
    }

    suspend fun add(track: TrackEntity): Long = dao.insert(track)
    suspend fun remove(track: TrackEntity) = dao.delete(track)
    suspend fun findByHash(hash: String): TrackEntity? = dao.getByHash(hash)
    suspend fun findByUri(uri: String): TrackEntity? = dao.getByUri(uri)
}
