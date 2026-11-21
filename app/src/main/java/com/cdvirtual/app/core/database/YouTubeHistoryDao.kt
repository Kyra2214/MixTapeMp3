package com.cdvirtual.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface YouTubeHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(item: YouTubeHistoryEntity)

    @Query("SELECT * FROM youtube_history ORDER BY watchedAt DESC")
    fun observeAll(): Flow<List<YouTubeHistoryEntity>>

    @Query("SELECT * FROM youtube_history WHERE videoId = :videoId LIMIT 1")
    suspend fun find(videoId: String): YouTubeHistoryEntity?

    @Query("""
        UPDATE youtube_history
        SET lastPositionMs = :positionMs
        WHERE videoId = :videoId
    """)
    suspend fun updateProgress(
        videoId: String,
        positionMs: Long
    )

    @Query("DELETE FROM youtube_history WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM youtube_history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM youtube_history")
    suspend fun count(): Int
}
