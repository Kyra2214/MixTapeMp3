package com.cdvirtual.app.feature.cdeditor

import com.cdvirtual.app.core.database.TrackEntity

/** Totais calculados do CD a partir das faixas atuais (recalculados a cada mudança da lista). */
object CdStats {
    /** Soma das durações das faixas; durações desconhecidas (0) não contribuem. */
    /** Taxa fixa do MP3 final (CBR), definida na 6.6. */
    const val MP3_KBPS = com.cdvirtual.app.media.encoder.Mp3Spec.KBPS

    /** Tamanho estimado do MP3 único: duração x taxa CBR (bytes = ms x kbps / 8); metadados/capa (Fase 07) não incluídos. */
    fun estimatedSizeBytes(durationMs: Long, kbps: Int = MP3_KBPS): Long = durationMs.coerceAtLeast(0) * kbps / 8

    fun totalDurationMs(tracks: List<TrackEntity>): Long = tracks.sumOf { it.durationMs.coerceAtLeast(0) }
}
