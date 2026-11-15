package com.cdvirtual.app.media.encoder

/** Codificador de PCM estéreo 16 bits para MP3. Isola a biblioteca nativa do resto do motor de áudio. */
interface Mp3Encoder : AutoCloseable {
    /** Codifica [frames] quadros estéreo e devolve os bytes MP3 produzidos (possivelmente vazio). */
    fun encode(left: ShortArray, right: ShortArray, frames: Int): ByteArray

    /** Finaliza o fluxo e devolve os bytes restantes. */
    fun finish(): ByteArray
}
