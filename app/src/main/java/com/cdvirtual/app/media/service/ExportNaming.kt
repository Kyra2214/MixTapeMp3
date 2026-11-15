package com.cdvirtual.app.media.service

import android.content.Context
import java.io.File

/**
 * T-401: nome do MP3 gerado sem sobrescrever o de outro CD. "<nome>.mp3" é usado se estiver livre ou se já pertence ao
 * mesmo CD (regerar substitui o próprio arquivo); senão "<nome> (2).mp3", "(3)"… A posse (arquivo → id do CD) fica em
 * SharedPreferences (sem alterar o schema do Room). Arquivo existente sem dono conhecido é tratado como de outro CD.
 */
object ExportNaming {
    private const val PREFS = "export_owners"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun targetFor(context: Context, cdId: Long, cdName: String): File {
        val dir = ExportRecovery.exportsDir(context)
        val base = ExportService.safeFileName(cdName)
        val p = prefs(context)
        var i = 1
        while (true) {
            val name = if (i == 1) "$base.mp3" else "$base ($i).mp3"
            val file = File(dir, name)
            val owner = if (p.contains(name)) p.getLong(name, -1L) else -1L
            if (!file.exists() || owner == cdId) return file
            i++
        }
    }

    fun claim(context: Context, file: File, cdId: Long) {
        prefs(context).edit().putLong(file.name, cdId).apply()
    }

    fun forget(context: Context, fileName: String) {
        prefs(context).edit().remove(fileName).apply()
    }

    fun rename(context: Context, oldName: String, newName: String) {
        val p = prefs(context)
        if (!p.contains(oldName)) return
        p.edit().putLong(newName, p.getLong(oldName, -1L)).remove(oldName).apply()
    }
}
