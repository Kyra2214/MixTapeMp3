package com.cdvirtual.app.media.connectors

import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException

/** Motivos de indisponibilidade de um vídeo do YouTube, traduzidos para o usuário em [toUserMessage]. */
enum class YouTubeFailure { Private, RegionBlocked, LiveNotSupported, AgeRestricted, NoAudio, PlayerChanged }

class YouTubeUnavailableException(val failure: YouTubeFailure) : IOException("YouTube: $failure")

fun Throwable.toUserMessage(): String = when (this) {
    is YouTubeUnavailableException -> when (failure) {
        YouTubeFailure.Private -> "Vídeo privado: o autor não liberou o acesso."
        YouTubeFailure.RegionBlocked -> "Vídeo indisponível na sua região."
        YouTubeFailure.LiveNotSupported -> "Transmissão ao vivo ainda não é suportada. Use a gravação, se existir."
        YouTubeFailure.AgeRestricted -> "Vídeo com restrição de idade: exige login."
        YouTubeFailure.NoAudio -> "Este vídeo não tem faixa de áudio disponível."
        YouTubeFailure.PlayerChanged -> "O YouTube mudou algo e a extração falhou. Atualize o app."
    }
    is HttpStatusException -> when (code) {
        401, 403 -> "Acesso negado: o link exige login ou permissão."
        404, 410 -> "Arquivo não encontrado (o link pode ter expirado)."
        429 -> "Muitas requisições. Tente novamente mais tarde."
        in 500..599 -> "O servidor está com problemas (erro $code). Tente mais tarde."
        else -> "O servidor respondeu com erro $code."
    }
    is UnknownHostException -> "Sem conexão com a internet ou servidor não encontrado."
    is ConnectException -> "Não foi possível conectar ao servidor. Verifique sua conexão."
    is SocketException -> "A conexão foi interrompida. Verifique sua internet e tente de novo."
    is EOFException -> "A conexão foi encerrada antes do fim do arquivo. Tente de novo."
    is SocketTimeoutException -> "Tempo esgotado ao conectar. Verifique sua conexão."
    is SSLException -> "Falha na conexão segura (HTTPS)."
    is UnknownServiceException -> "Links sem criptografia (http) não são permitidos. Use um link https."
    is FileNotFoundException -> "Arquivo não encontrado."
    is UnsupportedOperationException -> message ?: "Operação não permitida para esta fonte."
    else -> message ?: "Ocorreu um erro inesperado."
}
