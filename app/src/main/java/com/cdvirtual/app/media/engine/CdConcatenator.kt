package com.cdvirtual.app.media.engine

import android.content.Context
import com.cdvirtual.app.core.database.TrackEntity
import com.cdvirtual.app.media.equalization.CdAudioSettings
import com.cdvirtual.app.media.normalization.NormalizationSettings

/** Posição de uma faixa no fluxo contínuo do CD, em quadros a 44,1 kHz. */
data class TrackSpan(val trackId: Long, val startFrame: Long, val frames: Long) {
    val startMs get() = startFrame * 1000 / AudioStandardizer.DST_RATE
    val durationMs get() = frames * 1000 / AudioStandardizer.DST_RATE
}

data class ConcatResult(val spans: List<TrackSpan>, val totalFrames: Long) {
    val totalMs get() = totalFrames * 1000 / AudioStandardizer.DST_RATE
}

/**
 * Concatena as faixas do CD, na ordem dada, em um único fluxo PCM contínuo (sem pausas entre faixas),
 * já processado (6.3). Registra o início de cada faixa para ID3/CUE/tempos (Fases 07 e 08).
 * Qualquer falha de decodificação interrompe o CD inteiro com [DecodeException] identificando a faixa
 * (ASSUMINDO: nunca omitir faixa em silêncio, para não gerar um CD diferente do planejado).
 */
object CdConcatenator {
    fun concat(
        context: Context,
        tracks: List<TrackEntity>,
        settings: NormalizationSettings,
        cancelCheck: () -> Unit = {},
        audioSettings: CdAudioSettings? = null,
        onProgress: (Float) -> Unit = {},
        sink: (samples: FloatArray, count: Int) -> Unit
    ): ConcatResult {
        require(tracks.isNotEmpty()) { "O CD não tem músicas" }
        val totalWeight = tracks.sumOf { it.durationMs.coerceAtLeast(1) }.toDouble()
        var done = 0.0
        val spans = ArrayList<TrackSpan>(tracks.size)
        var cursor = 0L
        for (t in tracks) {
            cancelCheck()
            val w = t.durationMs.coerceAtLeast(1)
            val frames = ProcessedPcm.decode(
                context, t, settings, cancelCheck,
                audioSettings = audioSettings,
                onProgress = { f -> onProgress(((done + w * f) / totalWeight).toFloat().coerceIn(0f, 1f)) },
                sink = sink
            )
            spans += TrackSpan(t.id, cursor, frames)
            cursor += frames
            done += w
        }
        onProgress(1f)
        return ConcatResult(spans, cursor)
    }
}
