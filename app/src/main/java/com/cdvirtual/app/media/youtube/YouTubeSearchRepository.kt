package com.cdvirtual.app.media.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class YouTubeVideo(
    val videoId: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val url: String,
    val channelUrl: String? = null,
    val channelAvatarUrl: String? = null
)

data class YouTubeSearchPage(
    val videos: List<YouTubeVideo>,
    val nextPage: Page?
)

data class YouTubeChannelPage(
    val channelUrl: String,
    val channelName: String,
    val avatarUrl: String?,
    val videos: List<YouTubeVideo>,
    val nextPage: Page?,
    val tabHandler: ListLinkHandler
)

/**
 * Pesquisa, páginas de canais e paginação usando o mesmo NewPipe já adotado pelo app.
 * Conteúdo curto/Shorts é excluído intencionalmente da experiência MixTapeMp3.
 */
class YouTubeSearchRepository {
    suspend fun search(query: String): YouTubeSearchPage = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        require(normalized.isNotEmpty()) { "Digite o que deseja pesquisar." }

        YouTubeExtractor.initialize()
        val service = NewPipe.getService("YouTube")
        val handler = service.searchQHFactory.fromQuery(normalized)
        val info = SearchInfo.getInfo(service, handler)
        YouTubeSearchPage(
            videos = info.relatedItems.toVideos(),
            nextPage = info.nextPage.takeIf { info.hasNextPage() }
        )
    }

    suspend fun loadMore(query: String, page: Page): YouTubeSearchPage = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        require(normalized.isNotEmpty()) { "A pesquisa atual não é válida." }

        YouTubeExtractor.initialize()
        val service = NewPipe.getService("YouTube")
        val handler = service.searchQHFactory.fromQuery(normalized)
        val result = SearchInfo.getMoreItems(service, handler, page)
        YouTubeSearchPage(
            videos = result.items.toVideos(),
            nextPage = result.nextPage.takeIf { result.hasNextPage() }
        )
    }

    suspend fun loadChannel(channelUrl: String): YouTubeChannelPage = withContext(Dispatchers.IO) {
        require(channelUrl.isNotBlank()) { "O endereço do canal não é válido." }
        YouTubeExtractor.initialize()
        val service = NewPipe.getService("YouTube")
        val info = ChannelInfo.getInfo(service, channelUrl)
        val videosTab = info.tabs.firstOrNull()
            ?: throw IllegalStateException("Não foi possível encontrar a lista de vídeos deste canal.")
        val tab = ChannelTabInfo.getInfo(service, videosTab)
        YouTubeChannelPage(
            channelUrl = info.url,
            channelName = info.name.orEmpty(),
            avatarUrl = info.avatars.firstOrNull()?.url,
            videos = tab.relatedItems.toVideos(),
            nextPage = tab.nextPage.takeIf { tab.hasNextPage() },
            tabHandler = videosTab
        )
    }

    suspend fun loadMoreChannel(
        tabHandler: ListLinkHandler,
        page: Page
    ): YouTubeSearchPage = withContext(Dispatchers.IO) {
        YouTubeExtractor.initialize()
        val service = NewPipe.getService("YouTube")
        val result = ChannelTabInfo.getMoreItems(service, tabHandler, page)
        YouTubeSearchPage(
            videos = result.items.toVideos(),
            nextPage = result.nextPage
        )
    }

    private fun List<InfoItem>.toVideos(): List<YouTubeVideo> =
        filterIsInstance<StreamInfoItem>()
            .filterNot { it.isShortFormContent }
            .mapNotNull { item ->
                val url = item.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val videoId = extractVideoId(url) ?: return@mapNotNull null
                YouTubeVideo(
                    videoId = videoId,
                    title = item.name.orEmpty(),
                    channel = item.uploaderName.orEmpty(),
                    thumbnailUrl = item.thumbnails.firstOrNull()?.url,
                    durationSeconds = item.duration.coerceAtLeast(0L),
                    url = url,
                    channelUrl = item.uploaderUrl?.takeIf { it.isNotBlank() },
                    channelAvatarUrl = item.uploaderAvatars.firstOrNull()?.url
                )
            }
            .distinctBy { it.videoId }

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
