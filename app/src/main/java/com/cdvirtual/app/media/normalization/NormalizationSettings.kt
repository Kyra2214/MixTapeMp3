package com.cdvirtual.app.media.normalization

import android.content.Context

data class NormalizationSettings(
    val enabled: Boolean = true,
    val targetLufs: Float = -14f,
    val maxTruePeakDb: Float = -1f
)

class NormalizationSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("normalization", Context.MODE_PRIVATE)

    fun load() = NormalizationSettings(
        enabled = prefs.getBoolean("enabled", true),
        targetLufs = prefs.getFloat("target", -14f),
        maxTruePeakDb = prefs.getFloat("max_tp", -1f)
    )

    fun save(s: NormalizationSettings) {
        prefs.edit().putBoolean("enabled", s.enabled).putFloat("target", s.targetLufs)
            .putFloat("max_tp", s.maxTruePeakDb).apply()
    }
}
