package com.cdvirtual.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface YouTubeChannelDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(channel: YouTubeChannelEntity): Long

    @Query("SELECT * FROM youtube_channels ORDER BY channelName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<YouTubeChannelEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM youtube_channels WHERE channelUrl = :channelUrl)")
    suspend fun contains(channelUrl: String): Boolean

    @Query("DELETE FROM youtube_channels WHERE channelUrl = :channelUrl")
    suspend fun remove(channelUrl: String)

    @Query("SELECT COUNT(*) FROM youtube_channels")
    suspend fun count(): Int
}
