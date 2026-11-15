package com.cdvirtual.app.media.equalization

import com.cdvirtual.app.core.database.TrackEntity
import kotlin.math.roundToInt

/** Ajuste de EQ sugerido para uma faixa (graves/médios/agudos em dB). */
data class EqSuggestion(val bassDb: Float, val midDb: Float, val trebleDb: Float)

/**
 * Equalização automática do CD inteiro: aproxima o equilíbrio espectral (graves e agudos relativos aos médios)
 * de cada faixa da média do conjunto, para que o CD soe coeso. Os médios são a referência (0 dB).
 */
object AutoEqPlanner {
    const val STRENGTH = 0.6f      // fração da diferença corrigida
    const val MAX_CORRECTION_DB = 6f
    private const val SILENCE_LUFS = -69.9f

    private class Balance(val id: Long, val bass: Float, val treble: Float)

    private fun balanceOf(t: TrackEntity): Balance? {
        val b = t.bandBassDb ?: return null
        val m = t.bandMidDb ?: return null
        val h = t.bandTrebleDb ?: return null
        if (t.lufs != null && t.lufs <= SILENCE_LUFS) return null
        return Balance(t.id, b - m, h - m)
    }

    /** Retorna sugestões por id de faixa; faixas sem análise espectral ou silenciosas ficam de fora. */
    fun plan(tracks: List<TrackEntity>): Map<Long, EqSuggestion> {
        val balances = tracks.mapNotNull(::balanceOf)
        if (balances.size < 2) return balances.associate { it.id to EqSuggestion(0f, 0f, 0f) }
        val refBass = balances.map { it.bass }.average().toFloat()
        val refTreble = balances.map { it.treble }.average().toFloat()
        return balances.associate {
            it.id to EqSuggestion(
                bassDb = correction(refBass - it.bass),
                midDb = 0f,
                trebleDb = correction(refTreble - it.treble)
            )
        }
    }

    private fun correction(diff: Float): Float {
        val c = (diff * STRENGTH).coerceIn(-MAX_CORRECTION_DB, MAX_CORRECTION_DB)
        return (c * 2).roundToInt() / 2f // passo de 0,5 dB, como os sliders
    }
}
