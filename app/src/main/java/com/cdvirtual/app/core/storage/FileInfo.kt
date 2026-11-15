package com.cdvirtual.app.core.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

data class FileInfo(val displayName: String, val sizeBytes: Long)

fun readFileInfo(context: Context, uri: Uri): FileInfo {
    var name: String? = null
    var size = 0L
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val s = c.getColumnIndex(OpenableColumns.SIZE)
            if (n >= 0) name = c.getString(n)
            if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
        }
    }
    return FileInfo(name ?: uri.lastPathSegment ?: "Sem nome", size)
}
