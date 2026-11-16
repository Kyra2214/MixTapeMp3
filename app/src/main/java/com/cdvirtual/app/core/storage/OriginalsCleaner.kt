package com.cdvirtual.app.core.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.cdvirtual.app.core.database.TrackEntity
import com.cdvirtual.app.feature.library.CoverStore
import com.cdvirtual.app.feature.library.TrackRepository
import com.cdvirtual.app.media.connectors.ImportRecovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** [deleted] arquivos originais apagados; [failed] não puderam ser apagados (ficam no aparelho, mas saem da biblioteca). */
data class CleanResult(val deleted: Int, val failed: Int)

/**
 * Opção "apagar os arquivos originais depois de gerar a playlist". Só é chamada depois que o MP3 unificado foi gerado,
 * validado e (se havia pasta de destino) copiado. Apaga o arquivo de origem e remove a música da biblioteca.
 * Em arquivos escolhidos pelo seletor do Android a exclusão depende do provedor permitir; quando não permite,
 * o arquivo é mantido, a música sai da biblioteca e a falha é contada em [CleanResult.failed].
 */
object OriginalsCleaner {
    suspend fun deleteOriginals(context: Context, repo: TrackRepository, tracks: List<TrackEntity>): CleanResult =
        withContext(Dispatchers.IO) {
            val covers = CoverStore(context)
            val storage = StorageAccess(context)
            var deleted = 0
            var failed = 0
            for (t in tracks) {
                if (deleteSource(context, t.uri)) deleted++ else failed++
                try { repo.remove(t) } catch (_: Throwable) { }
                if (t.uri.startsWith("content://")) storage.release(Uri.parse(t.uri))
                t.coverPath?.let { if (repo.coverUses(it) == 0) covers.delete(it) }
            }
            CleanResult(deleted, failed)
        }

    /** true se o arquivo deixou de existir. */
    private fun deleteSource(context: Context, uriString: String): Boolean {
        if (ImportRecovery.isManaged(context, uriString)) {
            ImportRecovery.deleteIfManaged(context, uriString)
            return true
        }
        val uri = Uri.parse(uriString)
        return try {
            when (uri.scheme) {
                "file" -> uri.path?.let { val f = File(it); !f.exists() || f.delete() } ?: false
                "content" -> {
                    val resolver = context.contentResolver
                    val byDocument = try {
                        DocumentsContract.isDocumentUri(context, uri) && DocumentsContract.deleteDocument(resolver, uri)
                    } catch (_: Throwable) { false }
                    byDocument || try { resolver.delete(uri, null, null) > 0 } catch (_: Throwable) { false }
                }
                else -> false
            }
        } catch (_: Throwable) { false }
    }
}
