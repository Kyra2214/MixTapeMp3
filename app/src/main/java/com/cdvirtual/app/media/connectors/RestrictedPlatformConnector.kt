package com.cdvirtual.app.media.connectors

import java.io.File
import java.net.URL

/**
 * YouTube, Facebook e Instagram não oferecem download de mídia por meio oficial para apps de terceiros,
 * e seus termos proíbem extrair o conteúdo. Este conector reconhece o link, explica a restrição e
 * orienta o usuário, sem contornar proteções (regra técnica 8).
 */
class RestrictedPlatformConnector : SourceConnector {
    override val id = "restricted"
    private val restricted = setOf(Platform.YouTube, Platform.Facebook, Platform.Instagram)

    override fun supports(platform: Platform) = platform in restricted

    override suspend fun analyze(url: URL): LinkInfo {
        val platform = PlatformDetector.detect(url)
        return LinkInfo(
            finalUrl = url.toString(), contentType = null, sizeBytes = 0, fileName = null,
            kind = LinkKind.Restricted,
            message = "${platform.label} não permite baixar o conteúdo por este meio. " +
                "Se você tem o arquivo de áudio (por exemplo, obtido com autorização do autor ou por download " +
                "oficial), use \"Importar músicas\" na Biblioteca."
        )
    }

    override suspend fun download(option: MediaOption, target: File, onProgress: (Long, Long) -> Unit) {
        throw UnsupportedOperationException("Download não permitido para esta plataforma")
    }
}
