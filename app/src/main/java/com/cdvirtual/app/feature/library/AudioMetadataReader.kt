package com.cdvirtual.app.feature.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.cdvirtual.app.core.storage.readFileInfo

class AudioInfo(
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val format: String?,
    val sizeBytes: Long,
    val cover: ByteArray?
)

object AudioMetadataReader {
    /** Lança exceção se o arquivo não for um áudio legível. */
    fun read(context: Context, uri: Uri): AudioInfo {
        val file = readFileInfo(context, uri)
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: throw IllegalArgumentException("Arquivo sem duração de áudio")
            val mime = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            return AudioInfo(
                title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: file.displayName.substringBeforeLast('.'),
                artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.takeIf { it.isNotEmpty() },
                album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.takeIf { it.isNotEmpty() },
                durationMs = duration,
                format = formatOf(file.displayName, mime),
                sizeBytes = file.sizeBytes,
                cover = r.embeddedPicture
            )
        } finally {
            r.release()
        }
    }

    private fun formatOf(name: String, mime: String?): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty() && ext.length <= 5) return ext.uppercase()
        return mime?.substringAfter('/')?.uppercase()
    }
}
