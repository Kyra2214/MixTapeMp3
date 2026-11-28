package com.cdvirtual.app.media.youtube

import com.cdvirtual.app.core.diagnostics.AppDiagnostics
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

        val startedAt = System.currentTimeMillis()
        AppDiagnostics.record("VIDEO_SEARCH", "Pesquisa iniciada", "queryLength=${normalized.length}")
        try {
            YouTubeExtractor.initialize()
            val service = NewPipe.getService("YouTube")
            val handler = service.searchQHFactory.fromQuery(normalized)
            val info = SearchInfo.getInfo(service, handler)
            val videos = info.relatedItems.toVideos()
            AppDiagnostics.record("VIDEO_SEARCH", "Pesquisa concluída", "results=${videos.size} hasNext=${info.hasNextPage()} elapsedMs=${System.currentTimeMillis() - startedAt}")
            YouTubeSearchPage(videos = videos, nextPage = info.nextPage.takeIf { info.hasNextPage() })
        } catch (failure: Exception) {
            AppDiagnostics.record("ERROR", "Falha na pesquisa de vídeos", "exception=${failure.javaClass.name} message=${failure.message ?: "sem mensagem"} elapsedMs=${System.currentTimeMillis() - startedAt}\n${failure.stackTraceToString()}")
            throw failure
        }
    }

    suspend fun loadMore(query: String, page: Page): YouTubeSearchPage = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        require(normalized.isNotEmpty()) { "A pesquisa atual não é válida." }

        val startedAt = System.currentTimeMillis()
        AppDiagnostics.record("VIDEO_SEARCH", "Carregamento de mais resultados iniciado", "queryLength=${normalized.length}")
        try {
            YouTubeExtractor.initialize()
            val service = NewPipe.getService("YouTube")
            val handler = service.searchQHFactory.fromQuery(normalized)
            val result = SearchInfo.getMoreItems(service, handler, page)
            val videos = result.items.toVideos()
            AppDiagnostics.record("VIDEO_SEARCH", "Mais resultados carregados", "results=${videos.size} hasNext=${result.hasNextPage()} elapsedMs=${System.currentTimeMillis() - startedAt}")
            YouTubeSearchPage(videos = videos, nextPage = result.nextPage.takeIf { result.hasNextPage() })
        } catch (failure: Exception) {
            AppDiagnostics.record("ERROR", "Falha ao paginar pesquisa de vídeos", "exception=${failure.javaClass.name} message=${failure.message ?: "sem mensagem"} elapsedMs=${System.currentTimeMillis() - startedAt}\n${failure.stackTraceToString()}")
            throw failure
        }
    }

    suspend fun loadChannel(channelUrl: String): YouTubeChannelPage = withContext(Dispatchers.IO) {
        require(channelUrl.isNotBlank()) { "O endereço do canal não é válido." }
        val startedAt = System.currentTimeMillis()
        AppDiagnostics.record("VIDEO_SEARCH", "Abertura de canal iniciada")
        try {
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
        ).also { AppDiagnostics.record("VIDEO_SEARCH", "Canal carregado", "videos=${it.videos.size} hasNext=${it.nextPage != null} elapsedMs=${System.currentTimeMillis() - startedAt}") }
        } catch (failure: Exception) {
            AppDiagnostics.record("ERROR", "Falha ao carregar canal", "exception=${failure.javaClass.name} message=${failure.message ?: "sem mensagem"} elapsedMs=${System.currentTimeMillis() - startedAt}\n${failure.stackTraceToString()}")
            throw failure
        }
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
