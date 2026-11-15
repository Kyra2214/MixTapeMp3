package com.cdvirtual.app.media.service

import android.content.Context
import java.io.File

/**
 * Recuperação de gerações interrompidas (processo encerrado pelo sistema, bateria, falha): um marcador persistente
 * indica geração em curso; ao abrir o app sem serviço ativo, os parciais ".part" são removidos e o estado passa a
 * "interrompido", permitindo gerar de novo. Arquivos MP3 finais já concluídos nunca são tocados.
 */
object ExportRecovery {
    private const val PREFS = "export_recovery"

    fun exportsDir(context: Context) = File(context.filesDir, "exports")

    fun markStarted(context: Context, cdId: Long, cdName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("cdId", cdId).putString("cdName", cdName).commit() // commit: precisa sobreviver a uma morte imediata
    }

    fun markFinished(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Chamar uma vez na criação do aplicativo. */
    fun recoverOnLaunch(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cdId = prefs.getLong("cdId", -1L)
        exportsDir(context).listFiles { f -> f.name.endsWith(".mp3.part") || f.name.endsWith(".mp3.tagged") }?.forEach { it.delete() }
        if (cdId >= 0) {
            ExportState.set(
                ExportStatus.Failed(cdId, prefs.getString("cdName", "CD") ?: "CD", "A geração anterior foi interrompida. Toque em \"Gerar MP3\" para tentar de novo.")
            )
            prefs.edit().clear().commit()
        }
    }
}
