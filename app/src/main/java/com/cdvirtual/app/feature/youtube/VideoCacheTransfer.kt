package com.cdvirtual.app.feature.youtube

import com.cdvirtual.app.core.storage.VideoCacheFileStore
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
data class VideoCacheTransferRequest(
    val mediaUrl: String,
    val mimeType: String,
    val extension: String,
    val headers: Map<String, String> = emptyMap(),
    val expectedBytes: Long = 0L
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
    private val fileStore: VideoCacheFileStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()
) {
    fun requiredFreeBytes(cacheId: String, expectedBytes: Long): Long {
        if (expectedBytes <= 0L) return 0L
        val partial = fileStore.getOrCreatePartialFile(cacheId)
        return (expectedBytes - partial.length()).coerceAtLeast(0L)
    }

    suspend fun download(
        cacheId: String,
        request: VideoCacheTransferRequest,
        onProgress: (VideoCacheTransferProgress) -> Unit = {}
    ): File {
        val partial = fileStore.getOrCreatePartialFile(cacheId)
        val requestedOffset = partial.length()
        val builder = Request.Builder().url(request.mediaUrl)
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        if (requestedOffset > 0L) builder.header("Range", "bytes=$requestedOffset-")
        val call = client.newCall(builder.get().build())

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, failure: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(failure)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use { http ->
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
                                if (request.expectedBytes > 0L && declaredTotal != request.expectedBytes) {
                                    throw IOException("O tamanho total informado pela fonte não corresponde ao esperado.")
                                }
                            }

                            val responseMime = http.header("Content-Type")
                                ?.substringBefore(';')
                                ?.trim()
                                ?.lowercase(Locale.ROOT)
                            val requestedMime = request.mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
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
                                ?: request.expectedBytes.takeIf { it > 0L }
                                ?: if (http.code == 206) null else responseLength

                            if (request.expectedBytes > 0L && totalBytes != null &&
                                totalBytes != request.expectedBytes
                            ) {
                                throw IOException("O tamanho informado pela fonte não corresponde ao esperado.")
                            }

                            FileOutputStream(partial, append).use { output ->
                                val input = body.byteStream()
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
                                    onProgress(VideoCacheTransferProgress(downloaded, totalBytes))
                                }
                                if (responseRangeLength != null && receivedInResponse != responseRangeLength) {
                                    throw IOException("A transferência terminou antes de completar o intervalo HTTP informado.")
                                }
                                output.flush()
                                output.fd.sync()
                            }

                            if (partial.length() <= 0L) throw IOException("O arquivo recebido está vazio.")
                            if (request.expectedBytes > 0L && partial.length() != request.expectedBytes) {
                                throw IOException("O download terminou antes de receber todos os bytes.")
                            }
                            if (continuation.isActive) {
                                continuation.resumeWith(Result.success(partial))
                            }
                        }
                    } catch (failure: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                }
            })
        }
    }
}
