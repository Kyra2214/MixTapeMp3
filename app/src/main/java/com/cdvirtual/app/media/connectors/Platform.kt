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
}
