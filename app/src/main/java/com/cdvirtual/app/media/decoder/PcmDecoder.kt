package com.cdvirtual.app.media.decoder

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

class PcmFormat(val sampleRate: Int, val channels: Int)

/** Decodifica qualquer áudio suportado pelo Android para PCM float intercalado (bloqueante; chamar fora da thread principal). */
object PcmDecoder {
    /**
     * @param startMs posição inicial; @param maxMs duração máxima decodificada.
     * @param onFormat chamado antes do primeiro bloco; @param sink retorna false para interromper.
     */
    fun decode(
        context: Context,
        uri: Uri,
        startMs: Long = 0,
        maxMs: Long = Long.MAX_VALUE,
        onFormat: (PcmFormat) -> Unit = {},
        sink: (samples: FloatArray, count: Int) -> Boolean
    ) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalArgumentException("Arquivo sem faixa de áudio")
            extractor.selectTrack(index)
            val inputFormat = extractor.getTrackFormat(index)
            if (startMs > 0) extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            codec = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var isFloat = false
            var reported = false
            var maxFrames = Long.MAX_VALUE
            var emitted = 0L
            var inputDone = false
            var outputDone = false
            var scratch = FloatArray(0)

            fun refreshFormat(f: MediaFormat) {
                sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                    f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                if (maxMs != Long.MAX_VALUE) maxFrames = maxMs * sampleRate / 1000
                onFormat(PcmFormat(sampleRate, channels))
                reported = true
            }

            while (!outputDone) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    refreshFormat(codec.outputFormat)
                } else if (o >= 0) {
                    var stop = false
                    if (info.size > 0) {
                        if (!reported) refreshFormat(codec.outputFormat)
                        val out = codec.getOutputBuffer(o)!!
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val data = out.slice().order(ByteOrder.nativeOrder())
                        var count: Int
                        if (isFloat) {
                            val fb = data.asFloatBuffer()
                            count = fb.remaining()
                            if (scratch.size < count) scratch = FloatArray(count)
                            fb.get(scratch, 0, count)
                        } else {
                            val sb = data.asShortBuffer()
                            count = sb.remaining()
                            if (scratch.size < count) scratch = FloatArray(count)
                            for (k in 0 until count) scratch[k] = sb.get(k) / 32768f
                        }
                        val frames = count / channels
                        val remaining = maxFrames - emitted
                        if (frames > remaining) {
                            count = (remaining * channels).toInt()
                            stop = true
                        }
                        emitted += count / channels
                        if (count > 0 && !sink(scratch, count)) stop = true
                        if (emitted >= maxFrames) stop = true
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (stop || (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                }
            }
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            codec?.release()
            extractor.release()
        }
    }
}
