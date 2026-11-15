package com.cdvirtual.app.feature.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cdvirtual.app.CDVirtualApp
import com.cdvirtual.app.core.database.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as CDVirtualApp).trackRepository
    private val importer = (app as CDVirtualApp).trackImporter
    private val analysis = (app as CDVirtualApp).analysisRunner
    val analysisFailures = analysis.failures
    fun retryAnalysis(id: Long) = analysis.retry(id)
    private val covers = CoverStore(app)
    private val storage = com.cdvirtual.app.core.storage.StorageAccess(app)

    val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val tracks = query.flatMapLatest { repo.search(it.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val message = MutableStateFlow<String?>(null)

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var added = 0
            var duplicates = 0
            var failed = 0
            for (uri in uris) {
                when (importer.import(uri)) {
                    is ImportOutcome.Added -> added++
                    ImportOutcome.Duplicate -> duplicates++
                    is ImportOutcome.Failed -> failed++
                }
            }
            message.value = "$added importada(s)" +
                (if (duplicates > 0) ", $duplicates duplicada(s) ignorada(s)" else "") +
                (if (failed > 0) ", $failed com erro" else "")
        }
    }

    /** Remove apenas o registro e a capa extraída; o arquivo de áudio original nunca é apagado. */
    fun remove(track: TrackEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.remove(track)
            // T-201: libera a vaga de permissão persistida (só para content://; arquivos do app não têm).
            com.cdvirtual.app.media.connectors.ImportRecovery.deleteIfManaged(getApplication(), track.uri) // T-202
            if (track.uri.startsWith("content://")) storage.release(android.net.Uri.parse(track.uri))
            track.coverPath?.let { if (repo.coverUses(it) == 0) covers.delete(it) }
            message.value = "\"${track.title}\" removida da biblioteca"
        }
    }
}
