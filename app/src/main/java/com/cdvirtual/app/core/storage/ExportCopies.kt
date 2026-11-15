package com.cdvirtual.app.core.storage

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * T-402: cópias gravadas na pasta de destino para cada MP3 gerado (nome com que o MP3 foi gravado e URIs de MP3/CUE),
 * guardadas em SharedPreferences para sobreviver a troca de tela e reinício do app. Chave: nome do MP3 em filesDir/exports.
 */
object ExportCopies {
    private const val PREFS = "export_copies"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(context: Context, mp3: String): JSONObject =
        try { JSONObject(prefs(context).getString(mp3, null) ?: "{}") } catch (_: Throwable) { JSONObject() }

    /** Nome com que o MP3 foi gravado na pasta de destino (o CUE precisa apontar para ele), ou null. */
    fun displayName(context: Context, mp3: String): String? = read(context, mp3).optString("n").ifEmpty { null }

    fun docs(context: Context, mp3: String): List<Uri> {
        val arr = read(context, mp3).optJSONArray("d") ?: return emptyList()
        return (0 until arr.length()).map { Uri.parse(arr.getString(it)) }
    }

    /** Registra uma cópia; [displayName] só é informado para a cópia do MP3 (não para o CUE). */
    @Synchronized
    fun addCopy(context: Context, mp3: String, displayName: String?, doc: Uri) {
        val o = read(context, mp3)
        if (displayName != null) o.put("n", displayName)
        val arr = o.optJSONArray("d") ?: JSONArray()
        arr.put(doc.toString())
        o.put("d", arr)
        prefs(context).edit().putString(mp3, o.toString()).apply()
    }

    @Synchronized
    fun clear(context: Context, mp3: String) {
        prefs(context).edit().remove(mp3).apply()
    }

    @Synchronized
    fun rename(context: Context, old: String, new: String) {
        val raw = prefs(context).getString(old, null) ?: return
        prefs(context).edit().putString(new, raw).remove(old).apply()
    }
}
