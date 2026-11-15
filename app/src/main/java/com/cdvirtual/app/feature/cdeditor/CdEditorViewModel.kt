package com.cdvirtual.app.feature.cdeditor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cdvirtual.app.CDVirtualApp
import com.cdvirtual.app.core.database.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CdEditorViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as CDVirtualApp).cdRepository

    val cds = repo.cds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val prefs = app.getSharedPreferences("cdeditor", android.content.Context.MODE_PRIVATE)

    /** Reabre o último CD editado (-1 = nenhum). */
    private val _selectedId = MutableStateFlow<Long?>(prefs.getLong("selected", -1L).takeIf { it >= 0 })
    val selectedId: StateFlow<Long?> = _selectedId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val cdTracks: StateFlow<List<TrackEntity>> = _selectedId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.tracksOf(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val library = repo.library.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTracks(cdId: Long, ids: List<Long>) {
        viewModelScope.launch {
            val n = repo.addTracks(cdId, ids)
            _message.value = if (n == 0) "Nenhuma música nova adicionada" else "$n música(s) adicionada(s)"
        }
    }

    fun moveTrack(cdId: Long, trackId: Long, delta: Int) {
        viewModelScope.launch { repo.moveTrack(cdId, trackId, delta) }
    }

    fun removeTrack(cdId: Long, trackId: Long) {
        viewModelScope.launch { repo.removeTrack(cdId, trackId); _message.value = "Música removida do CD" }
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun createCd() {
        viewModelScope.launch { select(repo.create()) }
    }

    fun deleteCd(id: Long) {
        viewModelScope.launch {
            repo.delete(id)
            if (_selectedId.value == id) { _selectedId.value = null; prefs.edit().putLong("selected", -1L).apply() }
            _message.value = "CD excluído (músicas e arquivos originais não foram afetados)"
        }
    }

    fun select(id: Long) {
        _selectedId.value = id
        prefs.edit().putLong("selected", id).apply()
    }

    /** As alterações já são gravadas a cada edição; este botão confirma e atualiza a data do projeto. */
    fun saveProject(id: Long) {
        viewModelScope.launch { repo.touch(id); _message.value = "Projeto salvo" }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch {
            _message.value = if (repo.rename(id, name)) "Nome salvo" else "Informe um nome para o CD"
        }
    }

    fun setArtist(id: Long, artist: String) {
        viewModelScope.launch { repo.setArtist(id, artist); _message.value = "Artista salvo" }
    }

    fun setCover(id: Long, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = try { CoverPrepare.fromUri(getApplication(), uri) } catch (_: Throwable) { null }
            if (bytes == null) _message.value = "Imagem inválida ou grande demais"
            else { repo.setCover(id, bytes); _message.value = "Capa definida" }
        }
    }

    fun removeCover(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repo.setCover(id, null); _message.value = "Capa removida" }
    }
}
