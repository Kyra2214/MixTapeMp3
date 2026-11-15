package com.cdvirtual.app.feature.export

import android.content.Context
import com.cdvirtual.app.media.metadata.Id3Reader
import com.cdvirtual.app.core.storage.ExportCopies
import com.cdvirtual.app.media.service.ExportNaming
import com.cdvirtual.app.media.service.ExportRecovery
import com.cdvirtual.app.media.service.ExportService
import java.io.File

/** Um CD gerado (MP3 único em filesDir/exports) com os dados mostrados no gerenciador (9.5). */
data class GeneratedCd(
    val file: File,
    val title: String,
    val artist: String?,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val durationMs: Long?,
    val trackCount: Int,
    val hasCue: Boolean
) {
    val cueFile: File get() = File(file.parentFile, file.nameWithoutExtension + ".cue")
}

/** Listagem e renomeação dos CDs gerados. Tudo local/offline; nunca toca nos arquivos originais de música. */
object GeneratedCds {
    fun dir(context: Context) = ExportRecovery.exportsDir(context)

    /** CDs gerados, do mais recente ao mais antigo. Arquivos ".part"/".tagged" (parciais) são ignorados. */
    fun list(context: Context): List<GeneratedCd> =
        dir(context).listFiles { f -> f.isFile && f.extension.equals("mp3", true) }
            ?.map { describe(it) }
            ?.sortedByDescending { it.modifiedAt }
            ?: emptyList()

    fun describe(file: File): GeneratedCd {
        val info = Id3Reader.readInfo(file) // 10.2: uma única leitura da tag (sem carregar a capa)
        return GeneratedCd(
            file = file,
            title = info.title ?: file.nameWithoutExtension,
            artist = info.artist,
            sizeBytes = file.length(),
            modifiedAt = file.lastModified(),
            durationMs = info.durationMs,
            trackCount = info.chapters.size,
            hasCue = File(file.parentFile, file.nameWithoutExtension + ".cue").isFile
        )
    }

    /** Soma dos tamanhos (MP3 + CUE) ocupados pelos CDs gerados. */
    fun totalBytes(cds: List<GeneratedCd>): Long = cds.sumOf { it.sizeBytes + (if (it.hasCue) it.cueFile.length() else 0L) }

    /**
     * Exclui o MP3 gerado e o CUE interno ao lado (somente arquivos em filesDir/exports; músicas originais e a
     * biblioteca nunca são tocadas). Devolve true se o MP3 deixou de existir.
     */
    fun delete(context: Context, cd: GeneratedCd): Boolean {
        val mp3Gone = !cd.file.exists() || cd.file.delete()
        if (mp3Gone) ExportNaming.forget(context, cd.file.name)
        val cue = cd.cueFile
        if (cue.exists()) cue.delete()
        return mp3Gone
    }

    sealed interface RenameResult {
        data class Ok(val file: File) : RenameResult
        data class Error(val message: String) : RenameResult
    }

    /** Renomeia o MP3 (e o CUE ao lado). Nunca sobrescreve outro arquivo; nomes inválidos são saneados. */
    fun rename(context: Context, cd: GeneratedCd, newName: String): RenameResult {
        val base = ExportService.safeFileName(newName)
        if (newName.isBlank()) return RenameResult.Error("Informe um nome")
        if (base == cd.file.nameWithoutExtension) return RenameResult.Ok(cd.file)
        val target = File(cd.file.parentFile, "$base.mp3")
        val targetCue = File(cd.file.parentFile, "$base.cue")
        if (target.exists() || (cd.hasCue && targetCue.exists())) return RenameResult.Error("Já existe um CD com o nome \"$base\"")
        if (!cd.file.renameTo(target)) return RenameResult.Error("Não foi possível renomear o arquivo")
        if (cd.hasCue && !cd.cueFile.renameTo(targetCue)) {
            target.renameTo(cd.file) // desfaz para não deixar MP3 e CUE com nomes diferentes
            return RenameResult.Error("Não foi possível renomear o CUE")
        }
        ExportNaming.rename(context, cd.file.name, target.name)
        ExportCopies.rename(context, cd.file.name, target.name)
        return RenameResult.Ok(target)
    }
}
