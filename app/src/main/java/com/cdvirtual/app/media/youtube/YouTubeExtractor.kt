package com.cdvirtual.app.media.youtube

import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization

/**
 * Ponto único de inicialização e transporte do NewPipe.
 * A área YouTube e o importador compartilham o extrator, mas não suas regras de negócio.
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
        val requestPath = java.net.URL(request.url()).let { it.host + it.path }
        val data = request.dataToSend()
        val builder = okhttp3.Request.Builder().url(request.url())

        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }
        if (request.headers().keys.none { it.equals("User-Agent", true) }) {
            builder.header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
            )
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
                            if (total > MAX_RESPONSE_BYTES) {
                                throw java.io.IOException("Resposta excedeu o limite de $MAX_RESPONSE_BYTES bytes")
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                    val responseBody = output.toString("UTF-8")
                    println("[NP-HTTP] CORPO RECEBIDO: $total bytes")
                    println("[NP-HTTP] CONCLUIDO EM ${System.currentTimeMillis() - startedAt} ms")
                    return Response(
                        status,
                        response.message,
                        responseHeaders,
                        responseBody,
                        response.request.url.toString()
                    )
                }?.let { return it }
            } catch (failure: java.io.IOException) {
                println("[NP-HTTP] FALHA ${failure.javaClass.simpleName}: ${failure.message}")
                if (attempt == MAX_ATTEMPTS) throw failure
                Thread.sleep(700L * attempt)
            }
        }
        throw java.io.IOException("Falha inesperada no transporte HTTP")
    }
}

object YouTubeExtractor {
    @Volatile private var initialized = false

    fun initialize() {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                NewPipe.init(NewPipeHttpDownloader, Localization("pt", "BR"))
                initialized = true
            }
        }
    }
}
