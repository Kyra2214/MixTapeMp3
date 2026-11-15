package com.cdvirtual.app.media.normalization

import com.cdvirtual.app.core.database.TrackEntity
import kotlin.math.max

data class GainPlan(val gainDb: Float, val limitedByPeak: Boolean)

object GainPlanner {
    /**
     * Ganho total = ganho automático (se normalização ativa) + ajuste individual, limitado para que o pico real
     * não passe de [NormalizationSettings.maxTruePeakDb]. Como a equalização pode elevar picos, o limite é reduzido
     * pelo maior reforço de EQ ([eqBoostDb], estimativa conservadora).
     */
    fun plan(
        lufs: Float?, truePeakDb: Float?, userGainDb: Float, settings: NormalizationSettings, eqBoostDb: Float = 0f
    ): GainPlan? {
        val silent = lufs != null && lufs <= -69.9f
        val auto = if (!settings.enabled || lufs == null || silent) 0f else settings.targetLufs - lufs
        val wanted = auto + userGainDb
        if (truePeakDb == null) {
            return if (settings.enabled && lufs == null && userGainDb == 0f) null else GainPlan(wanted, false)
        }
        val maxAllowed = settings.maxTruePeakDb - truePeakDb - eqBoostDb
        return if (wanted > maxAllowed) GainPlan(maxAllowed, true) else GainPlan(wanted, false)
    }

    fun eqBoostDb(t: TrackEntity) = max(0f, max(t.bassDb, max(t.midDb, t.trebleDb)))

    fun forTrack(t: TrackEntity, settings: NormalizationSettings) =
        plan(t.lufs, t.truePeakDb, t.userGainDb, settings, eqBoostDb(t))
}
