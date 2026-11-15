package com.cdvirtual.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CdDao {
    @Insert
    suspend fun insert(cd: CdEntity): Long

    @Query("SELECT * FROM cds ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CdEntity>>

    @Query("SELECT * FROM cds WHERE id = :id")
    suspend fun getById(id: Long): CdEntity?

    @Update
    suspend fun update(cd: CdEntity)

    @Query("SELECT COUNT(*) FROM cds WHERE coverPath = :path")
    suspend fun countByCover(path: String): Int

    @Query(
        "SELECT t.* FROM tracks t INNER JOIN cd_tracks ct ON ct.trackId = t.id " +
            "WHERE ct.cdId = :cdId ORDER BY ct.position"
    )
    fun observeTracks(cdId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM cd_tracks WHERE cdId = :cdId ORDER BY position")
    suspend fun entries(cdId: Long): List<CdTrackEntity>

    @Query("DELETE FROM cd_tracks WHERE cdId = :cdId AND trackId = :trackId")
    suspend fun deleteEntry(cdId: Long, trackId: Long)

    @Update
    suspend fun updateEntries(items: List<CdTrackEntity>)

    @Query("SELECT COALESCE(MAX(position), -1) FROM cd_tracks WHERE cdId = :cdId")
    suspend fun maxPosition(cdId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracks(items: List<CdTrackEntity>)

    @Query("SELECT COUNT(*) FROM cds")
    suspend fun count(): Int

    /** T-205: remove o projeto; cd_tracks sai em cascata (FK). Músicas da biblioteca não são afetadas. */
    @Query("DELETE FROM cds WHERE id = :id")
    suspend fun delete(id: Long)
}
