package com.cdvirtual.app.core.storage

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Acesso a arquivos via Storage Access Framework. Não usa permissões amplas de armazenamento.
 * Os URIs escolhidos pelo usuário têm permissão persistida para uso offline posterior.
 */
class StorageAccess(private val context: Context) {

    private val prefs = context.getSharedPreferences("storage", Context.MODE_PRIVATE)

    fun persistRead(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun persistReadWrite(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    /** Libera a permissão persistida de leitura de [uri] (T-201); silencioso se não houver. */
    fun release(uri: Uri) {
        try { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) { }
    }

    fun persistedCount(): Int = context.contentResolver.persistedUriPermissions.size

    /** Verdadeiro quando só restam poucas vagas de permissão persistida (inclui a pasta de destino). */
    fun isNearPersistLimit(): Boolean = persistedCount() >= persistLimit() - 2

    var outputFolder: Uri?
        get() = prefs.getString(KEY_OUTPUT, null)?.let(Uri::parse)
        set(value) { prefs.edit().putString(KEY_OUTPUT, value?.toString()).apply() }

    companion object {
        private const val KEY_OUTPUT = "output_folder"

        /** ASSUMINDO (limites históricos do Android, a confirmar na documentação): 128 até a API 29 e 512 a partir da API 30. */
        fun persistLimit(): Int = if (android.os.Build.VERSION.SDK_INT >= 30) 512 else 128
        val AUDIO_MIME_TYPES = arrayOf("audio/*", "application/ogg")
    }
}
