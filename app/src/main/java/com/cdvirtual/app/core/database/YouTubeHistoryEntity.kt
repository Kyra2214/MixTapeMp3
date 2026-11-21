package com.cdvirtual.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Histórico local da área YouTube.
 *
 * Não depende de autenticação Google nem compartilha dados com o histórico remoto.
 */
@Entity(tableName = "youtube_history")
data class YouTubeHistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val uploader: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long = 0,
    val lastPositionMs: Long = 0,
    val watchedAt: Long = System.currentTimeMillis(),
    val watchCount: Int = 1
)
