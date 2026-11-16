package com.cdvirtual.app.media.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URL

object DirectDownloader {
    /** Baixa para um arquivo .part e só renomeia ao concluir; cancelável (ensureActive a cada bloco). */
    suspend fun download(
        option: MediaOption,
        target: File,
        onProgress: (done: Long, total: Long) -> Unit,
        headers: Map<String, String> = emptyMap()
    ) = withContext(Dispatchers.IO) {
            val tmp = File(target.parentFile, target.name + ".part")
            val conn = Http.open(URL(option.url), "GET", headers)
            try {
                if (conn.responseCode !in 200..299) throw HttpStatusException(conn.responseCode)
                val declared = conn.contentLengthLong.takeIf { it > 0 }
                val total = declared ?: option.sizeBytes
                var done = 0L
                conn.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                }
                // 10.5: conexão encerrada antes do fim não pode virar uma música cortada na biblioteca.
                if (declared != null && done != declared) throw IOException("Download incompleto: $done de $declared bytes. A conexão foi interrompida.")
                if (!tmp.renameTo(target)) throw IOException("Não foi possível salvar o arquivo")
            } catch (t: Throwable) {
                tmp.delete()
                throw t
            } finally {
                conn.disconnect()
            }
        }
}
