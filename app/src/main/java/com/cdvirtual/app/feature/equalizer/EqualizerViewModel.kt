package com.cdvirtual.app.feature.equalizer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cdvirtual.app.CDVirtualApp
import com.cdvirtual.app.core.database.TrackEntity
import com.cdvirtual.app.media.equalization.AutoEqPlanner
import com.cdvirtual.app.media.preview.PreviewPlayer
import com.cdvirtual.app.media.normalization.NormalizationSettings
import com.cdvirtual.app.media.normalization.NormalizationSettingsStore
import com.cdvirtual.app.core.storage.OriginalsGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EqualizerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as CDVirtualApp).trackRepository
    private val store = NormalizationSettingsStore(app)
    private val cdRepo = (app as CDVirtualApp).cdRepository

    val cds = cdRepo.cds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** T-303: CD ao qual "EQ automática" e "Zerar EQ" se aplicam; null = biblioteca inteira. Começa no último CD aberto no editor. */
    private val _scopeCdId = MutableStateFlow<Long?>(
        app.getSharedPreferences("cdeditor", android.content.Context.MODE_PRIVATE).getLong("selected", -1L).takeIf { it >= 0 }
    )
    val scopeCdId: StateFlow<Long?> = _scopeCdId.asStateFlow()

    /** Alterna entre "biblioteca inteira" e cada CD. */
    fun cycleScope() {
        val ids = listOf<Long?>(null) + cds.value.map { it.id }
        _scopeCdId.value = ids[(ids.indexOf(_scopeCdId.value) + 1) % ids.size]
    }

    /** Faixas do escopo atual; um CD que não existe mais equivale à biblioteca inteira. */
    private suspend fun scopeTracks(): List<TrackEntity> {
        val id = _scopeCdId.value
        return if (id == null || cdRepo.find(id) == null) tracks.value else cdRepo.tracksOf(id).first()
    }

    val tracks = repo.tracks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _settings = MutableStateFlow(store.load())
    val settings: StateFlow<NormalizationSettings> = _settings.asStateFlow()

    private fun updateSettings(change: (NormalizationSettings) -> NormalizationSettings) {
        _settings.update(change)
        store.save(_settings.value)
    }

    fun setUserGain(id: Long, db: Float) {
        viewModelScope.launch { repo.edit(id) { it.copy(userGainDb = db) } }
    }

    fun setEq(id: Long, bass: Float, mid: Float, treble: Float) {
        viewModelScope.launch { repo.edit(id) { it.copy(bassDb = bass, midDb = mid, trebleDb = treble) } }
    }

    /** Aplica a EQ automática às faixas analisadas do escopo (CD escolhido, ou biblioteca inteira). Armazenamento continua por faixa. */
    fun autoEqualize() {
        viewModelScope.launch {
            val plan = AutoEqPlanner.plan(scopeTracks())
            plan.forEach { (id, s) -> repo.edit(id) { it.copy(bassDb = s.bassDb, midDb = s.midDb, trebleDb = s.trebleDb) } }
        }
    }

    fun resetEq() {
        viewModelScope.launch {
            scopeTracks().forEach { t -> repo.edit(t.id) { it.copy(bassDb = 0f, midDb = 0f, trebleDb = 0f) } }
        }
    }

    /** Faixa em pré-visualização e se é a versão processada (true) ou original (false). */
    private val _preview = MutableStateFlow<Pair<Long, Boolean>?>(null)
    val preview: StateFlow<Pair<Long, Boolean>?> = _preview.asStateFlow()
    private val _previewError = MutableStateFlow<String?>(null)
    val previewError: StateFlow<String?> = _previewError.asStateFlow()
    private val player = PreviewPlayer(app)

    fun preview(t: TrackEntity, processed: Boolean) {
        _previewError.value = null
        val key = t.id to processed
        _preview.value = key
        player.play(t, _settings.value, processed) { err ->
            _preview.update { if (it == key) null else it }
            if (err != null) _previewError.value = err
        }
    }

    private val _originals = MutableStateFlow<String?>(null)
    val originals: StateFlow<String?> = _originals.asStateFlow()

    /** Confere (somente leitura) se os arquivos originais continuam idênticos aos importados. */
    fun verifyOriginals() {
        val app = getApplication<Application>()
        _originals.value = "Verificando originais…"
        viewModelScope.launch(Dispatchers.IO) {
            val r = OriginalsGuard.checkAll(app, tracks.value)
            _originals.value = "Originais: ${r.intact} intactos" +
                (if (r.modified > 0) ", ${r.modified} modificados fora do app" else "") +
                (if (r.missing > 0) ", ${r.missing} ausentes" else "") +
                (if (r.unverified > 0) ", ${r.unverified} sem verificação" else "")
        }
    }

    fun stopPreview() = player.stop()

    override fun onCleared() { player.release() }

    fun setEnabled(enabled: Boolean) = updateSettings { it.copy(enabled = enabled) }
    fun setTarget(lufs: Float) = updateSettings { it.copy(targetLufs = lufs.coerceIn(-23f, -8f)) }
}
