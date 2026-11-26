package com.cdvirtual.app.core.storage

import android.content.Context
import java.io.File

/**
 * Limites físicos dos domínios de mídia.
 *
 * O diretório legado "imports" permanece no mesmo caminho para preservar URIs já
 * registrados no Room. Ele contém somente arquivos de áudio importados.
 * O cache de vídeo tem diretório exclusivo e nunca deve ser percorrido pelo
 * ImportRecovery ou por rotinas de limpeza da biblioteca musical.
 */
object MediaStorageLayout {
    private const val AUDIO_IMPORTS = "imports"
    private const val VIDEO_CACHE = "video_cache"

    fun audioImportsDir(context: Context): File =
        File(context.filesDir, AUDIO_IMPORTS)

    fun videoCacheDir(context: Context): File =
        File(context.filesDir, VIDEO_CACHE)
}
