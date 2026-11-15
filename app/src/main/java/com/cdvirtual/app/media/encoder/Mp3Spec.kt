package com.cdvirtual.app.media.encoder

/** Especificação fixa do MP3 do CD: MPEG-1 Layer III, 44,1 kHz, estéreo, 320 kbps CBR. */
object Mp3Spec {
    const val KBPS = 320
    const val SAMPLE_RATE = 44100
    const val CHANNELS = 2
    const val SAMPLES_PER_FRAME = 1152
}
