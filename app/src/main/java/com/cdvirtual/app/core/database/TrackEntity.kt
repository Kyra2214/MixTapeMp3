package com.cdvirtual.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0,
    val format: String? = null,
    val sizeBytes: Long = 0,
    val coverPath: String? = null,
    val contentHash: String? = null,
    val peakDb: Float? = null,
    val rmsDb: Float? = null,
    val lufs: Float? = null,
    val truePeakDb: Float? = null,
    /** Energia por faixa de frequência (dB, média quadrática): graves <250 Hz, médios 250 Hz–4 kHz, agudos >4 kHz. */
    val bandBassDb: Float? = null,
    val bandMidDb: Float? = null,
    val bandTrebleDb: Float? = null,
    val userGainDb: Float = 0f,
    val bassDb: Float = 0f,
    val midDb: Float = 0f,
    val trebleDb: Float = 0f,
    val addedAt: Long = System.currentTimeMillis()
)
