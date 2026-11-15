package com.cdvirtual.app.media.engine

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Converte PCM float intercalado de qualquer taxa/número de canais para o formato padrão do CD:
 * [DST_RATE] Hz, estéreo. Reamostragem por sinc janelada (Hann) polifásica com passa-baixas ajustado quando
 * reduz a taxa; mono é duplicado e multicanal é rebaixado para estéreo (centro/surround a -3 dB, LFE descartado).
 * Uso em fluxo: [process] a cada bloco decodificado e [finish] no fim.
 */
class AudioStandardizer(private val srcRate: Int, private val srcChannels: Int) {
    companion object {
        const val DST_RATE = 44100
        const val DST_CHANNELS = 2
        private const val HALF_TAPS = 16
        private const val PHASES = 2048
        private const val OUT_FRAMES = 4096
        private const val SQ = 0.70710678f
    }

    private val passthrough = srcRate == DST_RATE
    private val step = srcRate.toDouble() / DST_RATE
    private val fc = min(1.0, DST_RATE.toDouble() / srcRate)
    private val hw = ceil(HALF_TAPS / fc).toInt()
    private val taps = 2 * hw
    private val table: Array<FloatArray> by lazy(::buildTable)

    private var l = FloatArray(OUT_FRAMES * 2)
    private var r = FloatArray(OUT_FRAMES * 2)
    private var bufStart = if (passthrough) 0L else -(hw - 1).toLong()
    private var bufLen = if (passthrough) 0 else hw - 1
    private var pos = 0.0
    private var totalIn = 0L

    private val out = FloatArray(OUT_FRAMES * DST_CHANNELS)
    private var outFrames = 0

    /** Número total de quadros de saída entregues até agora. */
    var framesOut = 0L
        private set

    private fun sinc(x: Double) = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)

    private fun buildTable(): Array<FloatArray> = Array(PHASES) { p ->
        val frac = p.toDouble() / PHASES
        val row = FloatArray(taps)
        var sum = 0.0
        for (j in 0 until taps) {
            val x = (j - hw + 1) - frac
            val w = if (kotlin.math.abs(x) < hw) 0.5 * (1 + cos(PI * x / hw)) else 0.0
            val h = fc * sinc(fc * x) * w
            row[j] = h.toFloat(); sum += h
        }
        for (j in 0 until taps) row[j] = (row[j] / sum).toFloat()
        row
    }

    private fun ensureCapacity(extra: Int) {
        if (bufLen + extra <= l.size) return
        val n = maxOf(l.size * 2, bufLen + extra)
        l = l.copyOf(n); r = r.copyOf(n)
    }

    /** Rebaixa/duplica canais e acrescenta o bloco ao buffer de entrada. */
    private fun append(buf: FloatArray, count: Int) {
        val frames = count / srcChannels
        ensureCapacity(frames)
        val c = srcChannels
        val norm = when { c <= 2 -> 1f; c == 3 || c == 4 -> 1f / (1f + SQ); else -> 1f / (1f + 2 * SQ) }
        for (f in 0 until frames) {
            val b = f * c
            var lv: Float; var rv: Float
            when {
                c == 1 -> { lv = buf[b]; rv = buf[b] }
                c == 2 -> { lv = buf[b]; rv = buf[b + 1] }
                c == 3 -> { lv = (buf[b] + SQ * buf[b + 2]) * norm; rv = (buf[b + 1] + SQ * buf[b + 2]) * norm }
                c == 4 -> { lv = (buf[b] + SQ * buf[b + 2]) * norm; rv = (buf[b + 1] + SQ * buf[b + 3]) * norm }
                else -> { // L R C LFE Ls Rs [...]
                    lv = (buf[b] + SQ * buf[b + 2] + SQ * buf[b + 4]) * norm
                    rv = (buf[b + 1] + SQ * buf[b + 2] + SQ * buf[b + 5]) * norm
                }
            }
            l[bufLen + f] = lv; r[bufLen + f] = rv
        }
        bufLen += frames
        totalIn += frames
    }

    private fun put(lv: Float, rv: Float, emit: (FloatArray, Int) -> Unit) {
        out[outFrames * 2] = lv; out[outFrames * 2 + 1] = rv
        if (++outFrames == OUT_FRAMES) flush(emit)
    }

    private fun flush(emit: (FloatArray, Int) -> Unit) {
        if (outFrames > 0) { emit(out, outFrames * 2); framesOut += outFrames; outFrames = 0 }
    }

    private fun produce(emit: (FloatArray, Int) -> Unit, untilEnd: Boolean) {
        while (true) {
            val i = floor(pos).toLong()
            if (untilEnd && pos >= totalIn) break
            if (i + hw >= bufStart + bufLen) break
            val row = table[minOf(((pos - i) * PHASES).toInt(), PHASES - 1)]
            val base = (i - hw + 1 - bufStart).toInt()
            var sl = 0f; var sr = 0f
            for (j in 0 until taps) { val k = row[j]; sl += k * l[base + j]; sr += k * r[base + j] }
            put(sl, sr, emit)
            pos += step
        }
        val drop = (floor(pos).toLong() - hw + 1 - bufStart).toInt()
        if (drop > 0) {
            System.arraycopy(l, drop, l, 0, bufLen - drop)
            System.arraycopy(r, drop, r, 0, bufLen - drop)
            bufLen -= drop; bufStart += drop
        }
    }

    /** [emit] recebe blocos estéreo intercalados a 44,1 kHz; o buffer é reutilizado. */
    fun process(buf: FloatArray, count: Int, emit: (FloatArray, Int) -> Unit) {
        append(buf, count)
        if (passthrough) {
            for (f in 0 until bufLen) put(l[f], r[f], emit)
            bufLen = 0; bufStart = 0
        } else produce(emit, untilEnd = false)
    }

    /** Descarrega o restante (cauda do filtro). */
    fun finish(emit: (FloatArray, Int) -> Unit) {
        if (!passthrough) {
            ensureCapacity(hw)
            for (k in 0 until hw) { l[bufLen + k] = 0f; r[bufLen + k] = 0f }
            bufLen += hw
            produce(emit, untilEnd = true)
        }
        flush(emit)
    }
}
