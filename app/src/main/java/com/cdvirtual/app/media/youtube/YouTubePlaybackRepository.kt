package com.cdvirtual.app.media.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo

data class YouTubePlaybackInfo(
    val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val streamUrl: String,
    val mimeType: String?
)

/**
 * Resolve os streams usados exclusivamente pelo player da aba YouTube.
 * Não participa da importação de áudio para a biblioteca local.
 */
object YouTubePlaybackRepository {
    private const val BASE_URL = "https://www.youtube.com/watch?v="

    suspend fun resolve(videoId: String): YouTubePlaybackInfo = withContext(Dispatchers.IO) {
        YouTubeExtractor.initialize()
        val info = try {
            StreamInfo.getInfo(BASE_URL + videoId)
        } catch (failure: Exception) {
            throw classifyFailure(failure)
        }
        if (info.streamType.toString().contains("LIVE", ignoreCase = true)) {
            throw YouTubeUnavailableException(YouTubeFailure.LiveNotSupported)
        }

        val dashUrl = info.dashMpdUrl.takeIf { it.isNotBlank() }
        val progressive = info.videoStreams
            .filter { !it.isVideoOnly && it.isUrl }
            .maxByOrNull { it.height }
        val hlsUrl = info.hlsUrl.takeIf { it.isNotBlank() }
        val streamUrl = dashUrl
            ?: progressive?.content
            ?: hlsUrl
            ?: throw YouTubeUnavailableException(YouTubeFailure.PlayerChanged)
        val mimeType = when {
            dashUrl != null -> "application/dash+xml"
            progressive != null -> progressive.format?.mimeType
            else -> "application/x-mpegURL"
        }

        YouTubePlaybackInfo(
            videoId = videoId,
            title = info.name.orEmpty(),
            uploader = info.uploaderName.orEmpty(),
            thumbnailUrl = info.thumbnails.firstOrNull()?.url,
            durationSeconds = info.duration.coerceAtLeast(0L),
            streamUrl = streamUrl,
            mimeType = mimeType
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
