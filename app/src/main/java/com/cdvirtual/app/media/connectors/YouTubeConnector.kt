package com.cdvirtual.app.media.connectors

import java.io.File
import java.net.URL

/**
 * Importação de áudio a partir de link do YouTube.
 *
 * O download reaproveita [DirectDownloader]: as URLs do player apontam direto para o CDN, então
 * progresso, cancelamento, ".part" e detecção de download incompleto continuam valendo sem alteração.
 */
class YouTubeConnector : SourceConnector {
    override val id = "youtube"

    override fun supports(platform: Platform) = platform == Platform.YouTube

    override suspend fun analyze(url: URL): LinkInfo {
        val videoId = PlatformDetector.videoIdOf(url)
            ?: throw IllegalArgumentException("O link não aponta para um vídeo do YouTube (playlist e canal não são suportados).")
        return YouTubeApi.analyze(videoId)
    }

    override suspend fun download(option: MediaOption, target: File, onProgress: (Long, Long) -> Unit) {
        val headers = mapOf(
            "User-Agent" to "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip",
            "Accept" to "*/*",
            "Accept-Language" to "pt-BR,pt;q=0.9",
            "Referer" to "https://www.youtube.com/"
        )

        try {
            DirectDownloader.download(option, target, onProgress, headers)
        } catch (e: HttpStatusException) {
            throw YouTubeCdnException(e.code)
        }
    }
}
