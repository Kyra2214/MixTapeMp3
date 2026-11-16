package com.cdvirtual.app.feature.library

import android.content.Context
import android.net.Uri
import com.cdvirtual.app.core.database.TrackEntity

/** Gênero das músicas: normalização do texto da tag e agrupamento da biblioteca. */
object Genres {
    const val NONE = "Sem gênero"

    private val ID3V1 = listOf(
        "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies",
        "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska", "Death Metal", "Pranks",
        "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical", "Instrumental",
        "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise", "Alt. Rock", "Bass", "Soul", "Punk", "Space", "Meditative",
        "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk",
        "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta Rap", "Top 40", "Christian Rap", "Pop/Funk", "Jungle",
        "Native American", "Cabaret", "New Wave", "Psychedelic", "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal", "Acid Punk",
        "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock"
    )
    private val CODE_IN_PARENS = Regex("""^\((\d{1,3})\)(.*)$""")
    private val ONLY_DIGITS = Regex("""^\d{1,3}$""")

    /** Texto da tag -> nome do gênero; null se vazio ou se for só um código desconhecido. */
    fun normalize(raw: String?): String? {
        val s = raw?.replace('\u0000', ' ')?.trim().orEmpty()
        if (s.isEmpty()) return null
        CODE_IN_PARENS.matchEntire(s)?.let { m ->
            val rest = m.groupValues[2].trim()
            return rest.ifEmpty { ID3V1.getOrNull(m.groupValues[1].toInt()) }
        }
        if (ONLY_DIGITS.matches(s)) return ID3V1.getOrNull(s.toInt())
        return s
    }

    /** Nome mostrado para a faixa ("Sem gênero" quando vazio/ainda não lido). */
    fun labelOf(track: TrackEntity): String = track.genre?.trim()?.takeIf { it.isNotEmpty() } ?: NONE

    /** Agrupa por gênero (sem diferenciar maiúsculas), em ordem alfabética, com "Sem gênero" por último. */
    fun group(tracks: List<TrackEntity>): List<Pair<String, List<TrackEntity>>> =
        tracks.groupBy { labelOf(it).lowercase() }
            .map { (_, list) -> labelOf(list.first()) to list }
            .sortedWith(compareBy<Pair<String, List<TrackEntity>>> { it.first == NONE }.thenBy { it.first.lowercase() })
}

/** Preenche o gênero das músicas que já estavam na biblioteca antes deste campo existir. */
object GenreBackfill {
    suspend fun run(context: Context, repo: TrackRepository) {
        for (t in repo.pendingGenre()) {
            val genre = try {
                Genres.normalize(AudioMetadataReader.readGenre(context, Uri.parse(t.uri))).orEmpty()
            } catch (_: Throwable) { "" }
            repo.edit(t.id) { it.copy(genre = genre) }
        }
    }
}
