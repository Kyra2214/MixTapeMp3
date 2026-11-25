package com.cdvirtual.app.feature.browser

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Extrai o ID de vídeos do YouTube sem aceitar URLs de outros domínios.
 * Mantém a decisão de encaminhar um link ao player nativo testável em JVM.
 */
internal object BrowserYouTubeUrl {
    private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

    fun extractVideoId(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val candidate = when {
            host == "youtu.be" -> uri.path
                ?.trim('/')
                ?.substringBefore('/')
            host == "youtube.com" || host.endsWith(".youtube.com") ->
                uri.rawQuery
                    ?.split('&')
                    ?.firstNotNullOfOrNull { pair ->
                        val key = pair.substringBefore('=')
                        if (key != "v") null
                        else URLDecoder.decode(pair.substringAfter('=', ""), StandardCharsets.UTF_8.name())
                    }
            else -> null
        }
        return candidate?.takeIf(videoIdPattern::matches)
    }
}
