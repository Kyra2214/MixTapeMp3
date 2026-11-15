package com.cdvirtual.app.media.engine

import android.content.Context
import com.cdvirtual.app.core.database.TrackEntity

/** Decodifica uma faixa já no formato padrão do CD (44,1 kHz, estéreo, float). Base das etapas 6.3 a 6.5. */
object StandardPcm {
    /** @return total de quadros estéreo entregues ao [sink]. */
    fun decode(
        context: Context,
        track: TrackEntity,
        cancelCheck: () -> Unit = {},
        onProgress: (Float) -> Unit = {},
        sink: (samples: FloatArray, count: Int) -> Unit
    ): Long {
        var standardizer: AudioStandardizer? = null
        EngineDecoder.decode(
            context, track, cancelCheck,
            onFormat = { standardizer = AudioStandardizer(it.sampleRate, it.channels) },
            onProgress = onProgress
        ) { buf, n -> standardizer!!.process(buf, n, sink) }
        val s = standardizer!!
        s.finish(sink)
        return s.framesOut
    }
}
