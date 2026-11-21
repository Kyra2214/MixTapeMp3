package com.cdvirtual.app.media.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Transporte HTTP utilizado exclusivamente pelo NewPipe Extractor.
 */
private object NewPipeHttpDownloader : Downloader() {
    private const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024
    private const val MAX_ATTEMPTS = 3

    private val client = okhttp3.OkHttpClient.Builder()
        .proxy(java.net.Proxy.NO_PROXY)
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(false)
        .build()

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val requestUrl = java.net.URL(request.url())
        val requestPath = requestUrl.host + requestUrl.path
        val data = request.dataToSend()
        val builder = okhttp3.Request.Builder().url(request.url())

        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }
        if (request.headers().keys.none { it.equals("User-Agent", true) }) {
            builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
        }
        val body = when {
            data != null -> data.toRequestBody(null)
            method in setOf("POST", "PUT", "PATCH") -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        builder.header("Accept-Encoding", "identity")
        val httpRequest = builder.method(method, body).build()

        for (attempt in 1..MAX_ATTEMPTS) {
            val startedAt = System.currentTimeMillis()
            println("[NP-HTTP] INICIO $method $requestPath tentativa $attempt/$MAX_ATTEMPTS")
            try {
                client.newCall(httpRequest).execute().use { response ->
                    val status = response.code
                    println("[NP-HTTP] HTTP $status em ${System.currentTimeMillis() - startedAt} ms")
                    if (status in setOf(408, 429, 500, 502, 503, 504) && attempt < MAX_ATTEMPTS) {
                        Thread.sleep(700L * attempt)
                        return@use null
                    }
                    val responseHeaders = response.headers.toMultimap()
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    response.body?.byteStream()?.use { input ->
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            total += count
                            if (total > MAX_RESPONSE_BYTES) throw java.io.IOException("Resposta excedeu o limite de $MAX_RESPONSE_BYTES bytes")
                            output.write(buffer, 0, count)
                        }
                    }
                    val responseBody = output.toString("UTF-8")
                    println("[NP-HTTP] CORPO RECEBIDO: $total bytes")
                    println("[NP-HTTP] CONCLUIDO EM ${System.currentTimeMillis() - startedAt} ms")
                    return Response(status, response.message, responseHeaders, responseBody, response.request.url.toString())
                }?.let { return it }
            } catch (e: java.io.IOException) {
                println("[NP-HTTP] FALHA ${e.javaClass.simpleName}: ${e.message}")
                if (attempt == MAX_ATTEMPTS) throw e
                Thread.sleep(700L * attempt)
            }
        }
        throw java.io.IOException("Falha inesperada no transporte HTTP")
    }
}

data class YouTubePlaybackInfo(
    val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val streamUrl: String,
    val mimeType: String?
)

object YouTubeApi {
    private const val BASE_URL = "https://www.youtube.com/watch?v="
    @Volatile private var initialized = false

    fun initialize() = ensureInitialized()

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(YouTubeApi) {
            if (!initialized) {
                NewPipe.init(NewPipeHttpDownloader, Localization("pt", "BR"))
                initialized = true
            }
        }
    }

    suspend fun resolvePlayback(videoId: String): YouTubePlaybackInfo = withContext(Dispatchers.IO) {
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

        // Streams progressivos incluem áudio e vídeo. DASH separado será tratado em outra etapa.
        val progressive = info.videoStreams
            .filter { !it.isVideoOnly && it.isUrl }
            .maxByOrNull { it.height }
        val streamUrl = progressive?.content
            ?: info.dashMpdUrl.takeIf { it.isNotBlank() }
            ?: throw YouTubeUnavailableException(YouTubeFailure.PlayerChanged)
        val mimeType = if (progressive == null) "application/dash+xml" else progressive.format?.mimeType

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