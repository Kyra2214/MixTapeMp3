package com.cdvirtual.app.media.metadata

import android.content.Context
import java.io.File

/** Faixa do CUE: título e início em milissegundos no MP3 único. */
data class CueTrack(val title: String, val startMs: Long)

/** Geração de arquivo CUE (opcional, 7.6) que aponta para o MP3 único e marca o início de cada faixa. */
object CueSheet {
    private const val FRAMES_PER_SECOND = 75 // um "frame" CUE = 1/75 s

    /** Texto do CUE para [mp3Name] (somente o nome do arquivo, sem pasta). Textos recebem aspas simples no lugar de aspas duplas. */
    fun build(mp3Name: String, album: String, artist: String?, tracks: List<CueTrack>): String {
        val sb = StringBuilder()
        artist?.trim()?.takeIf { it.isNotEmpty() }?.let { sb.append("PERFORMER ").append(q(it)).append("\r\n") }
        sb.append("TITLE ").append(q(album)).append("\r\n")
        sb.append("FILE ").append(q(mp3Name)).append(" MP3\r\n")
        tracks.forEachIndexed { i, t ->
            sb.append("  TRACK %02d AUDIO\r\n".format(java.util.Locale.ROOT, i + 1))
            sb.append("    TITLE ").append(q(t.title)).append("\r\n")
            artist?.trim()?.takeIf { it.isNotEmpty() }?.let { sb.append("    PERFORMER ").append(q(it)).append("\r\n") }
            sb.append("    INDEX 01 ").append(time(t.startMs)).append("\r\n")
        }
        return sb.toString()
    }

    /**
     * CUE montado a partir das tags do próprio MP3 (capítulos CHAP, álbum TALB, artista TPE1), apontando para [mp3Name].
     * Null se o MP3 não tiver faixas marcadas.
     */
    fun fromMp3(mp3: File, mp3Name: String = mp3.name): String? {
        val info = Id3Reader.readInfo(mp3)
        val chapters = info.chapters
        if (chapters.isEmpty()) return null
        val album = info.album ?: mp3.nameWithoutExtension
        val artist = info.artist
        return build(mp3Name, album, artist, chapters.mapIndexed { i, c -> CueTrack(c.title.ifBlank { "Faixa ${i + 1}" }, c.startMs) })
    }

    /** UTF-8 com BOM, aceito pela maioria dos players. */
    fun encode(text: String): ByteArray = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)

    /** mm:ss:ff (minutos podem passar de 99; frames 0..74). */
    fun time(ms: Long): String {
        val m = ms.coerceAtLeast(0)
        val minutes = m / 60_000
        val seconds = (m / 1000) % 60
        val frames = (m % 1000) * FRAMES_PER_SECOND / 1000
        return "%02d:%02d:%02d".format(java.util.Locale.ROOT, minutes, seconds, frames) // ASCII sempre: o CUE é lido por outros players
    }

    /** Grava o CUE (UTF-8 com BOM, aceito pela maioria dos players) ao lado do MP3, com o mesmo nome base. Devolve o arquivo. */
    fun writeNextTo(mp3: File, album: String, artist: String?, tracks: List<CueTrack>): File {
        val cue = File(mp3.parentFile, mp3.nameWithoutExtension + ".cue")
        val tmp = File(cue.path + ".part")
        tmp.writeBytes(encode(build(mp3.name, album, artist, tracks)))
        if (cue.exists() && !cue.delete()) { tmp.delete(); throw java.io.IOException("Não foi possível substituir ${cue.name}") }
        if (!tmp.renameTo(cue)) { tmp.delete(); throw java.io.IOException("Não foi possível gravar ${cue.name}") }
        return cue
    }

    private fun q(s: String) = "\"" + s.replace('"', '\'').replace(Regex("[\\r\\n]+"), " ").trim() + "\""
}

/** Preferência "gerar CUE junto com o MP3" (desligada por padrão: o CUE é opcional). */
object CueSettings {
    private const val PREFS = "export"
    private const val KEY = "write_cue"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }
}
