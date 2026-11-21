package com.cdvirtual.app.media.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.cdvirtual.app.media.youtube.YouTubeExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo

object YouTubeApi {
    private const val BASE_URL = "https://www.youtube.com/watch?v="
    fun initialize() = YouTubeExtractor.initialize()

    suspend fun analyze(videoId: String): LinkInfo = withContext(Dispatchers.IO) {
        ensureInitialized()
        val videoUrl = BASE_URL + videoId
        val info = try {
            StreamInfo.getInfo(videoUrl)
        } catch (e: Exception) {
            throw classifyFailure(e)
        }
        if (info.streamType.toString().contains("LIVE", ignoreCase = true)) {
            throw YouTubeUnavailableException(YouTubeFailure.LiveNotSupported)
        }

        val options = info.audioStreams.mapNotNull { stream ->
            val url = stream.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val format = stream.format ?: return@mapNotNull null
            val extension = format.suffix.takeIf { it.isNotBlank() }?.lowercase() ?: return@mapNotNull null
            val mime = format.mimeType
            // NewPipe já entrega averageBitrate/bitrate em kbps. Não dividir novamente por 1000.
            val bitrateKbps = stream.averageBitrate.takeIf { it > 0 } ?: stream.bitrate.takeIf { it > 0 }
            val size = bitrateKbps?.let { it.toLong() * 1000L * info.duration.coerceAtLeast(0L) / 8L } ?: 0L
            val formatName = extension.uppercase()
            val bitrateText = bitrateKbps?.let { " · $it kbps" } ?: ""
            MediaOption(
                id = stream.itag.takeIf { it > 0 }?.toString() ?: stream.formatId.toString(),
                label = "$formatName$bitrateText",
                format = extension,
                mimeType = mime,
                sizeBytes = size,
                bitrateKbps = bitrateKbps,
                url = url
            )
        }
        if (options.isEmpty()) throw YouTubeUnavailableException(YouTubeFailure.NoAudio)
        LinkInfo(
            finalUrl = videoUrl,
            contentType = null,
            sizeBytes = 0,
            fileName = info.name.takeIf { it.isNotBlank() },
            kind = LinkKind.Audio,
            options = options.distinctBy { it.url }.sortedByDescending { it.bitrateKbps ?: 0 }
        )
    }

    private fun classifyFailure(error: Exception): Exception {
        if (error is java.net.SocketException || error is java.net.SocketTimeoutException ||
            error is java.net.ConnectException || error is java.net.UnknownHostException ||
            error is javax.net.ssl.SSLException
        ) return error
        val description = (error.javaClass.simpleName + " " + (error.message ?: "")).lowercase()
        val failure = when {
            "age" in description || "idade" in description || "age restricted" in description -> YouTubeFailure.AgeRestricted
            "private" in description || "privado" in description -> YouTubeFailure.Private
            "live" in description || "ao vivo" in description -> YouTubeFailure.LiveNotSupported
            "country" in description || "region" in description || "país" in description || "região" in description -> YouTubeFailure.RegionBlocked
            else -> YouTubeFailure.PlayerChanged
        }
        return YouTubeUnavailableException(failure).also { it.initCause(error) }
    }
}