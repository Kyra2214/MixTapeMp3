package com.cdvirtual.app.core.common

import android.content.Context

/** Opções gerais do app (tela Configurações). Normalização de volume e CUE têm seus próprios armazenamentos. */
object AppSettings {
    private const val PREFS = "app_settings"
    private const val KEY_AUTO_SAVE = "auto_save_to_folder"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Ao terminar de gerar uma playlist, copia o MP3 sozinho para a pasta de destino escolhida (padrão: ligado). */
    fun autoSaveToFolder(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_SAVE, true)

    fun setAutoSaveToFolder(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_SAVE, value).apply()
    }
}
