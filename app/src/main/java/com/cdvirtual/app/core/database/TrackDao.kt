package com.cdvirtual.app.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Insert
    suspend fun insert(track: TrackEntity): Long

    @Update
    suspend fun update(track: TrackEntity)

    @Delete
    suspend fun delete(track: TrackEntity)

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query(
        "SELECT * FROM tracks WHERE title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' " +
            "OR album LIKE '%' || :q || '%' ORDER BY title COLLATE NOCASE"
    )
    fun search(q: String): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE coverPath = :path")
    suspend fun countByCover(path: String): Int

    @Query("SELECT * FROM tracks WHERE lufs IS NULL OR bandBassDb IS NULL")
    suspend fun pendingAnalysis(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE contentHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): TrackEntity?
}
