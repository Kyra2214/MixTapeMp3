package com.cdvirtual.app.core.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** T-601: ponto único de decodificação reduzida de capas (antes duplicada em 4 lugares). Sem mudança de comportamento. */
object CoverDecoder {
    /**
     * Decodifica [bytes] reduzindo por potências de 2 até que algum lado fique abaixo de [maxSide]
     * (usado por player, notificação e preparo da capa do CD). Null se vazio/inválido.
     */
    fun fromBytes(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Decodifica um arquivo de imagem reduzindo enquanto ambos os lados continuarem >= [target] (miniaturas da lista). */
    fun fromFile(path: String, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
