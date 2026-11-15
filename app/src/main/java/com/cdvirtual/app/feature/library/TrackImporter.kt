package com.cdvirtual.app.feature.library

import android.content.Context
import android.net.Uri
import com.cdvirtual.app.core.database.TrackEntity
import com.cdvirtual.app.core.storage.StorageAccess
import com.cdvirtual.app.core.storage.sha256Of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ImportOutcome {
    data class Added(val trackId: Long) : ImportOutcome
    data object Duplicate : ImportOutcome
    data class Failed(val reason: String) : ImportOutcome
}

/** Fluxo único de entrada na biblioteca (arquivos locais e baixados de links). */
class TrackImporter(
    private val context: Context,
    private val repo: TrackRepository,
    private val onAdded: () -> Unit = {}
) {
    private val storage = StorageAccess(context)
    private val covers = CoverStore(context)

    /** T-204: SHA-256 e leitura de metadados são bloqueantes; sempre fora da thread principal. */
    suspend fun import(uri: Uri): ImportOutcome = withContext(Dispatchers.IO) {
        var persisted = false
        try {
            if (repo.findByUri(uri.toString()) != null) return@withContext ImportOutcome.Duplicate
            val hash = sha256Of(context, uri)
            if (repo.findByHash(hash) != null) return@withContext ImportOutcome.Duplicate
            val isContent = uri.scheme == "content"
            if (isContent && storage.isNearPersistLimit()) {
                return@withContext ImportOutcome.Failed(
                    "Limite de arquivos com acesso permanente do Android atingido (${storage.persistedCount()} de ${StorageAccess.persistLimit()}). Remova músicas da biblioteca e tente de novo."
                )
            }
            val info = AudioMetadataReader.read(context, uri)
            // T-201: a permissão só é tomada depois que o arquivo provou ser um áudio legível.
            if (isContent) { storage.persistRead(uri); persisted = true }
            val id = repo.add(
                TrackEntity(
                    uri = uri.toString(), title = info.title, artist = info.artist, album = info.album,
                    durationMs = info.durationMs, format = info.format, sizeBytes = info.sizeBytes,
                    coverPath = info.cover?.let { covers.save(it) }, contentHash = hash
                )
            )
            onAdded()
            ImportOutcome.Added(id)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            if (persisted) storage.release(uri)
            throw ce // 10.1: cancelar não é "arquivo inválido"
        } catch (se: SecurityException) {
            if (persisted) storage.release(uri)
            ImportOutcome.Failed("Sem permissão para guardar o acesso a este arquivo (limite de permissões do Android?). Remova músicas da biblioteca e tente de novo.")
        } catch (t: Throwable) {
            if (persisted) storage.release(uri)
            ImportOutcome.Failed(t.message ?: "Arquivo inválido")
        }
    }
}
