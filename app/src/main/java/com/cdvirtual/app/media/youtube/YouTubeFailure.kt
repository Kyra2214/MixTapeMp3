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

fun Throwable.toYouTubePlaybackMessage(): String {
    // Media3 normalmente envolve erros de rede em PlaybackException/DataSourceException.
    // Procura a causa real para não mostrar apenas um código genérico ao usuário.
    val rootCause = generateSequence(this) { it.cause }
        .take(8)
        .firstOrNull {
            it is YouTubeUnavailableException ||
                it is java.net.UnknownHostException ||
                it is java.net.ConnectException ||
                it is java.net.SocketTimeoutException ||
                it is java.net.SocketException ||
                it is javax.net.ssl.SSLException
        } ?: this

    return when (rootCause) {
        is YouTubeUnavailableException -> when (rootCause.failure) {
            YouTubeFailure.Private -> "Vídeo privado: o autor não liberou o acesso."
            YouTubeFailure.RegionBlocked -> "Vídeo indisponível na sua região."
            YouTubeFailure.LiveNotSupported -> "Transmissões ao vivo ainda não são suportadas."
            YouTubeFailure.AgeRestricted -> "Vídeo com restrição de idade: exige login."
            YouTubeFailure.NoAudio -> "Este vídeo não tem faixa de áudio disponível."
            YouTubeFailure.PlayerChanged -> "O YouTube mudou algo e a extração falhou. Tente novamente após atualizar o app."
        }
        is java.net.UnknownHostException -> "Sem conexão com a internet ou servidor não encontrado. Quando a conexão voltar, toque em Tentar novamente."
        is java.net.ConnectException -> "Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente."
        is java.net.SocketTimeoutException -> "A conexão demorou demais. Verifique sua internet e tente novamente."
        is java.net.SocketException -> "A conexão foi interrompida. Verifique sua internet e tente novamente."
        is javax.net.ssl.SSLException -> "Falha na conexão segura (HTTPS)."
        else -> message ?: "Não foi possível iniciar a reprodução deste vídeo. Tente novamente."
    }
}
