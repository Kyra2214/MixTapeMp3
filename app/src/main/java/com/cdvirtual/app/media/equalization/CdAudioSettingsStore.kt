package com.cdvirtual.app.media.equalization

import android.content.Context

/** Persiste as opções por projeto de CD, sem alterar preferências de outros discos. */
class CdAudioSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("cd_audio_settings", Context.MODE_PRIVATE)

    fun load(cdId: Long): CdAudioSettings {
        val prefix = "cd_$cdId"
        return CdAudioSettings(
            presetId = prefs.getString("${prefix}_preset", "original") ?: "original",
            bassDb = prefs.getFloat("${prefix}_bass", 0f),
            midDb = prefs.getFloat("${prefix}_mid", 0f),
            trebleDb = prefs.getFloat("${prefix}_treble", 0f),
            normalize = prefs.getBoolean("${prefix}_normalize", true)
        )
    }

    fun save(cdId: Long, settings: CdAudioSettings) {
        val prefix = "cd_$cdId"
        prefs.edit()
            .putString("${prefix}_preset", settings.presetId)
            .putFloat("${prefix}_bass", settings.bassDb)
            .putFloat("${prefix}_mid", settings.midDb)
            .putFloat("${prefix}_treble", settings.trebleDb)
            .putBoolean("${prefix}_normalize", settings.normalize)
            .apply()
    }
}
