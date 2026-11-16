package com.cdvirtual.app.media.engine

import android.content.Context
import com.cdvirtual.app.core.database.TrackEntity
import com.cdvirtual.app.media.encoder.LameMp3Encoder
import com.cdvirtual.app.media.encoder.Mp3Encoder
import com.cdvirtual.app.media.equalization.CdAudioSettings
import com.cdvirtual.app.media.metadata.CdMetadata
import com.cdvirtual.app.media.metadata.Id3Chapter
import com.cdvirtual.app.media.metadata.Id3Writer
import com.cdvirtual.app.media.normalization.NormalizationSettings
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

data class Mp3Result(val file: File, val concat: ConcatResult)

/**
 * Gera o MP3 único do CD: decodifica, padroniza, processa e concatena (6.1–6.4) e codifica em um só arquivo.
 * Escreve em "<nome>.part" e só renomeia para o nome final quando tudo termina com sucesso; em falha ou
 * cancelamento o parcial é removido, e um destino existente nunca é substituído por um arquivo incompleto.
 */
object Mp3Exporter {
    /** Completa a tag com a duração real e, se não houver capítulos, um por faixa (título e tempos reais da concatenação). */
    internal fun withTimings(meta: CdMetadata, tracks: List<TrackEntity>, concat: ConcatResult): CdMetadata {
        val titles = tracks.associate { it.id to it.title }
        val chapters = meta.chapters.ifEmpty {
            concat.spans.map { Id3Chapter(titles[it.trackId].orEmpty(), it.startMs, it.startMs + it.durationMs) }
        }
        return meta.copy(durationMs = meta.durationMs ?: concat.totalMs, chapters = chapters)
    }

    fun export(
        context: Context,
        tracks: List<TrackEntity>,
        settings: NormalizationSettings,
        target: File,
        cancelCheck: () -> Unit = {},
        onProgress: (Float) -> Unit = {},
        metadata: CdMetadata? = null,
        audioSettings: CdAudioSettings? = null,
        encoderFactory: () -> Mp3Encoder = { LameMp3Encoder() }
    ): Mp3Result {
        target.absoluteFile.parentFile?.mkdirs()
        val part = File(target.absolutePath + ".part")
        var concat: ConcatResult? = null
        var ok = false
        try {
            encoderFactory().use { enc ->
                FileOutputStream(part).use { fos ->
                    val os = BufferedOutputStream(fos, 256 * 1024)
                    var left = ShortArray(0)
                    var right = ShortArray(0)
                    val quantizer = Pcm16Quantizer() // 10.4: dither TPDF na conversão para 16 bits
                    concat = CdConcatenator.concat(context, tracks, settings, cancelCheck, audioSettings, onProgress) { buf, n ->
                        val frames = n / 2
                        if (left.size < frames) { left = ShortArray(frames); right = ShortArray(frames) }
                        for (i in 0 until frames) {
                            left[i] = quantizer.quantize(buf[2 * i]); right[i] = quantizer.quantize(buf[2 * i + 1])
                        }
                        os.write(enc.encode(left, right, frames))
                    }
                    os.write(enc.finish())
                    os.flush()
                    fos.fd.sync()
                }
            }
            val report = Mp3Validator.validate(context, part, concat!!.totalMs)
            if (!report.ok) throw IllegalStateException("MP3 gerado é inválido: ${report.summary()}")
            var finalPart = part
            if (metadata != null) {
                val tagged = File(target.absolutePath + ".tagged")
                try {
                    Id3Writer.prepend(Id3Writer.build(withTimings(metadata, tracks, concat!!)), part, tagged)
                    val check = Mp3Validator.validate(context, tagged, concat!!.totalMs)
                    if (!check.ok) throw IllegalStateException("MP3 com tags é inválido: ${check.summary()}")
                } catch (e: Throwable) { tagged.delete(); throw e }
                part.delete()
                finalPart = tagged
            }
            if (target.exists() && !target.delete()) throw java.io.IOException("Não foi possível substituir ${target.name}")
            if (!finalPart.renameTo(target)) throw java.io.IOException("Não foi possível finalizar ${target.name}")
            ok = true
            return Mp3Result(target, concat!!)
        } finally {
            if (!ok) { part.delete(); File(target.absolutePath + ".tagged").delete() }
        }
    }
}
