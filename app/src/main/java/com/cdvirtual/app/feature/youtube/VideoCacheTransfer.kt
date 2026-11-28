package com.cdvirtual.app.feature.youtube

import com.cdvirtual.app.core.storage.VideoCacheTransferFileStore
import com.cdvirtual.app.core.diagnostics.AppDiagnostics
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/**
 * Requisição já resolvida por um provedor que tenha autorização para download
 * e armazenamento offline. Este componente não extrai URLs de plataformas.
 */
data class VideoCacheAudioTrackRequest(
    val mediaUrl: String,
    val mimeType: String,
    val headers: Map<String, String> = emptyMap(),
    val expectedBytes: Long = 0L
) {
    init {
        require(mediaUrl.startsWith("https://", ignoreCase = true)) { "A faixa de áudio exige URL HTTPS." }
        require(mimeType.substringBefore(';').trim().lowercase(Locale.ROOT) == "audio/mp4") {
            "A junção MP4 exige áudio AAC em contêiner MP4."
        }
        require(expectedBytes >= 0L) { "Tamanho esperado da faixa de áudio inválido." }
    }
}

data class VideoCacheTransferRequest(
    val mediaUrl: String,
    val mimeType: String,
    val extension: String,
    val headers: Map<String, String> = emptyMap(),
    val expectedBytes: Long = 0L,
    val audioTrack: VideoCacheAudioTrackRequest? = null
) {
    init {
        require(mediaUrl.startsWith("https://", ignoreCase = true)) {
            "O cache aceita somente URLs HTTPS."
        }
        require(mimeType.substringBefore(';').trim().lowercase(Locale.ROOT).startsWith("video/")) {
            "A fonte não declarou um tipo de mídia de vídeo."
        }
        require(extension.lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS) {
            "Formato de vídeo não suportado. Use MP4 ou WebM."
        }
        val normalizedMime = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
        require(
            (extension.equals("mp4", true) && normalizedMime == "video/mp4") ||
                (extension.equals("webm", true) && normalizedMime in setOf("video/webm", "video/x-matroska"))
        ) { "A extensão e o tipo MIME do vídeo não correspondem." }
        require(expectedBytes >= 0L) { "Tamanho esperado inválido." }
        headers.forEach { (name, value) ->
            require(name.isNotBlank() && value.isNotBlank()) { "Cabeçalho HTTP inválido." }
            require(name.lowercase(Locale.ROOT) !in RESERVED_HEADERS) {
                "O provedor não pode sobrescrever cabeçalhos de controle do download."
            }
        }
    }

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("mp4", "webm")
        private val RESERVED_HEADERS = setOf(
            "range", "host", "content-length", "connection", "transfer-encoding"
        )
    }
}

data class VideoCacheTransferProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?
) {
    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0L }
            ?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
}


internal data class VideoCacheContentRange(
    val start: Long,
    val endInclusive: Long,
    val totalBytes: Long
)

/** Strict parser for RFC 7233 Content-Range values used by resumable transfers. */
internal fun parseVideoCacheContentRange(header: String?): VideoCacheContentRange? {
    val match = Regex("""^bytes\s+(\d+)-(\d+)/(\d+)$""", RegexOption.IGNORE_CASE)
        .matchEntire(header?.trim().orEmpty()) ?: return null
    val start = match.groupValues[1].toLongOrNull() ?: return null
    val end = match.groupValues[2].toLongOrNull() ?: return null
    val total = match.groupValues[3].toLongOrNull() ?: return null
    if (start < 0L || end < start || total <= end) return null
    return VideoCacheContentRange(start, end, total)
}

/**
 * Transporte exclusivo da Central de Vídeos.
 *
 * Preserva o .part em cancelamentos e falhas para permitir retomada quando o servidor
 * oferece HTTP Range. Não acessa tabelas, filas, arquivos ou serviços da Central de Áudio.
 */
class VideoCacheTransfer(
    private val fileStore: VideoCacheTransferFileStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()
) {
    fun requiredFreeBytes(cacheId: String, request: VideoCacheTransferRequest): Long {
        val videoPartial = fileStore.getOrCreatePartialFile(cacheId)
        val videoMissing = (request.expectedBytes - videoPartial.length()).coerceAtLeast(0L)
        val audioRequest = request.audioTrack ?: return videoMissing
        val audioPartial = fileStore.getOrCreateAuxiliaryPartialFile(cacheId, "audio")
        val audioMissing = (audioRequest.expectedBytes - audioPartial.length()).coerceAtLeast(0L)
        return videoMissing + audioMissing
    }

    suspend fun download(
        cacheId: String,
        request: VideoCacheTransferRequest,
        onProgress: (VideoCacheTransferProgress) -> Unit = {}
    ): File {
        val videoPartial = fileStore.getOrCreatePartialFile(cacheId)
        val audioRequest = request.audioTrack
            ?: return downloadSingle(
                cacheId, request.mediaUrl, request.mimeType, request.headers,
                request.expectedBytes, videoPartial, 0L, request.expectedBytes.takeIf { it > 0L }, onProgress
            )

        val audioPartial = fileStore.getOrCreateAuxiliaryPartialFile(cacheId, "audio")
        val combinedTotal = if (request.expectedBytes > 0L && audioRequest.expectedBytes > 0L) {
            request.expectedBytes + audioRequest.expectedBytes
        } else null
        downloadSingle(
            cacheId, request.mediaUrl, request.mimeType, request.headers,
            request.expectedBytes, videoPartial, 0L, combinedTotal, onProgress
        )
        downloadSingle(
            cacheId, audioRequest.mediaUrl, audioRequest.mimeType, audioRequest.headers,
            audioRequest.expectedBytes, audioPartial, videoPartial.length(), combinedTotal, onProgress
        )

        val muxed = fileStore.getOrCreateAuxiliaryPartialFile(cacheId, "mux")
        AppDiagnostics.record("VIDEO_MUX", "Junção de áudio e vídeo iniciada", "cacheId=$cacheId videoBytes=${videoPartial.length()} audioBytes=${audioPartial.length()}")
        withContext(Dispatchers.IO) { VideoCacheMuxer.mux(videoPartial, audioPartial, muxed) }
        AppDiagnostics.record("VIDEO_MUX", "Junção de áudio e vídeo concluída", "cacheId=$cacheId outputBytes=${muxed.length()}")
        return muxed
    }

    private suspend fun downloadSingle(
        cacheId: String,
        mediaUrl: String,
        mimeType: String,
        headers: Map<String, String>,
        expectedBytes: Long,
        partial: File,
        progressOffset: Long,
        combinedTotalBytes: Long?,
        onProgress: (VideoCacheTransferProgress) -> Unit
    ): File {
        if (expectedBytes > 0L && partial.length() == expectedBytes) return partial
        val requestedOffset = partial.length()
        AppDiagnostics.record("VIDEO_HTTP", "Requisição HTTP de vídeo", "cacheId=$cacheId resumeOffset=$requestedOffset expectedBytes=${expectedBytes} mime=${mimeType} track=${if (mimeType.startsWith("audio/")) "audio" else "video"}")
        val builder = Request.Builder().url(mediaUrl)
        headers.forEach { (name, value) -> builder.header(name, value) }
        if (requestedOffset > 0L) builder.header("Range", "bytes=$requestedOffset-")
        val call = client.newCall(builder.get().build())

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, failure: IOException) {
                    AppDiagnostics.record("ERROR", "Falha de conexão no download", "cacheId=$cacheId exception=${failure.javaClass.name} message=${failure.message ?: "sem mensagem"}")
                    if (continuation.isActive) continuation.resumeWithException(failure)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use { http ->
                            AppDiagnostics.record("VIDEO_HTTP", "Resposta HTTP recebida", "cacheId=$cacheId status=${http.code} contentType=${http.header("Content-Type") ?: "ausente"} contentLength=${http.body?.contentLength() ?: -1L} contentRange=${http.header("Content-Range") ?: "ausente"} resumed=${requestedOffset > 0L}")
                            if (!http.isSuccessful) {
                                throw IOException("A fonte recusou o download (HTTP ${http.code}).")
                            }

                            if (!http.request.url.isHttps) {
                                throw IOException("A fonte redirecionou o download para uma conexão insegura.")
                            }

                            val append = requestedOffset > 0L && http.code == 206
                            if (requestedOffset > 0L && http.code != 200 && http.code != 206) {
                                throw IOException("A fonte não aceitou a retomada do download.")
                            }
                            var responseRangeLength: Long? = null
                            if (http.code == 206) {
                                val parsedRange = parseVideoCacheContentRange(http.header("Content-Range"))
                                    ?: throw IOException("A fonte retornou HTTP 206 com Content-Range inválido ou ausente.")
                                val expectedStart = if (append) requestedOffset else 0L
                                if (parsedRange.start != expectedStart) {
                                    throw IOException("A fonte retornou um intervalo incompatível com o parcial.")
                                }
                                responseRangeLength = parsedRange.endInclusive - parsedRange.start + 1L
                                val declaredTotal = parsedRange.totalBytes
                                if (parsedRange.endInclusive != declaredTotal - 1L) {
                                    throw IOException("A fonte retornou somente parte do intervalo solicitado; o parcial foi preservado para retomada.")
                                }
                                if (expectedBytes > 0L && declaredTotal != expectedBytes) {
                                    throw IOException("O tamanho total informado pela fonte não corresponde ao esperado.")
                                }
                            }

                            val responseMime = http.header("Content-Type")
                                ?.substringBefore(';')
                                ?.trim()
                                ?.lowercase(Locale.ROOT)
                            val requestedMime = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
                            val mimeMatches = responseMime == null ||
                                responseMime == "application/octet-stream" ||
                                responseMime == requestedMime ||
                                (requestedMime == "video/webm" && responseMime == "video/x-matroska")
                            if (!mimeMatches) {
                                throw IOException("O tipo MIME recebido não corresponde ao formato selecionado.")
                            }

                            val body = http.body ?: throw IOException("A fonte retornou um corpo vazio.")
                            val totalFromRange = http.header("Content-Range")
                                ?.substringAfterLast('/')
                                ?.toLongOrNull()
                            val responseLength = body.contentLength().takeIf { it >= 0L }
                            if (responseRangeLength != null && responseLength != null &&
                                responseLength != responseRangeLength
                            ) {
                                throw IOException("O tamanho do corpo não corresponde ao intervalo HTTP informado.")
                            }
                            val effectiveOffset = if (append) requestedOffset else 0L
                            val totalBytes = totalFromRange
                                ?: expectedBytes.takeIf { it > 0L }
                                ?: if (http.code == 206) null else responseLength

                            if (expectedBytes > 0L && totalBytes != null &&
                                totalBytes != expectedBytes
                            ) {
                                throw IOException("O tamanho informado pela fonte não corresponde ao esperado.")
                            }

                            FileOutputStream(partial, append).use { output ->
                                val input = body.byteStream()
                                var nextLogAt = if (totalBytes != null && totalBytes > 0L) (totalBytes / 10L).coerceAtLeast(1L) else 5L * 1024L * 1024L
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var downloaded = effectiveOffset
                                var receivedInResponse = 0L
                                while (true) {
                                    if (!continuation.isActive) throw IOException("Download cancelado.")
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    downloaded += count
                                    receivedInResponse += count
                                    onProgress(VideoCacheTransferProgress(progressOffset + downloaded, combinedTotalBytes ?: totalBytes?.let { progressOffset + it }))
                                    if (downloaded >= nextLogAt) {
                                        val percent = (combinedTotalBytes ?: totalBytes?.let { progressOffset + it })?.takeIf { it > 0L }?.let { (progressOffset + downloaded) * 100L / it }
                                        AppDiagnostics.record("VIDEO_PROGRESS", "Progresso do download", "cacheId=$cacheId downloadedBytes=${progressOffset + downloaded} totalBytes=${combinedTotalBytes ?: totalBytes?.let { progressOffset + it } ?: -1L} percent=${percent ?: -1L}")
                                        nextLogAt = if (totalBytes != null && totalBytes > 0L) nextLogAt + (totalBytes / 10L).coerceAtLeast(1L) else nextLogAt + 5L * 1024L * 1024L
                                    }
                                }
                                if (responseRangeLength != null && receivedInResponse != responseRangeLength) {
                                    throw IOException("A transferência terminou antes de completar o intervalo HTTP informado.")
                                }
                                output.flush()
                                output.fd.sync()
                            }

                            if (partial.length() <= 0L) throw IOException("O arquivo recebido está vazio.")
                            if (expectedBytes > 0L && partial.length() != expectedBytes) {
                                throw IOException("O download terminou antes de receber todos os bytes.")
                            }
                            AppDiagnostics.record("VIDEO_HTTP", "Corpo HTTP recebido e validado", "cacheId=$cacheId bytes=${partial.length()} expectedBytes=${expectedBytes} httpStatus=${http.code}")
                            if (continuation.isActive) {
                                continuation.resumeWith(Result.success(partial))
                            }
                        }
                    } catch (failure: Throwable) {
                        AppDiagnostics.record("ERROR", "Resposta HTTP de vídeo rejeitada", "cacheId=$cacheId exception=${failure.javaClass.name} message=${failure.message ?: "sem mensagem"}\n${failure.stackTraceToString()}")
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                }
            })
        }
    }
}
