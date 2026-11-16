package com.cdvirtual.app.feature.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.cdvirtual.app.core.storage.readFileInfo
import java.io.File

class AudioInfo(
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val format: String?,
    val sizeBytes: Long,
    val cover: ByteArray?,
    val genre: String? = null
)

object AudioMetadataReader {
    /** Lança exceção se o arquivo não for um áudio legível. */
    fun read(context: Context, uri: Uri, fallbackTitle: String? = null): AudioInfo {
        val file = readFileInfo(context, uri)
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: throw IllegalArgumentException("Arquivo sem duração de áudio")
            val mime = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val actualSize = if (uri.scheme == "file") uri.path?.let { File(it).length() }?.takeIf { it > 0L } ?: file.sizeBytes else file.sizeBytes
            val embeddedTitle = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
            val fileTitle = file.displayName.substringBeforeLast('.').takeIf { it.isNotBlank() }
            return AudioInfo(
                title = embeddedTitle ?: fallbackTitle?.trim()?.takeIf { it.isNotEmpty() } ?: fileTitle ?: "Áudio sem título",
                artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.takeIf { it.isNotEmpty() },
                album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.takeIf { it.isNotEmpty() },
                durationMs = duration,
                format = formatOf(file.displayName, mime),
                sizeBytes = actualSize,
                cover = r.embeddedPicture,
                genre = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            )
        } finally {
            r.release()
        }
    }

    /** Só o gênero (usado para preencher músicas antigas da biblioteca); null se não houver. */
    fun readGenre(context: Context, uri: Uri): String? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
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