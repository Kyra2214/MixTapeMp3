package com.cdvirtual.app.media.connectors

import com.cdvirtual.app.core.common.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

enum class LinkKind { Audio, NotAudio, Restricted }

/** Uma versão do áudio disponível para importação (formato/qualidade). */
data class MediaOption(
    val id: String,
    val label: String,
    val format: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val bitrateKbps: Int?,
    val url: String
)

data class LinkInfo(
    val finalUrl: String,
    val contentType: String?,
    val sizeBytes: Long,
    val fileName: String?,
    val kind: LinkKind,
    val options: List<MediaOption> = emptyList(),
    val message: String? = null
)

object LinkAnalyzer {
    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "oga", "opus", "wma", "amr", "mka")
    private val GENERIC_TYPES = setOf("application/octet-stream", "binary/octet-stream")
    private val DISPOSITION = Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)

    suspend fun analyze(url: URL): LinkInfo = withContext(Dispatchers.IO) {
        var conn = Http.open(url, "HEAD")
        if (conn.responseCode in listOf(403, 405, 501)) {
            conn.disconnect()
            conn = Http.open(url, "GET", mapOf("Range" to "bytes=0-0"))
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            val type = conn.contentType?.substringBefore(';')?.trim()?.lowercase()
            val name = fileNameOf(conn)
            val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
            val isAudio = type?.startsWith("audio/") == true || type == "application/ogg" ||
                ((type == null || type in GENERIC_TYPES) && ext in AUDIO_EXT)
            val size = sizeOf(conn)
            val finalUrl = conn.url.toString()
            val options = if (isAudio) listOf(directOption(finalUrl, type, ext, size)) else emptyList()
            val message = if (isAudio) null else
                "O link não aponta para um arquivo de áudio. Em serviços como o 4shared, use o link direto de " +
                    "download de um arquivo que você tem permissão para baixar."
            LinkInfo(finalUrl, type, size, name, if (isAudio) LinkKind.Audio else LinkKind.NotAudio, options, message)
        } finally {
            conn.disconnect()
        }
    }

    /** Link direto expõe uma única versão: o arquivo original, sem reconversão nem bitrate presumido. */
    private fun directOption(url: String, type: String?, ext: String, size: Long): MediaOption {
        val format = formatName(type, ext)
        val sizeText = if (size > 0) ", ${formatSize(size)}" else ""
        return MediaOption("direct", "Original ($format$sizeText)", format, type, size, null, url)
    }

    private fun formatName(type: String?, ext: String): String = when (type) {
        "audio/mpeg", "audio/mp3" -> "MP3"
        "audio/mp4", "audio/x-m4a", "audio/m4a" -> "M4A"
        "audio/aac", "audio/aacp" -> "AAC"
        "audio/flac", "audio/x-flac" -> "FLAC"
        "audio/wav", "audio/x-wav", "audio/wave" -> "WAV"
        "audio/ogg", "application/ogg" -> "OGG"
        "audio/opus" -> "OPUS"
        else -> if (ext.isNotEmpty()) ext.uppercase() else (type?.substringAfter('/')?.uppercase() ?: "ÁUDIO")
    }

    private fun sizeOf(conn: HttpURLConnection): Long {
        conn.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull()?.let { return it }
        return conn.contentLengthLong.coerceAtLeast(0)
    }

    private fun fileNameOf(conn: HttpURLConnection): String? {
        conn.getHeaderField("Content-Disposition")?.let { h ->
            DISPOSITION.find(h)?.groupValues?.get(1)?.let { return URLDecoder.decode(it.trim(), "UTF-8") }
        }
        val last = conn.url.path.substringAfterLast('/')
        return last.takeIf { it.contains('.') }?.let { URLDecoder.decode(it, "UTF-8") }
    }
}
