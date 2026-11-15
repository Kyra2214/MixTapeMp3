package com.cdvirtual.app.core.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Resultado de uma cópia para a pasta de destino. */
data class ExportedCopy(val documentUri: Uri, val displayName: String, val sizeBytes: Long)

/**
 * Copia arquivos gerados pelo app (filesDir/exports) para a pasta escolhida pelo usuário via SAF (9.2).
 * Nunca sobrescreve: se o nome já existe, usa "nome (1).ext". Em falha ou cancelamento o arquivo parcial é apagado
 * e o original interno permanece intacto.
 */
object FolderExporter {
    private const val BUFFER = 256 * 1024
    private const val FAT32_LIMIT = 4L * 1024 * 1024 * 1024 - 1

    suspend fun copy(
        context: Context,
        source: File,
        treeUri: Uri,
        mime: String,
        onProgress: (Float) -> Unit = {}
    ): ExportedCopy = withContext(Dispatchers.IO) {
        if (!source.isFile || source.length() == 0L) throw IOException("Arquivo de origem não encontrado: ${source.name}")
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val name = uniqueName(context, treeUri, source.name)
        val target = try { DocumentsContract.createDocument(resolver, parent, mime, name) } catch (e: Throwable) { throw IOException("Sem acesso à pasta de destino: ${e.message}") }
            ?: throw IOException("Não foi possível criar o arquivo na pasta de destino")
        var ok = false
        try {
            val total = source.length()
            var done = 0L
            resolver.openOutputStream(target, "w")?.use { out ->
                source.inputStream().use { input ->
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(done.toFloat() / total)
                    }
                }
                out.flush()
            } ?: throw IOException("Não foi possível gravar na pasta de destino")
            val written = sizeOf(context, target)
            if (written != null && written != total) throw IOException("Cópia incompleta ($written de $total bytes)")
            onProgress(1f)
            ok = true
            ExportedCopy(target, displayNameOf(context, target) ?: name, total)
        } catch (e: IOException) {
            // 10.3: cartões SD/pendrives em FAT32 recusam arquivos de 4 GiB ou mais; a falha do provedor costuma ser genérica.
            if (source.length() > FAT32_LIMIT) {
                throw IOException("${e.message} — o arquivo tem ${source.length() / (1L shl 30)} GiB ou mais e a pasta de destino pode ser FAT32 (limite de 4 GiB por arquivo). Escolha uma pasta em armazenamento interno ou exFAT.", e)
            }
            throw e
        } finally {
            if (!ok) try { DocumentsContract.deleteDocument(resolver, target) } catch (_: Throwable) { }
        }
    }

    /** Exclui um documento criado por esta classe na pasta de destino; true se não existe mais. */
    suspend fun delete(context: Context, document: Uri): Boolean = withContext(Dispatchers.IO) {
        try { DocumentsContract.deleteDocument(context.contentResolver, document) } catch (_: Throwable) { false }
    }

    /** Grava [bytes] como novo arquivo [name] (nome único, nunca sobrescreve) na pasta de destino. */
    suspend fun writeBytes(context: Context, treeUri: Uri, name: String, mime: String, bytes: ByteArray): ExportedCopy =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
            val finalName = uniqueName(context, treeUri, name)
            val target = try { DocumentsContract.createDocument(resolver, parent, mime, finalName) } catch (e: Throwable) { throw IOException("Sem acesso à pasta de destino: ${e.message}") }
                ?: throw IOException("Não foi possível criar o arquivo na pasta de destino")
            var ok = false
            try {
                resolver.openOutputStream(target, "w")?.use { it.write(bytes); it.flush() }
                    ?: throw IOException("Não foi possível gravar na pasta de destino")
                ok = true
                ExportedCopy(target, displayNameOf(context, target) ?: finalName, bytes.size.toLong())
            } finally {
                if (!ok) try { DocumentsContract.deleteDocument(resolver, target) } catch (_: Throwable) { }
            }
        }

    /** Nome livre na pasta: [wanted] ou "base (n).ext". */
    internal fun uniqueName(context: Context, treeUri: Uri, wanted: String): String {
        val existing = childNames(context, treeUri)
        if (wanted !in existing) return wanted
        val dot = wanted.lastIndexOf('.')
        val base = if (dot > 0) wanted.substring(0, dot) else wanted
        val ext = if (dot > 0) wanted.substring(dot) else ""
        var i = 1
        while ("$base ($i)$ext" in existing) i++
        return "$base ($i)$ext"
    }

    private fun childNames(context: Context, treeUri: Uri): Set<String> = try {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        } ?: emptySet()
    } catch (_: Throwable) { emptySet() }

    private fun sizeOf(context: Context, doc: Uri): Long? = try {
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    } catch (_: Throwable) { null }

    private fun displayNameOf(context: Context, doc: Uri): String? = try {
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Throwable) { null }
}
