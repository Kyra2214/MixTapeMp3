package com.cdvirtual.app.core.storage

import android.content.Context
import android.net.Uri
import java.security.MessageDigest

/** SHA-256 do conteúdo do arquivo, lido em streaming (suporta arquivos grandes). */
fun sha256Of(context: Context, uri: Uri): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Não foi possível abrir o arquivo")
    input.use { stream ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
