package com.cdvirtual.app.media.engine

import android.content.Context
import com.cdvirtual.app.core.database.TrackEntity
import com.cdvirtual.app.media.equalization.CdAudioSettings
import com.cdvirtual.app.media.equalization.ThreeBandEqualizer
import com.cdvirtual.app.media.normalization.GainPlanner
import com.cdvirtual.app.media.normalization.NormalizationSettings
import kotlin.math.pow

/**
 * Faixa pronta para o CD: formato padrão (6.2) + equalização da faixa + ganho planejado (normalização, ajuste
 * individual e limite de true peak). Mesma cadeia da pré-visualização (4.8), agora sobre o fluxo completo.
 * Os originais não são alterados.
 */
object ProcessedPcm {
    /** Ganho em dB aplicado à faixa (0 se ainda não analisada). */
    fun gainDb(track: TrackEntity, settings: NormalizationSettings): Float =
        GainPlanner.forTrack(track, settings)?.gainDb ?: 0f

    /** @return total de quadros estéreo entregues ao [sink] (44,1 kHz, float intercalado, limitado a ±1). */
    fun decode(
        context: Context,
        track: TrackEntity,
        settings: NormalizationSettings,
        cancelCheck: () -> Unit = {},
        onProgress: (Float) -> Unit = {},
        audioSettings: CdAudioSettings? = null,
        sink: (samples: FloatArray, count: Int) -> Unit
    ): Long {
        val gain = 10.0.pow(gainDb(track, settings) / 20.0).toFloat()
        val eq = ThreeBandEqualizer(AudioStandardizer.DST_RATE, AudioStandardizer.DST_CHANNELS, audioSettings?.bassDb ?: track.bassDb, audioSettings?.midDb ?: track.midDb, audioSettings?.trebleDb ?: track.trebleDb)
        return StandardPcm.decode(context, track, cancelCheck, onProgress) { buf, n ->
            eq.process(buf, n)
            for (i in 0 until n) buf[i] = (buf[i] * gain).coerceIn(-1f, 1f) // rede de segurança contra clipping
            sink(buf, n)
        }
    }
}
