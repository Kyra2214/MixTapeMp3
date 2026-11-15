package com.cdvirtual.app.feature.library

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Guarda capas extraídas em armazenamento interno do app; arquivos originais não são alterados. */
class CoverStore(context: Context) {
    private val dir = File(context.filesDir, "covers").apply { mkdirs() }

    fun save(bytes: ByteArray): String {
        val name = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
        val file = File(dir, "$name.img")
        if (!file.exists()) file.writeBytes(bytes)
        return file.absolutePath
    }

    fun delete(path: String) {
        File(path).takeIf { it.exists() && it.parentFile == dir }?.delete()
    }
}
