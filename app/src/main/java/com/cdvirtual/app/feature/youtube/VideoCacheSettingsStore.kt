package com.cdvirtual.app.feature.youtube

import android.content.Context

/**
 * Preferências locais do Cache Offline. Alterar o limite nunca remove itens existentes.
 */
class VideoCacheSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun maxItems(): Int = preferences.getInt(KEY_MAX_ITEMS, DEFAULT_MAX_ITEMS).coerceIn(MIN_ITEMS, MAX_ITEMS)

    fun setMaxItems(value: Int) {
        require(value in MIN_ITEMS..MAX_ITEMS) { "O limite deve ficar entre 1 e 100 vídeos." }
        preferences.edit().putInt(KEY_MAX_ITEMS, value).apply()
    }

    companion object {
        const val DEFAULT_MAX_ITEMS = 3
        const val MIN_ITEMS = 1
        const val MAX_ITEMS = 100
        val PRESETS = listOf(1, 2, 3, 5, 10)
        private const val PREFERENCES = "video_cache_settings"
        private const val KEY_MAX_ITEMS = "max_items"
    }
}
