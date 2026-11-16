package com.cdvirtual.app.media.connectors

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertTrue

class YouTubeApiSmokeTest {

    @Test
    fun extractAudioStreamsFromYoutube() = runBlocking {
        val videoId = "aqz-KE-bpKQ"

        println("\n========== TESTE NEWPIPE ==========")
        println("Vídeo: https://www.youtube.com/watch?v=$videoId")

        val info = YouTubeApi.analyze(videoId)

        println("Título: ${info.fileName}")
        println("Tipo: ${info.kind}")
        println("Quantidade de faixas: ${info.options.size}")

        info.options.forEachIndexed { index, option ->
            println(
                "${index + 1}. ${option.label} | " +
                "Formato: ${option.format} | " +
                "Bitrate: ${option.bitrateKbps} kbps"
            )
        }

        assertTrue("Nenhuma faixa de áudio foi encontrada", info.options.isNotEmpty())

        println("========== EXTRAÇÃO APROVADA ==========\n")
    }
}
