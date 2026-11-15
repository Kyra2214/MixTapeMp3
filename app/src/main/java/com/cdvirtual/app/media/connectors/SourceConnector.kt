package com.cdvirtual.app.media.connectors

import java.io.File
import java.net.URL

/** Fonte de áudio compatível. Cada conector só usa meios permitidos pela respectiva fonte. */
interface SourceConnector {
    val id: String
    fun supports(platform: Platform): Boolean
    suspend fun analyze(url: URL): LinkInfo
    suspend fun download(option: MediaOption, target: File, onProgress: (done: Long, total: Long) -> Unit)
}
