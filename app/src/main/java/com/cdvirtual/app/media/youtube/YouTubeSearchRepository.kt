package com.cdvirtual.app.media.youtube

import com.cdvirtual.app.media.connectors.YouTubeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class YouTubeVideo(
    val videoId: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val url: String
)

/**
 * Pesquisa isolada da interface e do importador de áudio.
 * A extração usa o transporte NewPipe já configurado pelo conector existente.
 */
class YouTubeSearchRepository {
    suspend fun search(query: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        require(normalized.isNotEmpty()) { "Digite o que deseja pesquisar." }

        YouTubeApi.initialize()
        val service = NewPipe.getService("YouTube")
        val handler = service.searchQHFactory.fromQuery(normalized)
        val info = SearchInfo.getInfo(service, handler)

        info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                val url = item.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val videoId = extractVideoId(url) ?: return@mapNotNull null
                YouTubeVideo(
                    videoId = videoId,
                    title = item.name.orEmpty(),
                    channel = item.uploaderName.orEmpty(),
                    thumbnailUrl = item.thumbnails.firstOrNull()?.url,
                    durationSeconds = item.duration.coerceAtLeast(0L),
                    url = url
                )
            }
            .distinctBy { it.videoId }
    }

    private fun extractVideoId(rawUrl: String): String? = try {
        val uri = android.net.Uri.parse(rawUrl)
        uri.getQueryParameter("v")
            ?: uri.lastPathSegment?.takeIf { segment ->
                uri.host?.contains("youtu.be", ignoreCase = true) == true && segment.isNotBlank()
            }
            ?: Regex("(?:shorts|embed)/([A-Za-z0-9_-]{6,})").find(rawUrl)?.groupValues?.get(1)
    } catch (_: Exception) {
        null
    }
}
