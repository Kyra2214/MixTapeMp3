package com.cdvirtual.app.media.metadata

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Dados descritivos do CD gravados como tags ID3 no MP3 final. */
/**
 * [album] é o nome do CD (padrão: igual ao título). [artist] (opcional) vai em TPE1 e TPE2; [coverJpeg] (opcional)
 * é a capa em JPEG, gravada como quadro APIC (capa frontal). [durationMs] (TLEN) é a duração do áudio em ms; o
 * Mp3Exporter a preenche com a duração real concatenada.
 * [chapters] são as faixas do CD (título + início/fim), gravadas como CHAP/CTOC (7.5).
 */
data class CdMetadata(
    val title: String,
    val album: String = title,
    val year: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
    val artist: String? = null,
    val coverJpeg: ByteArray? = null,
    val durationMs: Long? = null,
    val chapters: List<Id3Chapter> = emptyList()
)

object Id3Writer {
    /** Monta a tag do CD (7.1: título e ano; 7.2: álbum; 7.3: artista e capa; 7.4: duração (TLEN); 7.5: capítulos por faixa). */
    fun build(meta: CdMetadata): Id3Tag = Id3Tag()
        .text("TIT2", meta.title)
        .text("TALB", meta.album)
        .text("TPE1", meta.artist.orEmpty())
        .text("TPE2", meta.artist.orEmpty())
        .text("TYER", meta.year.toString())
        .chapters(meta.chapters)
        .apply { meta.durationMs?.takeIf { it > 0 }?.let { numeric("TLEN", it) } }
        .apply { meta.coverJpeg?.takeIf { it.isNotEmpty() }?.let { picture(it) } }

    /**
     * Grava em [out] a [tag] seguida do áudio de [audio] (que não deve ter tag ID3v2 própria). Escreve em arquivo novo,
     * sem alterar [audio]; o chamador decide quando apagar o original.
     */
    fun prepend(tag: Id3Tag, audio: File, out: File) {
        val bytes = tag.toBytes()
        FileOutputStream(out).use { fos ->
            val os = BufferedOutputStream(fos, 256 * 1024)
            if (!tag.isEmpty) os.write(bytes)
            BufferedInputStream(FileInputStream(audio), 256 * 1024).use { it.copyTo(os) }
            os.flush()
            fos.fd.sync()
        }
    }
}
