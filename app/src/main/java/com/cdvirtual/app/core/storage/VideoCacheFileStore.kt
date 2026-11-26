package com.cdvirtual.app.core.storage

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Acesso seguro aos arquivos do cache de vídeo.
 *
 * Este componente não acessa a rede nem extrai streams. Ele só organiza arquivos
 * recebidos de uma fonte que autorize armazenamento offline.
 */
class VideoCacheFileStore(context: Context) {
    private val directory = MediaStorageLayout.videoCacheDir(context).canonicalFile

    fun cacheDirectory(): File = directory

    fun createPartialFile(cacheId: String): File {
        requireSafeId(cacheId)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Não foi possível criar o diretório do cache de vídeos.")
        }
        val partial = File(directory, "$cacheId.part").canonicalFile
        requireManaged(partial)
        if (partial.exists()) throw IOException("Já existe um arquivo parcial para este vídeo.")
        return partial
    }

    /**
     * Publica um arquivo .part somente após confirmar tamanho não vazio e tamanho esperado,
     * usando rename no mesmo diretório para evitar expor arquivos incompletos.
     */
    fun promotePartialFile(partialFile: File, extension: String, expectedBytes: Long = 0L): File {
        val partial = partialFile.canonicalFile
        requireManaged(partial)
        require(partial.name.endsWith(".part")) { "Somente arquivos .part podem ser publicados." }
        require(extension.lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS) {
            "Formato de vídeo não suportado pelo cache."
        }
        if (!partial.isFile || partial.length() <= 0L) {
            throw IOException("O arquivo temporário está vazio ou não existe.")
        }
        if (expectedBytes > 0L && partial.length() != expectedBytes) {
            throw IOException("O arquivo temporário está incompleto.")
        }

        val baseName = partial.name.removeSuffix(".part")
        val destination = File(directory, "$baseName.${extension.lowercase(Locale.ROOT)}").canonicalFile
        requireManaged(destination)
        if (destination.exists()) throw IOException("Já existe um arquivo publicado para este vídeo.")
        if (!partial.renameTo(destination)) throw IOException("Não foi possível publicar o arquivo do cache.")
        return destination
    }

    /** Retorna somente arquivos completos e contidos no diretório exclusivo de vídeo. */
    fun resolveValidFile(path: String?, expectedBytes: Long = 0L): File? {
        if (path.isNullOrBlank()) return null
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        if (!isManaged(file) || !file.isFile || file.length() <= 0L) return null
        if (expectedBytes > 0L && file.length() != expectedBytes) return null
        return file
    }

    /** Remove somente um arquivo cujo caminho canônico pertença ao diretório de vídeo. */
    fun deleteManagedFile(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        if (!isManaged(file)) return false
        return !file.exists() || file.delete()
    }

    /** Lista parciais para recuperação posterior; não os apaga automaticamente. */
    fun listPartialFiles(): List<File> =
        directory.listFiles { file -> file.isFile && file.name.endsWith(".part") }?.toList().orEmpty()

    private fun requireSafeId(cacheId: String) {
        require(cacheId.matches(Regex("^[A-Za-z0-9_-]{1,100}$"))) {
            "Identificador de cache inválido."
        }
    }

    private fun requireManaged(file: File) {
        require(isManaged(file)) { "O arquivo está fora do diretório exclusivo do cache de vídeos." }
    }

    private fun isManaged(file: File): Boolean =
        file.canonicalFile.parentFile == directory

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("mp4", "webm")
    }
}
