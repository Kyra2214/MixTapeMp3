package com.cdvirtual.app.core.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Estado da pasta de destino das exportações (9.1). */
sealed interface OutputFolderState {
    data object NotSet : OutputFolderState
    /** Pasta escolhida e com permissão de leitura/escrita persistida. */
    data class Ready(val uri: Uri, val name: String) : OutputFolderState
    /** Pasta escolhida antes, mas a permissão foi revogada ou a pasta não existe mais. */
    data class Unavailable(val uri: Uri) : OutputFolderState
}

/** Escolha, validação e descrição da pasta de destino (via SAF; sem permissões amplas de armazenamento). */
class OutputFolder(private val context: Context, private val storage: StorageAccess = StorageAccess(context)) {

    /** Grava a escolha do usuário (URI de árvore do OpenDocumentTree), persistindo leitura e escrita. Devolve o novo estado. */
    fun choose(treeUri: Uri): OutputFolderState {
        try { storage.persistReadWrite(treeUri) } catch (_: Throwable) { return OutputFolderState.Unavailable(treeUri) }
        val previous = storage.outputFolder
        storage.outputFolder = treeUri
        if (previous != null && previous != treeUri) releaseQuietly(previous)
        return state()
    }

    /** Esquece a pasta e libera a permissão persistida. */
    fun clear() {
        storage.outputFolder?.let { releaseQuietly(it) }
        storage.outputFolder = null
    }

    fun state(): OutputFolderState {
        val uri = storage.outputFolder ?: return OutputFolderState.NotSet
        if (!hasWritePermission(uri)) return OutputFolderState.Unavailable(uri)
        val name = displayName(uri) ?: return OutputFolderState.Unavailable(uri)
        return OutputFolderState.Ready(uri, name)
    }

    private fun hasWritePermission(uri: Uri) =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }

    /** Nome legível da pasta; null se ela não puder mais ser consultada. */
    private fun displayName(treeUri: Uri): String? = try {
        val doc = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Throwable) { null }

    private fun releaseQuietly(uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Throwable) { }
    }
}
