package com.cdvirtual.app.media.youtube

import java.io.IOException

/** Motivos específicos de indisponibilidade de vídeos do YouTube. */
enum class YouTubeFailure {
    Private,
    RegionBlocked,
    LiveNotSupported,
    AgeRestricted,
    NoAudio,
    PlayerChanged
}

class YouTubeUnavailableException(
    val failure: YouTubeFailure
) : IOException("YouTube: $failure")
