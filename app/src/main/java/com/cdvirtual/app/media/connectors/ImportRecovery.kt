package com.cdvirtual.app.media.connectors

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Recuperação de downloads interrompidos (processo encerrado, app fechado, sem rede): os parciais ".part" em
 * filesDir/imports nunca são músicas válidas e são removidos ao abrir o app. Arquivos já importados não são tocados.
 */
object ImportRecovery {
    fun importsDir(context: Context) = File(context.filesDir, "imports")

    fun cleanPartials(context: Context) {
        importsDir(context).listFiles { f -> f.isFile && f.name.endsWith(".part") }?.forEach { it.delete() }
    }

    /** T-202: true se [uriString] é um arquivo baixado e guardado pelo app em filesDir/imports. */
    fun isManaged(context: Context, uriString: String): Boolean {
        if (!uriString.startsWith("file:")) return false
        val path = Uri.parse(uriString).path ?: return false
        return File(path).parentFile?.canonicalFile == importsDir(context).canonicalFile
    }

    /** T-202: apaga o arquivo baixado de uma faixa removida da biblioteca; nunca toca em arquivos fora de imports/. */
    fun deleteIfManaged(context: Context, uriString: String) {
        if (isManaged(context, uriString)) File(Uri.parse(uriString).path!!).delete()
    }

    /** T-202: ao abrir o app, remove downloads concluídos sem faixa na biblioteca. Chamar fora da thread principal. */
    suspend fun cleanOrphans(context: Context, isKnown: suspend (uri: String) -> Boolean) {
        importsDir(context).listFiles { f -> f.isFile && !f.name.endsWith(".part") }?.forEach { f ->
            if (!isKnown(Uri.fromFile(f).toString())) f.delete()
        }
    }
}
