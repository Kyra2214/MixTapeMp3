package com.cdvirtual.app.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Persistência exclusiva do Cache Offline de vídeos.
 * A remoção física dos arquivos é responsabilidade do gerenciador, nunca deste DAO.
 */
@Dao
interface VideoCacheDao {
    @Upsert
    suspend fun upsert(item: VideoCacheEntity)

    @Query("SELECT * FROM video_cache ORDER BY createdAt DESC")
    suspend fun getAll(): List<VideoCacheEntity>

    @Query("SELECT * FROM video_cache WHERE cacheId = :cacheId LIMIT 1")
    suspend fun find(cacheId: String): VideoCacheEntity?

    @Query("SELECT * FROM video_cache WHERE sourceId = :sourceId AND sourceVideoId = :sourceVideoId LIMIT 1")
    suspend fun findBySource(sourceId: String, sourceVideoId: String): VideoCacheEntity?

    @Query("SELECT * FROM video_cache ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VideoCacheEntity>>

    @Query("SELECT COUNT(*) FROM video_cache WHERE status != 'REMOVED'")
    suspend fun countActive(): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM video_cache WHERE status != 'REMOVED'")
    suspend fun totalBytes(): Long

    @Query("UPDATE video_cache SET status = :status, updatedAt = :updatedAt, completedAt = CASE WHEN :status = 'COMPLETED' THEN :updatedAt ELSE completedAt END WHERE cacheId = :cacheId")
    suspend fun updateStatus(cacheId: String, status: String, updatedAt: Long)

    @Query("UPDATE video_cache SET lastPositionMs = :positionMs, updatedAt = :updatedAt WHERE cacheId = :cacheId")
    suspend fun updatePosition(cacheId: String, positionMs: Long, updatedAt: Long)

    @Query("DELETE FROM video_cache WHERE cacheId = :cacheId")
    suspend fun delete(cacheId: String)
}
