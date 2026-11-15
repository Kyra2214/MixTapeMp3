package com.cdvirtual.app.feature.cdeditor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.ByteArrayOutputStream
import com.cdvirtual.app.core.common.CoverDecoder

/** Lê a imagem escolhida (somente leitura), reduz para no máximo 1024 px e devolve JPEG; null se não for imagem válida. */
object CoverPrepare {
    private const val MAX_SIDE = 1024
    private const val MAX_INPUT_BYTES = 20 * 1024 * 1024

    fun fromUri(context: Context, uri: Uri): ByteArray? {
        val raw = context.contentResolver.openInputStream(uri)?.use { s ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_INPUT_BYTES) return null
            }
            out.toByteArray()
        } ?: return null
        val bmp = CoverDecoder.fromBytes(raw, MAX_SIDE) ?: return null
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }
}
