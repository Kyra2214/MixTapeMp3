package com.cdvirtual.app.media.equalization

/** Equalizador de 3 bandas: graves (shelf 100 Hz), médios (pico 1 kHz, Q 0,7) e agudos (shelf 8 kHz). */
class ThreeBandEqualizer(sampleRate: Int, private val channels: Int, bassDb: Float, midDb: Float, trebleDb: Float) {
    val isBypass = bassDb == 0f && midDb == 0f && trebleDb == 0f

    private val filters: Array<Array<Biquad>> = Array(channels) {
        buildList<Biquad> {
            if (bassDb != 0f) add(Biquad.lowShelf(sampleRate, 100.0, bassDb.toDouble()))
            if (midDb != 0f) add(Biquad.peaking(sampleRate, 1000.0, 0.7, midDb.toDouble()))
            if (trebleDb != 0f) add(Biquad.highShelf(sampleRate, 8000.0, trebleDb.toDouble()))
        }.toTypedArray()
    }

    /** Processa PCM float intercalado no próprio buffer. Laços indexados: sem alocação por amostra (10.4). */
    fun process(buf: FloatArray, count: Int) {
        if (isBypass) return
        var ch = 0
        for (i in 0 until count) {
            val chain = filters[ch]
            var y = buf[i].toDouble()
            for (k in chain.indices) y = chain[k].process(y)
            buf[i] = y.toFloat()
            if (++ch == channels) ch = 0
        }
    }
}
