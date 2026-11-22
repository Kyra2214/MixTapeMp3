package com.cdvirtual.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Canal salvo pelo usuário na área YouTube.
 * A URL é a identidade estável usada para abrir a página real do canal.
 */
@Entity(tableName = "youtube_channels")
data class YouTubeChannelEntity(
    @PrimaryKey val channelUrl: String,
    val channelName: String,
    val avatarUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
