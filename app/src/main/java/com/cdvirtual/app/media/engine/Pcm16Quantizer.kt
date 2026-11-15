package com.cdvirtual.app.media.engine

/**
 * Converte amostras float (-1..1) em PCM de 16 bits para o encoder com dither triangular (TPDF, ±1 LSB).
 * Sem dither, a quantização gera distorção correlacionada com o sinal nos trechos de baixo nível (finais de faixa,
 * reverberações); com TPDF ela vira ruído branco constante e inaudível. Silêncio digital exato (0f) permanece 0.
 * Gerador xorshift32 determinístico: a mesma entrada produz sempre o mesmo MP3.
 */
class Pcm16Quantizer(seed: Int = 0x2F6E2B1) {
    private var state = if (seed == 0) 1 else seed

    private fun next(): Int {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        state = x
        return x
    }

    /** Ruído TPDF em (-1, 1) LSB: diferença de dois uniformes em [0, 1). */
    private fun noise(): Float {
        val a = (next() ushr 8) * (1f / 16777216f)
        val b = (next() ushr 8) * (1f / 16777216f)
        return a - b
    }

    fun quantize(x: Float): Short {
        if (x == 0f) return 0
        val v = x.coerceIn(-1f, 1f) * 32767f + noise()
        return Math.round(v).coerceIn(-32768, 32767).toShort()
    }
}
