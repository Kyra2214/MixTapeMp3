package com.cdvirtual.app.media.connectors

import java.net.URL

enum class Platform(val label: String) {
    YouTube("YouTube"),
    Facebook("Facebook"),
    Instagram("Instagram"),
    FourShared("4shared"),
    Direct("Link direto")
}

object PlatformDetector {
    private val hosts = mapOf(
        Platform.YouTube to listOf("youtube.com", "youtu.be", "youtube-nocookie.com"),
        Platform.Facebook to listOf("facebook.com", "fb.com", "fb.watch"),
        Platform.Instagram to listOf("instagram.com", "instagr.am"),
        Platform.FourShared to listOf("4shared.com")
    )

    /** Hosts desconhecidos são tratados como possível link direto de arquivo. */
    fun detect(url: URL): Platform {
        val host = url.host.lowercase().removePrefix("www.")
        for ((platform, domains) in hosts) {
            if (domains.any { host == it || host.endsWith(".$it") }) return platform
        }
        return Platform.Direct
    }

    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val PATH_PREFIXES = setOf("shorts", "live", "embed", "v", "e")

    /**
     * Extrai o ID de 11 caracteres de um link do YouTube, ou null se o link não apontar para um vídeo
     * (playlist, canal, home). Cobre watch?v=, youtu.be/, /shorts/, /live/ e /embed/.
     */
    fun videoIdOf(url: URL): String? {
        val host = url.host.lowercase()
            .removePrefix("www.").removePrefix("music.").removePrefix("m.")
        val segments = url.path.split('/').filter { it.isNotEmpty() }
        val candidate = if (host == "youtu.be") {
            segments.firstOrNull()
        } else {
            url.query?.split('&')?.firstOrNull { it.startsWith("v=") }?.substringAfter('=')
                ?: segments.takeIf { it.size >= 2 && it[0] in PATH_PREFIXES }?.get(1)
        }
        return candidate?.substringBefore('&')?.takeIf { VIDEO_ID.matches(it) }
    }
}
