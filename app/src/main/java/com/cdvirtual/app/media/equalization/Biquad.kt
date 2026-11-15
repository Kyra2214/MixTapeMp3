package com.cdvirtual.app.media.equalization

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Filtro biquad (forma transposta II) de um canal, com coeficientes já normalizados por a0. */
class Biquad(
    private val b0: Double, private val b1: Double, private val b2: Double,
    private val a1: Double, private val a2: Double
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    companion object {
        /** Estágio 1 da ponderação K (ITU-R BS.1770): shelf de agudos. */
        fun kShelf(sampleRate: Int): Biquad {
            val f0 = 1681.974450955533; val g = 3.999843853973347; val q = 0.7071752369554196
            val k = tan(PI * f0 / sampleRate)
            val vh = 10.0.pow(g / 20.0)
            val vb = vh.pow(0.4996667741545416)
            val a0 = 1.0 + k / q + k * k
            return Biquad(
                (vh + vb * k / q + k * k) / a0, 2.0 * (k * k - vh) / a0, (vh - vb * k / q + k * k) / a0,
                2.0 * (k * k - 1.0) / a0, (1.0 - k / q + k * k) / a0
            )
        }

        private fun normalized(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

        /** Shelf de graves (RBJ cookbook, S=1). */
        fun lowShelf(sampleRate: Int, f0: Double, gainDb: Double): Biquad {
            val a = 10.0.pow(gainDb / 40.0)
            val w = 2 * PI * minOf(f0, sampleRate * 0.45) / sampleRate
            val cw = cos(w)
            val sa = 2 * sqrt(a) * (sin(w) / 2 * sqrt(2.0))
            return normalized(
                a * ((a + 1) - (a - 1) * cw + sa), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - sa),
                (a + 1) + (a - 1) * cw + sa, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - sa
            )
        }

        /** Shelf de agudos (RBJ cookbook, S=1). */
        fun highShelf(sampleRate: Int, f0: Double, gainDb: Double): Biquad {
            val a = 10.0.pow(gainDb / 40.0)
            val w = 2 * PI * minOf(f0, sampleRate * 0.45) / sampleRate
            val cw = cos(w)
            val sa = 2 * sqrt(a) * (sin(w) / 2 * sqrt(2.0))
            return normalized(
                a * ((a + 1) + (a - 1) * cw + sa), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - sa),
                (a + 1) - (a - 1) * cw + sa, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - sa
            )
        }

        /** Filtro de pico para os médios (RBJ cookbook). */
        fun peaking(sampleRate: Int, f0: Double, q: Double, gainDb: Double): Biquad {
            val a = 10.0.pow(gainDb / 40.0)
            val w = 2 * PI * minOf(f0, sampleRate * 0.45) / sampleRate
            val cw = cos(w)
            val alpha = sin(w) / (2 * q)
            return normalized(
                1 + alpha * a, -2 * cw, 1 - alpha * a,
                1 + alpha / a, -2 * cw, 1 - alpha / a
            )
        }

        /** Passa-baixas de 2ª ordem (RBJ cookbook), usado na análise espectral. */
        fun lowPass(sampleRate: Int, f0: Double, q: Double = 0.7071067811865476): Biquad {
            val w = 2 * PI * minOf(f0, sampleRate * 0.45) / sampleRate
            val cw = cos(w); val alpha = sin(w) / (2 * q)
            return normalized((1 - cw) / 2, 1 - cw, (1 - cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
        }

        /** Passa-altas de 2ª ordem (RBJ cookbook), usado na análise espectral. */
        fun highPass(sampleRate: Int, f0: Double, q: Double = 0.7071067811865476): Biquad {
            val w = 2 * PI * minOf(f0, sampleRate * 0.45) / sampleRate
            val cw = cos(w); val alpha = sin(w) / (2 * q)
            return normalized((1 + cw) / 2, -(1 + cw), (1 + cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
        }

        /** Estágio 2 (RLB): passa-altas em ~38 Hz. */
        fun kHighPass(sampleRate: Int): Biquad {
            val f0 = 38.13547087602444; val q = 0.5003270373238773
            val k = tan(PI * f0 / sampleRate)
            val a0 = 1.0 + k / q + k * k
            return Biquad(1.0, -2.0, 1.0, 2.0 * (k * k - 1.0) / a0, (1.0 - k / q + k * k) / a0)
        }
    }
}
