package com.cdvirtual.app.media.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URL
import kotlin.coroutines.coroutineContext

object DirectDownloader {
    private const val MAX_ATTEMPTS = 4
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 45_000

    /**
     * Baixa para .part. Se a conexão cair, tenta continuar com HTTP Range
     * quando o servidor/CDN aceitar. Só publica o arquivo depois de validar
     * o tamanho total conhecido.
     */
    suspend fun download(
        option: MediaOption,
        target: File,
        onProgress: (done: Long, total: Long) -> Unit,
        headers: Map<String, String> = emptyMap()
    ) = withContext(Dispatchers.IO) {
        val tmp = File(target.parentFile, target.name + ".part")
        var lastError: IOException? = null

        try {
            for (attempt in 1..MAX_ATTEMPTS) {
                coroutineContext.ensureActive()
                val offset = if (tmp.exists()) tmp.length() else 0L
                val requestHeaders = headers.toMutableMap()
                if (offset > 0L) requestHeaders["Range"] = "bytes=$offset-"

                var conn: java.net.HttpURLConnection? = null
                try {
                    conn = Http.open(
                        URL(option.url),
                        "GET",
                        requestHeaders,
                        CONNECT_TIMEOUT_MS,
                        READ_TIMEOUT_MS
                    )

                    val code = conn.responseCode
                    if (code !in 200..299 && code != 416) throw HttpStatusException(code)

                    if (code == 416) {
                        val expected = option.sizeBytes
                        if (expected > 0 && offset == expected) {
                            if (!tmp.renameTo(target)) throw IOException("Não foi possível salvar o arquivo")
                            onProgress(expected, expected)
                            return@withContext
                        }
                        throw IOException("O servidor recusou a retomada do download (HTTP 416). Analise o vídeo novamente.")
                    }

                    val partialResponse = code == 206
                    val rangeStart = conn.getHeaderField("Content-Range")
                        ?.substringAfter("bytes ", "")
                        ?.substringBefore('-')
                        ?.toLongOrNull()

                    // Se o servidor ignorar Range e devolver 200, reinicia do zero
                    // para não concatenar duas cópias do arquivo.
                    val append = offset > 0L && partialResponse && rangeStart == offset
                    if (offset > 0L && !append) {
                        tmp.delete()
                    }

                    val responseLength = conn.contentLengthLong.takeIf { it > 0L }
                    val rangeTotal = conn.getHeaderField("Content-Range")
                        ?.substringAfter('/')
                        ?.toLongOrNull()
                    val total = rangeTotal
                        ?: option.sizeBytes.takeIf { it > 0L }
                        ?: if (append && responseLength != null) offset + responseLength else responseLength ?: 0L

                    var done = if (append) offset else 0L
                    onProgress(done, total)

                    conn.inputStream.use { input ->
                        tmp.outputStream().let { raw ->
                            (if (append) raw.apply { /* append handled below */ } else raw).use { out ->
                                // File.outputStream() truncates; append mode is required only for valid 206.
                                val stream = if (append) {
                                    tmp.appendOutputStream()
                                } else out
                                if (append) out.close()
                                stream.use { destination ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        coroutineContext.ensureActive()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        destination.write(buffer, 0, count)
                                        done += count
                                        onProgress(done, total)
                                    }
                                }
                            }
                        }
                    }

                    val finalExpected = rangeTotal ?: option.sizeBytes.takeIf { it > 0L }
                    if (finalExpected != null && done != finalExpected) {
                        throw IOException("Download incompleto: $done de $finalExpected bytes. Tentativa $attempt/$MAX_ATTEMPTS.")
                    }

                    if (!tmp.renameTo(target)) throw IOException("Não foi possível salvar o arquivo")
                    onProgress(done, if (finalExpected != null) finalExpected else total)
                    return@withContext
                } catch (e: IOException) {
                    lastError = e
                    val retryable = e !is HttpStatusException ||
                        e.code == 408 || e.code == 429 || e.code in 500..599
                    if (!retryable || attempt == MAX_ATTEMPTS) throw e
                    Thread.sleep(800L * attempt)
                } finally {
                    conn?.disconnect()
                }
            }
            throw lastError ?: IOException("Não foi possível concluir o download")
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
    }

    private fun File.appendOutputStream() = java.io.FileOutputStream(this, true)
}
