package com.cdvirtual.app.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Metadados persistentes de um item preparado para visualização offline.
 *
 * Não reutiliza TrackEntity: arquivos de vídeo pertencem exclusivamente ao domínio
 * da Central de Vídeos. O arquivo só é considerado disponível quando status=READY
 * e filePath aponta para um arquivo completo confirmado pelo gerenciador.
 */
@Entity(
    tableName = "video_cache",
    indices = [Index(value = ["status", "createdAt"])]
)
data class VideoCacheEntity(
    @PrimaryKey val cacheId: String,
    val sourceId: String,
    val sourceVideoId: String,
    val title: String,
    val uploader: String? = null,
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0L,
    val qualityHeight: Int = VideoCacheQuality.DEFAULT,
    val filePath: String? = null,
    val sizeBytes: Long = 0L,
    val status: String = VideoCacheStatus.QUEUED,
    val lastPositionMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

object VideoCacheQuality {
    const val P240 = 240
    const val P360 = 360
    const val P480 = 480
    const val DEFAULT = P240

    val supportedHeights = setOf(P240, P360, P480)
    fun isValidHeight(height: Int): Boolean = height in 1..4320

    /**
     * Retorna a resolução real a usar para uma qualidade preferida:
     * exata, senão a menor acima; se não existir acima, a maior abaixo.
     */
    fun selectFallback(requested: Int, available: Set<Int>): Int? {
        if (available.isEmpty()) return null
        return requested.takeIf { it in available }
            ?: available.filter { it > requested }.minOrNull()
            ?: available.filter { it < requested }.maxOrNull()
    }
}

object VideoCacheStatus {
    const val QUEUED = "QUEUED"
    const val DOWNLOADING = "DOWNLOADING"
    const val INTERRUPTED = "INTERRUPTED"
    const val READY = "READY"
    const val PLAYING = "PLAYING"
    const val PAUSED = "PAUSED"
    const val COMPLETED = "COMPLETED"
    const val REMOVED = "REMOVED"

    private val knownStatuses = setOf(
        QUEUED, DOWNLOADING, INTERRUPTED, READY,
        PLAYING, PAUSED, COMPLETED, REMOVED
    )

    fun isKnown(value: String): Boolean = value in knownStatuses
}
