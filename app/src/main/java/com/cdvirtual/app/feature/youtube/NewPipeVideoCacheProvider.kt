package com.cdvirtual.app.feature.youtube

import com.cdvirtual.app.core.database.VideoCacheQuality
import com.cdvirtual.app.core.diagnostics.AppDiagnostics
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
            val startedAt = System.currentTimeMillis()
            AppDiagnostics.record("VIDEO_EXTRACT", "Consulta de qualidades iniciada", "videoId=$sourceVideoId provider=$SOURCE_ID")
            val info = infoOf(sourceVideoId) ?: run {
                AppDiagnostics.record("WARN", "Consulta de qualidades sem resultado", "videoId=$sourceVideoId durationMs=${System.currentTimeMillis() - startedAt}")
                return@withContext emptySet()
            }
            val groups = streamDiagnostics(info)
            val qualities = candidates(info).map { it.first }.toSet()
            AppDiagnostics.record(
                "VIDEO_EXTRACT",
                "Inventário de streams retornado pelo NewPipe",
                "videoId=$sourceVideoId durationMs=${System.currentTimeMillis() - startedAt} " +
                    "streamType=${readProperty(info, "getStreamType") ?: "unknown"} " +
                    "durationSeconds=${info.duration} shortForm=${readProperty(info, "isShortFormContent") ?: readProperty(info, "getIsShortFormContent") ?: "unknown"} " +
                    groups
            )
            AppDiagnostics.record(
                "VIDEO_EXTRACT",
                "Qualidades identificadas",
                "videoId=$sourceVideoId qualities=${qualities.sorted()} progressiveCandidates=${candidates(info).size} " +
                    "videoStreams=${info.videoStreams.size} videoOnlyStreams=${streamCollection(info, "getVideoOnlyStreams").size} " +
                    "progressiveInVideoList=${info.videoStreams.count { !it.isVideoOnly }} " +
                    "videoOnlyInVideoList=${info.videoStreams.count { it.isVideoOnly }} " +
                    "audioStreams=${streamCollection(info, "getAudioStreams").size} elapsedMs=${System.currentTimeMillis() - startedAt}"
            )
            qualities
        }

    override suspend fun resolve(
        sourceVideoId: String,
        qualityHeight: Int
    ): VideoCacheResolvedSource? = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        AppDiagnostics.record("VIDEO_EXTRACT", "Resolução de stream iniciada", "videoId=$sourceVideoId quality=${qualityHeight}p")
        val info = infoOf(sourceVideoId) ?: run {
            AppDiagnostics.record("WARN", "Resolução de stream sem informações", "videoId=$sourceVideoId quality=${qualityHeight}p")
            return@withContext null
        }
        val availableCandidates = candidates(info)
        val resolvedHeight = VideoCacheQuality.selectFallback(qualityHeight, availableCandidates.map { it.first }.toSet())
            ?: run {
                AppDiagnostics.record("WARN", "Nenhuma qualidade compatível disponível", "videoId=$sourceVideoId requested=${qualityHeight}p")
                return@withContext null
            }
        val stream = availableCandidates.firstOrNull { it.first == resolvedHeight }?.second
            ?: return@withContext null
        if (resolvedHeight != qualityHeight) {
            AppDiagnostics.record("VIDEO_EXTRACT", "Fallback de qualidade aplicado", "videoId=$sourceVideoId requested=${qualityHeight}p selected=${resolvedHeight}p available=${availableCandidates.map { it.first }}")
        }

        val url = stream.url?.takeIf { it.isNotBlank() } ?: run {
            AppDiagnostics.record("ERROR", "Stream sem URL utilizável", "videoId=$sourceVideoId quality=${qualityHeight}p")
            return@withContext null
        }
        val mime = stream.format?.mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: run {
                AppDiagnostics.record("ERROR", "Stream sem MIME reconhecido", "videoId=$sourceVideoId quality=${qualityHeight}p")
                return@withContext null
            }
        val extension = when (mime) {
            "video/mp4" -> "mp4"
            "video/webm", "video/x-matroska" -> "webm"
            else -> {
                AppDiagnostics.record("ERROR", "Formato de stream não suportado", "videoId=$sourceVideoId mime=$mime")
                return@withContext null
            }
        }
        AppDiagnostics.record("VIDEO_EXTRACT", "Stream resolvido", "videoId=$sourceVideoId quality=${qualityHeight}p mime=$mime expectedBytes=${contentLengthOf(stream)} durationMs=${System.currentTimeMillis() - startedAt}")

        VideoCacheResolvedSource(
            sourceId = SOURCE_ID,
            sourceVideoId = sourceVideoId,
            title = info.name?.takeIf { it.isNotBlank() } ?: "Vídeo do YouTube",
            uploader = info.uploaderName,
            thumbnailUrl = thumbnailOf(info),
            durationMs = info.duration.coerceAtLeast(0L) * 1000L,
            qualityHeight = resolvedHeight,
            transferRequest = VideoCacheTransferRequest(
                mediaUrl = url,
                mimeType = mime,
                extension = extension,
                headers = REQUEST_HEADERS,
                expectedBytes = contentLengthOf(stream)
            )
        )
    }

    private fun infoOf(videoId: String): StreamInfo? {
        val startedAt = System.currentTimeMillis()
        AppDiagnostics.record("VIDEO_EXTRACT", "Requisição ao extrator iniciada", "videoId=$videoId extractor=NewPipeExtractor")
        return try {
            YouTubeExtractor.initialize()
            val info = StreamInfo.getInfo(WATCH_URL + videoId)
            AppDiagnostics.record(
                "VIDEO_EXTRACT",
                "Metadados básicos recebidos do extrator",
                "videoId=$videoId elapsedMs=${System.currentTimeMillis() - startedAt} " +
                    "titlePresent=${!info.name.isNullOrBlank()} uploaderPresent=${!info.uploaderName.isNullOrBlank()} " +
                    "durationSeconds=${info.duration} videoStreams=${info.videoStreams.size} " +
                    "videoOnlyStreamsGetter=${streamCollection(info, "getVideoOnlyStreams").size} " +
                    "progressiveInVideoList=${info.videoStreams.count { !it.isVideoOnly }} " +
                    "videoOnlyInVideoList=${info.videoStreams.count { it.isVideoOnly }} " +
                    "audioStreams=${streamCollection(info, "getAudioStreams").size}"
            )
            info
        } catch (failure: Exception) {
            AppDiagnostics.record(
                "ERROR",
                "Falha na extração NewPipe",
                "videoId=$videoId elapsedMs=${System.currentTimeMillis() - startedAt} " +
                    "exception=${failure.javaClass.name} message=${failure.message ?: "sem mensagem"}\n${failure.stackTraceToString()}"
            )
            null
        }
    }

    /**
     * NewPipe muda a exposição de algumas coleções entre versões. A reflexão aqui é
     * apenas diagnóstica: não altera seleção de streams nem exige uma API opcional.
     */
    private fun streamDiagnostics(info: StreamInfo): String = listOf(
        "videoStreams" to streamCollection(info, "getVideoStreams"),
        "videoOnlyStreams" to streamCollection(info, "getVideoOnlyStreams"),
        "audioStreams" to streamCollection(info, "getAudioStreams"),
        "subtitles" to streamCollection(info, "getSubtitles")
    ).joinToString(" ") { (name, streams) ->
        "$name=${streams.size}[${streams.take(8).joinToString(";") { describeStream(it) }}]"
    }

    private fun streamCollection(target: Any, getter: String): List<Any> =
        runCatching {
            val value = target.javaClass.methods
                .firstOrNull { it.name == getter && it.parameterCount == 0 }
                ?.invoke(target)
            when (value) {
                is Iterable<*> -> value.filterNotNull()
                is Array<*> -> value.filterNotNull()
                else -> emptyList()
            }
        }.getOrDefault(emptyList())

    private fun describeStream(stream: Any): String {
        val format = invokeProperty(stream, "getFormat")
        val mime = format?.let { readProperty(it, "getMimeType") } ?: "?"
        val resolution = readProperty(stream, "getResolution") ?: "?"
        val videoOnly = readProperty(stream, "isVideoOnly")
            ?: readProperty(stream, "getIsVideoOnly")
            ?: "?"
        val delivery = readProperty(stream, "getDeliveryMethod") ?: "?"
        val size = readProperty(stream, "getContentLength") ?: "?"
        val bitrate = readProperty(stream, "getAverageBitrate") ?: "?"
        val hasUrl = readProperty(stream, "getUrl")?.isNotBlank() == true
        return "type=${stream.javaClass.simpleName},res=$resolution,mime=$mime,videoOnly=$videoOnly,delivery=$delivery,bytes=$size,bitrate=$bitrate,url=$hasUrl"
    }

    private fun invokeProperty(target: Any, getter: String): Any? =
        runCatching {
            target.javaClass.methods
                .firstOrNull { it.name == getter && it.parameterCount == 0 }
                ?.invoke(target)
        }.getOrNull()

    private fun readProperty(target: Any, getter: String): String? =
        invokeProperty(target, getter)?.toString()?.take(120)

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
