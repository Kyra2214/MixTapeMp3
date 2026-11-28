package com.cdvirtual.app.feature.youtube

import com.cdvirtual.app.core.database.VideoCacheQuality
import com.cdvirtual.app.media.youtube.YouTubeExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Locale

/**
 * Provedor de cache temporário baseado no NewPipeExtractor, o mesmo extrator já usado
 * na importação de áudio por link. Só aceita streams progressivos (áudio + vídeo):
 * stream video-only do DASH toca sem som.
 *
 * As qualidades oferecidas são as que o vídeo realmente possui dentro de
 * 240p/360p/480p, e a altura devolvida é sempre a real do arquivo.
 */
class NewPipeVideoCacheProvider : VideoCacheSourceProvider {
    override val sourceId: String = SOURCE_ID

    override suspend fun availableQualities(sourceVideoId: String): Set<Int> =
        withContext(Dispatchers.IO) {
            val info = infoOf(sourceVideoId) ?: return@withContext emptySet()
            candidates(info).map { it.first }.toSet()
        }

    override suspend fun resolve(
        sourceVideoId: String,
        qualityHeight: Int
    ): VideoCacheResolvedSource? = withContext(Dispatchers.IO) {
        val info = infoOf(sourceVideoId) ?: return@withContext null
        val stream = candidates(info).firstOrNull { it.first == qualityHeight }?.second
            ?: return@withContext null

        val url = stream.url?.takeIf { it.isNotBlank() } ?: return@withContext null
        val mime = stream.format?.mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: return@withContext null
        val extension = when (mime) {
            "video/mp4" -> "mp4"
            "video/webm", "video/x-matroska" -> "webm"
            else -> return@withContext null
        }

        VideoCacheResolvedSource(
            sourceId = SOURCE_ID,
            sourceVideoId = sourceVideoId,
            title = info.name?.takeIf { it.isNotBlank() } ?: "Vídeo do YouTube",
            uploader = info.uploaderName,
            thumbnailUrl = thumbnailOf(info),
            durationMs = info.duration.coerceAtLeast(0L) * 1000L,
            qualityHeight = qualityHeight,
            transferRequest = VideoCacheTransferRequest(
                mediaUrl = url,
                mimeType = mime,
                extension = extension,
                headers = REQUEST_HEADERS,
                expectedBytes = contentLengthOf(stream)
            )
        )
    }

    private fun infoOf(videoId: String): StreamInfo? = runCatching {
        YouTubeExtractor.initialize()
        StreamInfo.getInfo(WATCH_URL + videoId)
    }.getOrNull()

    /** Somente streams progressivos com altura dentro do contrato do cache. */
    private fun candidates(info: StreamInfo): List<Pair<Int, VideoStream>> =
        info.videoStreams
            .asSequence()
            .filter { !it.isVideoOnly }
            .mapNotNull { stream ->
                val height = stream.resolution
                    ?.takeWhile { it.isDigit() }
                    ?.toIntOrNull()
                    ?: return@mapNotNull null
                if (height !in VideoCacheQuality.supportedHeights) return@mapNotNull null
                height to stream
            }
            .distinctBy { it.first }
            .sortedBy { it.first }
            .toList()

    /**
     * A API de thumbnail mudou entre versões do NewPipe (getThumbnailUrl -> getThumbnails).
     * Resolvido por reflexão para compilar em qualquer uma; ausente, fica nulo.
     */
    private fun thumbnailOf(info: StreamInfo): String? =
        runCatching {
            val images = StreamInfo::class.java.methods
                .firstOrNull { it.name == "getThumbnails" && it.parameterCount == 0 }
                ?.invoke(info) as? List<*>
            images?.asSequence()
                ?.mapNotNull { image ->
                    image?.javaClass?.methods
                        ?.firstOrNull { it.name == "getUrl" && it.parameterCount == 0 }
                        ?.invoke(image) as? String
                }
                ?.firstOrNull { it.isNotBlank() }
        }.getOrNull()

    /**
     * O cache exige tamanho exato quando informado, então uma estimativa por bitrate
     * quebraria a checagem final. Lido por reflexão para não depender da assinatura
     * exata da versão do NewPipe; ausente, fica 0 (tamanho desconhecido).
     */
    private fun contentLengthOf(stream: VideoStream): Long =
        runCatching {
            VideoStream::class.java.methods
                .firstOrNull { it.name == "getContentLength" && it.parameterCount == 0 }
                ?.invoke(stream) as? Long
        }.getOrNull()?.takeIf { it > 0L } ?: 0L

    private companion object {
        const val SOURCE_ID = "youtube"
        const val WATCH_URL = "https://www.youtube.com/watch?v="
        val REQUEST_HEADERS = mapOf(
            "User-Agent" to "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip",
            "Accept" to "*/*",
            "Accept-Language" to "pt-BR,pt;q=0.9",
            "Referer" to "https://www.youtube.com/"
        )
    }
}
