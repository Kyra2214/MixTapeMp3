package com.cdvirtual.app.core.storage

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Acesso seguro aos arquivos do cache de vídeo.
 *
 * Este componente não acessa a rede nem extrai streams. Ele organiza os parciais
 * de vídeo/áudio e os arquivos MP4 combinados da Central de Vídeos.
 */
/** File operations consumed by startup recovery and implemented only for the video cache. */
interface VideoCacheRecoveryFileStore {
    fun resolveValidFile(path: String?, expectedBytes: Long = 0L): File?
    fun deleteManagedFile(path: String?): Boolean
    fun deletePartialFile(cacheId: String): Boolean
    fun listPublishedFiles(): List<File>
    fun listPartialFiles(): List<File>
}

interface VideoCacheTransferFileStore {
    fun getOrCreatePartialFile(cacheId: String): File
    fun getOrCreateAuxiliaryPartialFile(cacheId: String, role: String): File =
        throw UnsupportedOperationException("Arquivos auxiliares não são suportados por este armazenamento.")
}

class VideoCacheFileStore(context: Context) : VideoCacheRecoveryFileStore, VideoCacheTransferFileStore {
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
     * Retorna o parcial existente para retomada ou cria um novo arquivo.
     * O arquivo permanece restrito ao diretório exclusivo de vídeos.
     */
    override fun getOrCreatePartialFile(cacheId: String): File {
        requireSafeId(cacheId)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Não foi possível criar o diretório do cache de vídeos.")
        }
        val partial = File(directory, "$cacheId.part").canonicalFile
        requireManaged(partial)
        if (partial.exists() && !partial.isFile) {
            throw IOException("O caminho parcial já existe e não é um arquivo regular.")
        }
        return partial
    }

    /**
     * Publica um arquivo .part somente após confirmar tamanho não vazio e tamanho esperado,
     * usando rename no mesmo diretório para evitar expor arquivos incompletos.
     */
    override fun getOrCreateAuxiliaryPartialFile(cacheId: String, role: String): File {
        requireSafeId(cacheId)
        require(role in setOf("audio", "mux")) { "Tipo de arquivo auxiliar inválido." }
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Não foi possível criar o diretório do cache de vídeos.")
        }
        val partial = File(directory, "$cacheId.$role.part").canonicalFile
        requireManaged(partial)
        if (partial.exists() && !partial.isFile) {
            throw IOException("O caminho auxiliar já existe e não é um arquivo regular.")
        }
        return partial
    }

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
        if (!containsVideoTrack(partial)) {
            throw IOException("O arquivo recebido não contém uma faixa de vídeo reconhecível.")
        }

        val baseName = partial.name.removeSuffix(".part")
        val destination = File(directory, "$baseName.${extension.lowercase(Locale.ROOT)}").canonicalFile
        requireManaged(destination)
        if (destination.exists()) throw IOException("Já existe um arquivo publicado para este vídeo.")
        if (!partial.renameTo(destination)) throw IOException("Não foi possível publicar o arquivo do cache.")
        return destination
    }

    /**
     * Reverte a promoção se a persistência Room falhar, mantendo os bytes para nova tentativa.
     * Se o parcial já existir, preserva ambos os arquivos para reconciliação posterior.
     */
    fun rollbackPromotion(publishedFile: File, cacheId: String): Boolean =
        rollbackVideoCachePromotion(publishedFile, directory, cacheId)

    /** Retorna somente arquivos completos e contidos no diretório exclusivo de vídeo. */
    override fun resolveValidFile(path: String?, expectedBytes: Long): File? {
        if (path.isNullOrBlank()) return null
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        if (!isManaged(file) || !file.isFile || file.length() <= 0L) return null
        if (expectedBytes > 0L && file.length() != expectedBytes) return null
        if (!containsVideoTrack(file)) return null
        return file
    }

    /** Remove somente um arquivo cujo caminho canônico pertença ao diretório de vídeo. */
    override fun deleteManagedFile(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        if (!isManaged(file)) return false
        return !file.exists() || file.delete()
    }

    /** Remove um parcial identificado por cacheId, sem tocar em arquivos fora do diretório de vídeo. */
    override fun deletePartialFile(cacheId: String): Boolean {
        requireSafeId(cacheId)
        val partials = listOf(
            File(directory, "$cacheId.part"),
            File(directory, "$cacheId.audio.part"),
            File(directory, "$cacheId.mux.part")
        ).map { it.canonicalFile }
        if (partials.any { !isManaged(it) }) return false
        return partials.all { !it.exists() || it.delete() }
    }

    /** Lista arquivos publicados; a recuperação usa a lista para identificar órfãos sem apagá-los. */
    override fun listPublishedFiles(): List<File> =
        directory.listFiles { file -> file.isFile && file.extension.lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS }
            ?.toList().orEmpty()

    /** Lista parciais para recuperação posterior; não os apaga automaticamente. */
    override fun listPartialFiles(): List<File> =
        directory.listFiles { file -> file.isFile && file.name.endsWith(".part") }?.toList().orEmpty()

    private fun containsVideoTrack(file: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).any { index ->
                runCatching {
                    extractor.getTrackFormat(index)
                        .getString(MediaFormat.KEY_MIME)
                        ?.startsWith("video/") == true
                }.getOrDefault(false)
            }
        } catch (_: Exception) {
            false
        } finally {
            runCatching { extractor.release() }
        }
    }

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


/**
 * Pure filesystem rollback primitive. Kept outside the Android-backed store so its
 * safety properties can be exercised by local JVM tests using temporary directories.
 */
internal fun rollbackVideoCachePromotion(
    publishedFile: File,
    cacheDirectory: File,
    cacheId: String
): Boolean {
    require(cacheId.matches(Regex("^[A-Za-z0-9_-]{1,100}$"))) {
        "Identificador de cache inválido."
    }
    val directory = cacheDirectory.canonicalFile
    val published = publishedFile.canonicalFile
    require(published.parentFile == directory) {
        "O arquivo está fora do diretório exclusivo do cache de vídeos."
    }
    require(published.name.startsWith("$cacheId.") &&
        published.extension.lowercase(Locale.ROOT) in setOf("mp4", "webm")
    ) { "O arquivo publicado não pertence ao item de cache informado." }
    val partialName = when (published.name) {
        "$cacheId.mp4", "$cacheId.webm" -> "$cacheId.part"
        "$cacheId.mux.mp4" -> "$cacheId.mux.part"
        else -> throw IllegalArgumentException("O arquivo publicado não corresponde a um formato de cache conhecido.")
    }
    val partial = File(directory, partialName).canonicalFile
    require(partial.parentFile == directory) {
        "O arquivo parcial está fora do diretório exclusivo do cache de vídeos."
    }
    if (!published.isFile || partial.exists()) return false
    return published.renameTo(partial)
}
