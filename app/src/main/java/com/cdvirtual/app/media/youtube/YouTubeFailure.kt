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

fun Throwable.toYouTubePlaybackMessage(): String = when (this) {
    is YouTubeUnavailableException -> when (failure) {
        YouTubeFailure.Private -> "Vídeo privado: o autor não liberou o acesso."
        YouTubeFailure.RegionBlocked -> "Vídeo indisponível na sua região."
        YouTubeFailure.LiveNotSupported -> "Transmissões ao vivo ainda não são suportadas."
        YouTubeFailure.AgeRestricted -> "Vídeo com restrição de idade: exige login."
        YouTubeFailure.NoAudio -> "Este vídeo não tem faixa de áudio disponível."
        YouTubeFailure.PlayerChanged -> "O YouTube mudou algo e a extração falhou. Tente novamente após atualizar o app."
    }
    is java.net.UnknownHostException -> "Sem conexão com a internet ou servidor não encontrado."
    is java.net.ConnectException -> "Não foi possível conectar ao servidor. Verifique sua conexão."
    is java.net.SocketTimeoutException -> "A conexão demorou demais. Tente novamente."
    is java.net.SocketException -> "A conexão foi interrompida. Verifique sua internet."
    is javax.net.ssl.SSLException -> "Falha na conexão segura (HTTPS)."
    else -> message ?: "Não foi possível iniciar a reprodução deste vídeo."
}
