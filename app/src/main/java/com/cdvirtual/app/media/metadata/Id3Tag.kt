package com.cdvirtual.app.media.metadata

import java.io.ByteArrayOutputStream

/**
 * Construtor de tag ID3v2.3 (a versão mais compatível com players Android, carros e Bluetooth).
 * Textos em UTF-16 com BOM (codificação 1). Capa (APIC) entra na 7.3; duração (TLEN) na 7.4; capítulos por faixa (CHAP/CTOC) na 7.5.
 */
/** Capítulo ID3: título e intervalo em milissegundos. */
data class Id3Chapter(val title: String, val startMs: Long, val endMs: Long)

class Id3Tag {
    private val frames = ByteArrayOutputStream()
    var isEmpty = true
        private set

    /** Acrescenta um quadro bruto [id] (4 caracteres ASCII) com o [body] dado. */
    fun frame(id: String, body: ByteArray): Id3Tag {
        require(id.length == 4) { "ID de quadro inválido: $id" }
        frames.write(id.toByteArray(Charsets.US_ASCII))
        frames.write(be32(body.size))
        frames.write(byteArrayOf(0, 0)) // flags
        frames.write(body)
        isEmpty = false
        return this
    }

    /** Quadro de texto (TIT2, TALB, TPE1, TYER...). Valores em branco são ignorados. */
    fun text(id: String, value: String): Id3Tag {
        val v = value.trim()
        if (v.isEmpty()) return this
        return frame(id, textBody(v))
    }

    /** Quadro numérico (TLEN...) em ISO-8859-1 (codificação 0), como a especificação ID3v2.3 define para números em texto. */
    fun numeric(id: String, value: Long): Id3Tag =
        frame(id, byteArrayOf(0) + value.toString().toByteArray(Charsets.US_ASCII))

    /**
     * Capítulos (uma entrada por faixa do CD): quadros CHAP com título (TIT2) e tempos em ms, mais um CTOC raiz
     * ordenado que os lista. Segue a especificação ID3v2 Chapter Frame Addendum (usada em ID3v2.3 por vários players).
     */
    fun chapters(items: List<Id3Chapter>): Id3Tag {
        if (items.isEmpty()) return this
        val ids = items.indices.map { "chp$it" }
        items.forEachIndexed { i, c ->
            val b = ByteArrayOutputStream()
            b.write(ids[i].toByteArray(Charsets.US_ASCII)); b.write(0)
            b.write(be32(c.startMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()))
            b.write(be32(c.endMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()))
            b.write(be32(-1)); b.write(be32(-1)) // sem deslocamentos de bytes
            val title = c.title.trim()
            if (title.isNotEmpty()) {
                val tb = textBody(title)
                b.write("TIT2".toByteArray(Charsets.US_ASCII)); b.write(be32(tb.size)); b.write(byteArrayOf(0, 0)); b.write(tb)
            }
            frame("CHAP", b.toByteArray())
        }
        val toc = ByteArrayOutputStream()
        toc.write("toc".toByteArray(Charsets.US_ASCII)); toc.write(0)
        toc.write(0x03) // raiz + ordenado
        toc.write(ids.size)
        ids.forEach { toc.write(it.toByteArray(Charsets.US_ASCII)); toc.write(0) }
        return frame("CTOC", toc.toByteArray())
    }

    /** Quadro APIC com a capa frontal (tipo 3) em JPEG; descrição vazia, texto em ISO-8859-1 (codificação 0). */
    fun picture(jpeg: ByteArray): Id3Tag {
        val body = ByteArrayOutputStream(jpeg.size + 32)
        body.write(0) // codificação ISO-8859-1
        body.write("image/jpeg".toByteArray(Charsets.US_ASCII)); body.write(0) // MIME terminado em nulo
        body.write(3) // capa frontal
        body.write(0) // descrição vazia
        body.write(jpeg)
        return frame("APIC", body.toByteArray())
    }

    /** Bytes finais da tag, prontos para ficar no início do arquivo MP3. */
    fun toBytes(): ByteArray {
        val payload = frames.toByteArray()
        val out = ByteArrayOutputStream(10 + payload.size)
        out.write("ID3".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(3, 0, 0)) // v2.3.0, sem flags
        out.write(syncsafe(payload.size))
        out.write(payload)
        return out.toByteArray()
    }

    companion object {
        fun textBody(v: String): ByteArray =
            byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte()) + v.toByteArray(Charsets.UTF_16LE)

        fun be32(n: Int) = byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())

        fun syncsafe(n: Int): ByteArray {
            require(n in 0..0x0FFFFFFF) { "Tag ID3 grande demais" }
            return byteArrayOf(((n ushr 21) and 0x7F).toByte(), ((n ushr 14) and 0x7F).toByte(), ((n ushr 7) and 0x7F).toByte(), (n and 0x7F).toByte())
        }
    }
}
