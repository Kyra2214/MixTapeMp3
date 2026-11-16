package com.cdvirtual.app.media.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

object DirectDownloader {
    private const val MAX_ATTEMPTS = 4
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 45_000

    /**
     * Baixa para .part e tenta retomar com HTTP Range quando o servidor aceita.
     * O arquivo só é publicado depois que o tamanho real conhecido é validado.
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

                var conn: HttpURLConnection? = null
                try {
                    conn = Http.open(
                        URL(option.url),
                        "GET",
                        requestHeaders,
                        CONNECT_TIMEOUT_MS,
                        READ_TIMEOUT_MS
                    )

                    val code = conn.responseCode
                    if (code == 416) {
                        val exactSize = conn.getHeaderField("Content-Range")
                            ?.substringAfter("*/")
                            ?.toLongOrNull()
                        if (exactSize != null && offset == exactSize) {
                            if (!tmp.renameTo(target)) throw IOException("Não foi possível salvar o arquivo")
                            onProgress(exactSize, exactSize)
                            return@withContext
                        }
                        throw IOException("O servidor recusou a retomada do download (HTTP 416). Analise o vídeo novamente.")
                    }
                    if (code !in 200..299) throw HttpStatusException(code)

                    val partialResponse = code == HttpURLConnection.HTTP_PARTIAL
                    val contentRange = conn.getHeaderField("Content-Range")
                    val rangeStart = contentRange
                        ?.substringAfter("bytes ", "")
                        ?.substringBefore('-')
                        ?.toLongOrNull()

                    if (offset > 0L && partialResponse && rangeStart != offset) {
                        throw IOException("O servidor retornou um intervalo diferente do solicitado.")
                    }

                    // Um 200 significa que o servidor ignorou Range: reinicia sem concatenar bytes.
                    val append = offset > 0L && partialResponse && rangeStart == offset
                    if (offset > 0L && !append) tmp.delete()

                    val responseLength = conn.contentLengthLong.takeIf { it > 0L }
                    val rangeTotal = contentRange?.substringAfter('/')?.toLongOrNull()
                    val exactTotal = rangeTotal ?: if (!append) responseLength else null
                    // sizeBytes pode ser estimado no YouTube, portanto serve só para exibir progresso.
                    val progressTotal = exactTotal ?: option.sizeBytes.takeIf { it > 0L } ?: 0L
                    var done = if (append) offset else 0L
                    onProgress(done, progressTotal)

                    conn.inputStream.use { input ->
                        FileOutputStream(tmp, append).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                done += count
                                onProgress(done, progressTotal)
                            }
                        }
                    }

                    if (exactTotal != null && done != exactTotal) {
                        throw IOException("Download incompleto: $done de $exactTotal bytes. Tentativa $attempt/$MAX_ATTEMPTS.")
                    }

                    if (!tmp.renameTo(target)) throw IOException("Não foi possível salvar o arquivo")
                    onProgress(done, exactTotal ?: progressTotal)
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
}
