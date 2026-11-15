package com.cdvirtual.app.media.connectors

import java.io.File
import java.net.URL

/** Links que apontam diretamente para um arquivo de áudio (inclui links diretos de 4shared). */
class DirectLinkConnector : SourceConnector {
    override val id = "direct"
    override fun supports(platform: Platform) = platform == Platform.Direct || platform == Platform.FourShared
    override suspend fun analyze(url: URL) = LinkAnalyzer.analyze(url)
    override suspend fun download(option: MediaOption, target: File, onProgress: (Long, Long) -> Unit) =
        DirectDownloader.download(option, target, onProgress)
}
