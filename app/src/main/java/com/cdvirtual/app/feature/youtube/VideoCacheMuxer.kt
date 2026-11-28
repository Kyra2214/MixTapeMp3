package com.cdvirtual.app.feature.youtube

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException

/**
 * Junta faixas DASH progressivas já baixadas, sem recodificar nem alterar a qualidade.
 * Só publica MP4 com codecs que o MediaMuxer do Android consegue gravar.
 */
internal object VideoCacheMuxer {
    private const val MIN_BUFFER_BYTES = 4 * 1024 * 1024
    private const val MAX_BUFFER_BYTES = 32 * 1024 * 1024

    private data class TrackCursor(
        val extractor: MediaExtractor,
        val sourceTrack: Int,
        val outputTrack: Int,
        val buffer: java.nio.ByteBuffer,
        val info: MediaCodec.BufferInfo,
        var sampleSize: Int = -1,
        var sampleTimeUs: Long = -1L
    )

    fun mux(videoFile: File, audioFile: File, outputFile: File) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)

            val videoIndex = findTrack(videoExtractor, "video/")
                ?: throw IOException("A faixa baixada não contém vídeo.")
            val audioIndex = findTrack(audioExtractor, "audio/")
                ?: throw IOException("A faixa baixada não contém áudio.")
            val videoFormat = videoExtractor.getTrackFormat(videoIndex)
            val audioFormat = audioExtractor.getTrackFormat(audioIndex)
            val videoMime = videoFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            val audioMime = audioFormat.getString(MediaFormat.KEY_MIME).orEmpty()

            if (videoMime !in SUPPORTED_VIDEO_MIME) {
                throw IOException("Codec de vídeo não compatível com a junção MP4: $videoMime.")
            }
            if (audioMime !in SUPPORTED_AUDIO_MIME) {
                throw IOException("Codec de áudio não compatível com a junção MP4: $audioMime.")
            }

            videoExtractor.selectTrack(videoIndex)
            audioExtractor.selectTrack(audioIndex)

            outputFile.parentFile?.let { if (!it.exists() && !it.mkdirs()) {
                throw IOException("Não foi possível criar o diretório temporário do vídeo.")
            } }
            // O mux pode ser repetido a partir das duas faixas preservadas.
            if (outputFile.exists() && !outputFile.delete()) {
                throw IOException("Não foi possível recriar o arquivo temporário de junção.")
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputVideo = muxer.addTrack(videoFormat)
            val outputAudio = muxer.addTrack(audioFormat)
            muxer.start()
            started = true

            val video = cursor(videoExtractor, videoIndex, outputVideo, videoFormat)
            val audio = cursor(audioExtractor, audioIndex, outputAudio, audioFormat)
            loadNext(video)
            loadNext(audio)

            var writtenVideo = 0L
            var writtenAudio = 0L
            while (video.sampleSize >= 0 || audio.sampleSize >= 0) {
                val next = when {
                    video.sampleSize < 0 -> audio
                    audio.sampleSize < 0 -> video
                    video.sampleTimeUs <= audio.sampleTimeUs -> video
                    else -> audio
                }
                next.info.offset = 0
                next.info.size = next.sampleSize
                next.info.presentationTimeUs = next.sampleTimeUs
                next.info.flags = next.extractor.sampleFlags
                muxer.writeSampleData(next.outputTrack, next.buffer, next.info)
                if (next === video) writtenVideo++ else writtenAudio++
                next.extractor.advance()
                loadNext(next)
            }

            if (writtenVideo == 0L || writtenAudio == 0L) {
                throw IOException("Não foi possível combinar as faixas completas de áudio e vídeo.")
            }
            muxer.stop()
            started = false
            if (!outputFile.isFile || outputFile.length() <= 0L) {
                throw IOException("O arquivo MP4 combinado ficou vazio.")
            }
        } catch (failure: IOException) {
            throw failure
        } catch (failure: Exception) {
            throw IOException("Falha ao combinar áudio e vídeo: ${failure.message ?: failure.javaClass.simpleName}", failure)
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
        }
    }

    private fun findTrack(extractor: MediaExtractor, prefix: String): Int? =
        (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true
        }

    private fun cursor(
        extractor: MediaExtractor,
        sourceTrack: Int,
        outputTrack: Int,
        format: MediaFormat
    ): TrackCursor {
        val requested = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            runCatching { format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }.getOrDefault(MIN_BUFFER_BYTES)
        } else MIN_BUFFER_BYTES
        val capacity = requested.coerceIn(MIN_BUFFER_BYTES, MAX_BUFFER_BYTES)
        return TrackCursor(
            extractor = extractor,
            sourceTrack = sourceTrack,
            outputTrack = outputTrack,
            buffer = java.nio.ByteBuffer.allocateDirect(capacity),
            info = MediaCodec.BufferInfo()
        )
    }

    private fun loadNext(cursor: TrackCursor) {
        cursor.buffer.clear()
        val size = cursor.extractor.readSampleData(cursor.buffer, 0)
        if (size < 0) {
            cursor.sampleSize = -1
            cursor.sampleTimeUs = -1L
            return
        }
        if (size > cursor.buffer.capacity()) {
            throw IOException("Um quadro excedeu o buffer de junção disponível.")
        }
        cursor.sampleSize = size
        cursor.sampleTimeUs = cursor.extractor.sampleTime
        if (cursor.sampleTimeUs < 0L) {
            throw IOException("A faixa contém um quadro sem timestamp válido.")
        }
    }

    private val SUPPORTED_VIDEO_MIME = setOf("video/avc", "video/hevc", "video/mp4v-es")
    private val SUPPORTED_AUDIO_MIME = setOf("audio/mp4a-latm")
}
