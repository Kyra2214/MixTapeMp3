package com.cdvirtual.app.media.encoder

import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder

/**
 * Encoder MP3 baseado em LAME (TAndroidLame). Estéreo, [sampleRate] Hz, [kbps] kbps.
 * ASSUMINDO: a API LameBuilder/AndroidLame (encode, flush, close) conforme a documentação da biblioteca;
 * confirmar no primeiro build com internet (roteiro em docs/BUILD.md, "Verificação do encoder"). Todo o uso da biblioteca
 * fica restrito a este arquivo. O CBR 320 não depende de padrões da biblioteca: o Mp3Validator rejeita qualquer arquivo fora dele.
 */
class LameMp3Encoder(sampleRate: Int = Mp3Spec.SAMPLE_RATE, kbps: Int = Mp3Spec.KBPS) : Mp3Encoder {
    private val lame: AndroidLame = LameBuilder()
        .setInSampleRate(sampleRate)
        .setOutSampleRate(sampleRate)
        .setOutChannels(2)
        .setOutBitrate(kbps)
        .build()
    private var buffer = ByteArray(0)

    override fun encode(left: ShortArray, right: ShortArray, frames: Int): ByteArray {
        val needed = (1.25 * frames).toInt() + 7200 // dimensionamento recomendado pelo LAME
        if (buffer.size < needed) buffer = ByteArray(needed)
        val n = lame.encode(left, right, frames, buffer)
        check(n >= 0) { "Falha na codificação MP3 (código $n)" }
        return buffer.copyOf(n)
    }

    override fun finish(): ByteArray {
        if (buffer.size < 7200) buffer = ByteArray(7200)
        val n = lame.flush(buffer)
        check(n >= 0) { "Falha ao finalizar o MP3 (código $n)" }
        return buffer.copyOf(n)
    }

    override fun close() { lame.close() }
}
