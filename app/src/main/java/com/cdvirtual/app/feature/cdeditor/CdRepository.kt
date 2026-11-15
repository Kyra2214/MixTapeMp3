package com.cdvirtual.app.feature.cdeditor

import com.cdvirtual.app.core.database.CdDao
import com.cdvirtual.app.core.database.CdEntity
import com.cdvirtual.app.core.database.CdTrackEntity
import com.cdvirtual.app.core.database.TrackEntity
import com.cdvirtual.app.feature.library.CoverStore
import com.cdvirtual.app.feature.library.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CdRepository(
    private val dao: CdDao,
    private val tracks: TrackRepository,
    private val covers: CoverStore
) {
    val cds: Flow<List<CdEntity>> = dao.observeAll()

    /** Cria um CD vazio com nome padrão sequencial ("Novo CD N") e devolve seu id. */
    suspend fun create(): Long = dao.insert(CdEntity(name = "Novo CD ${dao.count() + 1}"))

    suspend fun find(id: Long): CdEntity? = dao.getById(id)

    val library: Flow<List<TrackEntity>> = tracks.tracks

    fun tracksOf(cdId: Long): Flow<List<TrackEntity>> = dao.observeTracks(cdId)

    /** Acrescenta ao fim do CD as músicas escolhidas (sem repetir as já incluídas); devolve quantas entraram. */
    suspend fun addTracks(cdId: Long, trackIds: List<Long>): Int {
        val cd = dao.getById(cdId) ?: return 0
        val already = dao.observeTracks(cdId).first().map { it.id }.toSet()
        val fresh = trackIds.distinct().filter { it !in already }
        if (fresh.isEmpty()) return 0
        val start = dao.maxPosition(cdId) + 1
        dao.insertTracks(fresh.mapIndexed { i, id -> CdTrackEntity(cdId, id, start + i) })
        dao.update(cd.copy(updatedAt = System.currentTimeMillis()))
        return fresh.size
    }

    /** Move a música uma posição para cima (delta -1) ou para baixo (+1); no limite não faz nada. */
    suspend fun moveTrack(cdId: Long, trackId: Long, delta: Int) {
        val list = dao.entries(cdId).toMutableList()
        val from = list.indexOfFirst { it.trackId == trackId }
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        val moved = list.removeAt(from)
        list.add(to, moved)
        dao.updateEntries(list.mapIndexed { i, e -> e.copy(position = i) })
        dao.getById(cdId)?.let { dao.update(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    /** Remove a música apenas do CD (a biblioteca e o arquivo original não são afetados) e fecha a lacuna nas posições. */
    suspend fun removeTrack(cdId: Long, trackId: Long) {
        dao.deleteEntry(cdId, trackId)
        dao.updateEntries(dao.entries(cdId).mapIndexed { i, e -> e.copy(position = i) })
        dao.getById(cdId)?.let { dao.update(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    suspend fun touch(id: Long) {
        dao.getById(id)?.let { dao.update(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    /** Renomeia o CD. Nomes vazios são recusados (retorna false); espaços nas pontas são removidos. */
    suspend fun rename(id: Long, name: String): Boolean {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return false
        val cd = dao.getById(id) ?: return false
        dao.update(cd.copy(name = clean, updatedAt = System.currentTimeMillis()))
        return true
    }

    /** Define o artista do CD (gravado em TPE1/TPE2). Vazio remove; espaços nas pontas são removidos. */
    suspend fun setArtist(id: Long, artist: String) {
        val cd = dao.getById(id) ?: return
        val clean = artist.trim().take(MAX_NAME).ifEmpty { null }
        dao.update(cd.copy(artist = clean, updatedAt = System.currentTimeMillis()))
    }

    /** Define (bytes) ou remove (null) a capa; a capa antiga só é apagada se nenhuma música/CD a usa mais. */
    suspend fun setCover(id: Long, imageBytes: ByteArray?) {
        val cd = dao.getById(id) ?: return
        val newPath = imageBytes?.let { covers.save(it) }
        dao.update(cd.copy(coverPath = newPath, updatedAt = System.currentTimeMillis()))
        val old = cd.coverPath
        if (old != null && old != newPath && dao.countByCover(old) == 0 && tracks.coverUses(old) == 0) covers.delete(old)
    }

    /** T-205: exclui só o projeto de CD (faixas da biblioteca, originais e MP3 já gerados ficam). Apaga a capa se ninguém mais a usa. */
    suspend fun delete(id: Long) {
        val cover = dao.getById(id)?.coverPath
        dao.delete(id)
        if (cover != null && dao.countByCover(cover) == 0 && tracks.coverUses(cover) == 0) covers.delete(cover)
    }

    companion object { const val MAX_NAME = 80 }
}
