package com.cdvirtual.app.media.encoder

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

/** Resumo da varredura dos quadros de um MP3. [bitrates] são os kbps distintos encontrados. */
data class Mp3StreamInfo(
    val frames: Long,
    val bitrates: Set<Int>,
    val sampleRates: Set<Int>,
    val junkBytes: Long,
    val audioBytes: Long
) {
    val sampleRate get() = sampleRates.singleOrNull() ?: 0
    val durationMs get() = if (sampleRate == 0) 0L else frames * Mp3Spec.SAMPLES_PER_FRAME * 1000 / sampleRate

    /** Verdadeiro se todos os quadros são MPEG-1 Layer III, 44,1 kHz e 320 kbps (CBR) e não há bytes soltos. */
    val isCbr320: Boolean
        get() = frames > 0 && bitrates == setOf(Mp3Spec.KBPS) && sampleRates == setOf(Mp3Spec.SAMPLE_RATE) && junkBytes == 0L
}

/** Leitor de cabeçalhos de quadros MPEG-1 Layer III (somente leitura), ignorando tags ID3v2 no início e ID3v1 no fim. */
object Mp3FrameScanner {
    private val BITRATES = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val RATES = intArrayOf(44100, 48000, 32000)

    fun scan(file: File): Mp3StreamInfo {
        val total = file.length()
        var end = total
        if (total >= 128) {
            java.io.RandomAccessFile(file, "r").use { raf ->
                raf.seek(total - 128)
                val t = ByteArray(3); raf.readFully(t)
                if (t[0] == 'T'.code.toByte() && t[1] == 'A'.code.toByte() && t[2] == 'G'.code.toByte()) end = total - 128
            }
        }
        BufferedInputStream(FileInputStream(file), 256 * 1024).use { s ->
            var pos = 0L
            val h = ByteArray(10)
            if (s.read(h, 0, 10) == 10 && h[0] == 'I'.code.toByte() && h[1] == 'D'.code.toByte() && h[2] == '3'.code.toByte()) {
                val size = ((h[6].toInt() and 0x7F) shl 21) or ((h[7].toInt() and 0x7F) shl 14) or
                    ((h[8].toInt() and 0x7F) shl 7) or (h[9].toInt() and 0x7F)
                val footer = if ((h[5].toInt() and 0x10) != 0) 10 else 0
                skipFully(s, size.toLong()); pos = 10L + size + footer
                if (footer > 0) skipFully(s, footer.toLong())
            }
            return scanFrom(file, pos, end)
        }
    }

    private fun skipFully(s: java.io.InputStream, n: Long) { var left = n; while (left > 0) { val k = s.skip(left); if (k <= 0) break; left -= k } }

    private fun scanFrom(file: File, start: Long, end: Long): Mp3StreamInfo {
        var frames = 0L; var junk = 0L; var audio = 0L
        val bitrates = HashSet<Int>(); val rates = HashSet<Int>()
        BufferedInputStream(FileInputStream(file), 256 * 1024).use { s ->
            skipFully(s, start)
            var pos = start
            val h = ByteArray(4)
            while (pos + 4 <= end) {
                s.mark(4)
                if (s.read(h, 0, 4) < 4) break
                val b0 = h[0].toInt() and 0xFF; val b1 = h[1].toInt() and 0xFF; val b2 = h[2].toInt() and 0xFF
                val br = (b2 shr 4) and 0xF; val sr = (b2 shr 2) and 3
                val valid = b0 == 0xFF && (b1 and 0xE0) == 0xE0 && ((b1 shr 3) and 3) == 3 && ((b1 shr 1) and 3) == 1 &&
                    br in 1..14 && sr in 0..2
                if (valid) {
                    val kbps = BITRATES[br]; val rate = RATES[sr]
                    val len = 144 * kbps * 1000 / rate + ((b2 shr 1) and 1)
                    if (pos + len > end) { junk += end - pos; break } // quadro truncado
                    frames++; audio += len; bitrates += kbps; rates += rate
                    skipFully(s, (len - 4).toLong()); pos += len
                } else {
                    s.reset(); s.skip(1); junk++; pos++
                }
            }
        }
        return Mp3StreamInfo(frames, bitrates, rates, junk, audio)
    }
}
