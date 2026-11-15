package com.cdvirtual.app.core.storage

import android.content.Context
import android.net.Uri
import com.cdvirtual.app.core.database.TrackEntity

enum class OriginalStatus { INTACT, MODIFIED, MISSING, UNVERIFIED }

data class OriginalsReport(val intact: Int, val modified: Int, val missing: Int, val unverified: Int) {
    val allSafe get() = modified == 0 && missing == 0
}

/**
 * Política de preservação dos arquivos originais: todo acesso é somente leitura (nenhum fluxo do app grava,
 * renomeia ou apaga o arquivo de origem; ganho e EQ vivem só no banco e são aplicados em memória/na exportação)
 * e a integridade pode ser conferida pelo SHA-256 registrado na importação.
 */
object OriginalsGuard {
    /** Única porta de leitura de originais fora dos decoders: abre sempre em modo "r". */
    fun openRead(context: Context, uri: Uri) =
        context.contentResolver.openAssetFileDescriptor(uri, "r")

    /** Bloqueante; chamar fora da thread principal. */
    fun check(context: Context, track: TrackEntity): OriginalStatus {
        val uri = Uri.parse(track.uri)
        val expected = track.contentHash ?: return OriginalStatus.UNVERIFIED
        return try {
            if (sha256Of(context, uri) == expected) OriginalStatus.INTACT else OriginalStatus.MODIFIED
        } catch (_: java.io.FileNotFoundException) {
            OriginalStatus.MISSING
        } catch (_: SecurityException) {
            OriginalStatus.MISSING
        } catch (_: IllegalStateException) {
            OriginalStatus.MISSING
        } catch (_: java.io.IOException) {
            OriginalStatus.MISSING
        }
    }

    fun checkAll(context: Context, tracks: List<TrackEntity>, cancelCheck: () -> Unit = {}): OriginalsReport {
        val counts = IntArray(OriginalStatus.values().size)
        for (t in tracks) { cancelCheck(); counts[check(context, t).ordinal]++ }
        return OriginalsReport(
            counts[OriginalStatus.INTACT.ordinal], counts[OriginalStatus.MODIFIED.ordinal],
            counts[OriginalStatus.MISSING.ordinal], counts[OriginalStatus.UNVERIFIED.ordinal]
        )
    }
}
