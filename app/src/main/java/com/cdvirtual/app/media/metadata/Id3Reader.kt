package com.cdvirtual.app.media.metadata

import java.io.File
import java.io.RandomAccessFile

/** Leitor mínimo de ID3v2.3 (o que o Id3Writer grava): capítulos por faixa e capa. Somente leitura; nunca lança exceção. */
object Id3Reader {
    private const val MAX_TAG = 32 * 1024 * 1024
    private const val MAX_FRAME = 16 * 1024 * 1024

    /** Dados de texto e capítulos lidos numa única passada (sem carregar a capa). */
    data class Info(val title: String?, val album: String?, val artist: String?, val durationMs: Long?, val chapters: List<Id3Chapter>)

    private val INFO_IDS = setOf("TIT2", "TALB", "TPE1", "TLEN", "CHAP")

    /** 10.2: título, álbum, artista, duração e capítulos com UMA leitura da tag (antes eram quatro, cada uma carregando a capa). */
    fun readInfo(file: File): Info = try {
        val fr = readFrames(file) { it in INFO_IDS }
        fun text(id: String) = fr.firstOrNull { it.id == id }?.let { decodeText(it.body) }?.ifBlank { null }
        Info(text("TIT2"), text("TALB"), text("TPE1"), text("TLEN")?.toLongOrNull(), chaptersOf(fr))
    } catch (_: Throwable) { Info(null, null, null, null, emptyList()) }

    /** Capítulos (CHAP) do arquivo, ordenados por início; lista vazia se não houver tag/capítulos ou se ela estiver inválida. */
    fun readChapters(file: File): List<Id3Chapter> = try {
        chaptersOf(readFrames(file) { it == "CHAP" })
    } catch (_: Throwable) { emptyList() }

    /** Imagem da capa (APIC; prefere a frontal, tipo 3) ou null. */
    fun readCover(file: File): ByteArray? = try {
        coverOf(readFrames(file) { it == "APIC" })
    } catch (_: Throwable) { null }

    /**
     * Lê só os quadros aceitos por [wanted]: percorre os cabeçalhos de quadro com seek e pula os demais corpos, de modo que
     * ler texto/capítulos nunca carrega a capa (até 8 MB) e ler a capa não carrega o resto. Tags acima de 32 MB são ignoradas.
     */
    internal fun readFrames(file: File, wanted: (String) -> Boolean): List<RawFrame> {
        RandomAccessFile(file, "r").use { f ->
            if (f.length() < 10) return emptyList()
            val h = ByteArray(10)
            f.readFully(h)
            if (h[0] != 'I'.code.toByte() || h[1] != 'D'.code.toByte() || h[2] != '3'.code.toByte() || h[3].toInt() != 3) return emptyList()
            val size = ((h[6].toInt() and 0x7F) shl 21) or ((h[7].toInt() and 0x7F) shl 14) or ((h[8].toInt() and 0x7F) shl 7) or (h[9].toInt() and 0x7F)
            if (size <= 0 || size > MAX_TAG || size > f.length() - 10) return emptyList()
            val out = ArrayList<RawFrame>()
            val fh = ByteArray(10)
            var p = 0L
            while (p + 10 <= size) {
                f.seek(10 + p)
                f.readFully(fh)
                if (fh[0].toInt() == 0) break // preenchimento
                val id = String(fh, 0, 4, Charsets.US_ASCII)
                val fsize = be32(fh, 4)
                if (fsize < 0 || p + 10 + fsize > size) break
                if (wanted(id) && fsize <= MAX_FRAME) {
                    val body = ByteArray(fsize)
                    f.readFully(body)
                    out += RawFrame(id, body)
                }
                p += 10 + fsize
            }
            return out
        }
    }

    internal fun chaptersOf(fr: List<RawFrame>): List<Id3Chapter> = fr.filter { it.id == "CHAP" }.mapNotNull { f ->
        val b = f.body
        val z = b.indexOf(0.toByte())
        if (z < 0 || z + 1 + 16 > b.size) return@mapNotNull null
        val start = be32(b, z + 1).toLong() and 0xFFFFFFFFL
        val end = be32(b, z + 5).toLong() and 0xFFFFFFFFL
        val title = frames(b.copyOfRange(z + 17, b.size)).firstOrNull { it.id == "TIT2" }?.let { decodeText(it.body) }.orEmpty()
        Id3Chapter(title, start, end)
    }.sortedBy { it.startMs }

    internal fun coverOf(fr: List<RawFrame>): ByteArray? {
        val pics = fr.filter { it.id == "APIC" }.mapNotNull { f ->
            val b = f.body
            if (b.size < 4) return@mapNotNull null
            val enc = b[0].toInt()
            val mimeEnd = (1 until b.size).firstOrNull { b[it].toInt() == 0 } ?: return@mapNotNull null
            val type = b.getOrNull(mimeEnd + 1)?.toInt() ?: return@mapNotNull null
            var p = mimeEnd + 2
            if (enc == 1 || enc == 2) { // descrição UTF-16: termina em 00 00 alinhado
                while (p + 1 < b.size && !(b[p].toInt() == 0 && b[p + 1].toInt() == 0)) p += 2
                p += 2
            } else {
                while (p < b.size && b[p].toInt() != 0) p++
                p += 1
            }
            if (p >= b.size) null else type to b
        }
        // devolve a imagem sem copiar o corpo inteiro uma segunda vez além do recorte final
        val best = pics.firstOrNull { it.first == 3 } ?: pics.firstOrNull() ?: return null
        return coverBytes(best.second)
    }

    private fun coverBytes(b: ByteArray): ByteArray? {
        val enc = b[0].toInt()
        val mimeEnd = (1 until b.size).first { b[it].toInt() == 0 }
        var p = mimeEnd + 2
        if (enc == 1 || enc == 2) {
            while (p + 1 < b.size && !(b[p].toInt() == 0 && b[p + 1].toInt() == 0)) p += 2
            p += 2
        } else {
            while (p < b.size && b[p].toInt() != 0) p++
            p += 1
        }
        return if (p >= b.size) null else b.copyOfRange(p, b.size)
    }

    internal data class RawFrame(val id: String, val body: ByteArray)

    internal fun frames(data: ByteArray): List<RawFrame> {
        val out = ArrayList<RawFrame>()
        var p = 0
        while (p + 10 <= data.size && data[p].toInt() != 0) {
            val id = String(data, p, 4, Charsets.US_ASCII)
            val size = be32(data, p + 4)
            if (size < 0 || p + 10 + size > data.size) break
            out += RawFrame(id, data.copyOfRange(p + 10, p + 10 + size))
            p += 10 + size
        }
        return out
    }

    internal fun decodeText(body: ByteArray): String {
        if (body.isEmpty()) return ""
        val data = body.copyOfRange(1, body.size)
        val s = when (body[0].toInt()) {
            0 -> String(data, Charsets.ISO_8859_1)
            1 -> when {
                data.size >= 2 && data[0] == 0xFF.toByte() && data[1] == 0xFE.toByte() -> String(data, 2, data.size - 2, Charsets.UTF_16LE)
                data.size >= 2 && data[0] == 0xFE.toByte() && data[1] == 0xFF.toByte() -> String(data, 2, data.size - 2, Charsets.UTF_16BE)
                else -> String(data, Charsets.UTF_16LE)
            }
            3 -> String(data, Charsets.UTF_8)
            else -> String(data, Charsets.ISO_8859_1)
        }
        return s.trimEnd('\u0000').trim()
    }

    private fun be32(a: ByteArray, o: Int) =
        ((a[o].toInt() and 0xFF) shl 24) or ((a[o + 1].toInt() and 0xFF) shl 16) or ((a[o + 2].toInt() and 0xFF) shl 8) or (a[o + 3].toInt() and 0xFF)
}
