package com.cdvirtual.app.media.normalization

import android.content.Context
import android.net.Uri
import com.cdvirtual.app.media.decoder.PcmDecoder
import com.cdvirtual.app.media.equalization.Biquad
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

/**
 * @property lufs loudness integrado (ITU-R BS.1770 com gating de -70 LUFS absoluto e -10 LU relativo);
 * -70 indica silêncio ou faixa mais curta que 400 ms.
 * @property truePeakDb pico real estimado com sobreamostragem 4x (interpolação cúbica).
 */
data class LoudnessResult(val peakDb: Float, val rmsDb: Float, val lufs: Float, val truePeakDb: Float,
    val bandBassDb: Float = -120f, val bandMidDb: Float = -120f, val bandTrebleDb: Float = -120f
)

object LoudnessAnalyzer {
    private const val SILENCE_LUFS = -70f

    /** T-302: fases de sobreamostragem 4x, criadas uma única vez (antes: um array novo por amostra e canal). */
    private val TP_PHASES = floatArrayOf(0.25f, 0.5f, 0.75f)

    private class State(val sampleRate: Int, val channels: Int) {
        val shelf = Array(channels) { Biquad.kShelf(sampleRate) }
        val highPass = Array(channels) { Biquad.kHighPass(sampleRate) }
        val history = Array(channels) { FloatArray(4) }
        val subLength = sampleRate / 10
        val ring = DoubleArray(4)
        var ringPos = 0
        var ringFilled = 0
        var subFrames = 0
        var subAcc = 0.0
        val bassLp = Array(channels) { Biquad.lowPass(sampleRate, 250.0) }
        val midHp = Array(channels) { Biquad.highPass(sampleRate, 250.0) }
        val midLp = Array(channels) { Biquad.lowPass(sampleRate, 4000.0) }
        val trebleHp = Array(channels) { Biquad.highPass(sampleRate, 4000.0) }
        var bassSq = 0.0
        var midSq = 0.0
        var trebleSq = 0.0
        val blocks = ArrayList<Double>()
        var peak = 0f
        var truePeak = 0f
        var sumSq = 0.0
        var samples = 0L

        fun feed(buf: FloatArray, count: Int) {
            val frames = count / channels
            for (f in 0 until frames) {
                for (c in 0 until channels) {
                    val x = buf[f * channels + c]
                    val a = abs(x)
                    if (a > peak) peak = a
                    sumSq += (x * x).toDouble()
                    val y = highPass[c].process(shelf[c].process(x.toDouble()))
                    subAcc += y * y
                    val bass = bassLp[c].process(x.toDouble())
                    val mid = midLp[c].process(midHp[c].process(x.toDouble()))
                    val treble = trebleHp[c].process(x.toDouble())
                    bassSq += bass * bass; midSq += mid * mid; trebleSq += treble * treble
                    trackTruePeak(history[c], x)
                }
                if (++subFrames == subLength) pushSubBlock()
            }
            samples += frames.toLong() * channels
        }

        private fun trackTruePeak(h: FloatArray, x: Float) {
            h[0] = h[1]; h[1] = h[2]; h[2] = h[3]; h[3] = x
            for (k in TP_PHASES.indices) {
                val t = TP_PHASES[k]
                val t2 = t * t; val t3 = t2 * t
                val v = 0.5f * (2f * h[1] + (-h[0] + h[2]) * t +
                    (2f * h[0] - 5f * h[1] + 4f * h[2] - h[3]) * t2 +
                    (-h[0] + 3f * h[1] - 3f * h[2] + h[3]) * t3)
                if (abs(v) > truePeak) truePeak = abs(v)
            }
            if (abs(x) > truePeak) truePeak = abs(x)
        }

        private fun pushSubBlock() {
            ring[ringPos] = subAcc
            ringPos = (ringPos + 1) % 4
            if (ringFilled < 4) ringFilled++
            subAcc = 0.0
            subFrames = 0
            if (ringFilled == 4) blocks.add(ring.sum() / (4.0 * subLength))
        }

        private fun lufsOf(energy: Double) = -0.691 + 10 * log10(energy)

        fun integratedLufs(): Float {
            val gated1 = blocks.filter { lufsOf(it) > -70.0 }
            if (gated1.isEmpty()) return SILENCE_LUFS
            val relative = lufsOf(gated1.average()) - 10.0
            val gated2 = gated1.filter { lufsOf(it) > relative }
            if (gated2.isEmpty()) return SILENCE_LUFS
            return lufsOf(gated2.average()).toFloat()
        }
    }

    /** Lê o arquivo original (somente leitura). [cancelCheck] deve lançar exceção se cancelado. */
    fun analyze(context: Context, uri: Uri, cancelCheck: () -> Unit = {}): LoudnessResult {
        var state: State? = null
        PcmDecoder.decode(context, uri, onFormat = { state = State(it.sampleRate, it.channels) }) { buf, n ->
            cancelCheck()
            state!!.feed(buf, n)
            true
        }
        val s = state
        if (s == null || s.samples == 0L) throw IllegalStateException("Áudio vazio ou ilegível")
        return LoudnessResult(
            peakDb = 20 * log10(max(s.peak, 1e-9f)),
            rmsDb = (10 * log10(max(s.sumSq / s.samples, 1e-12))).toFloat(),
            lufs = s.integratedLufs(),
            truePeakDb = 20 * log10(max(s.truePeak, 1e-9f)),
            bandBassDb = (10 * log10(max(s.bassSq / s.samples, 1e-12))).toFloat(),
            bandMidDb = (10 * log10(max(s.midSq / s.samples, 1e-12))).toFloat(),
            bandTrebleDb = (10 * log10(max(s.trebleSq / s.samples, 1e-12))).toFloat()
        )
    }
}
