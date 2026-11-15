package com.cdvirtual.app.media.preview

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import com.cdvirtual.app.core.database.TrackEntity
import com.cdvirtual.app.media.decoder.PcmDecoder
import com.cdvirtual.app.media.equalization.ThreeBandEqualizer
import com.cdvirtual.app.media.normalization.GainPlanner
import com.cdvirtual.app.media.normalization.NormalizationSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * Pré-visualização de um trecho curto (~15 s, a partir do meio da faixa) com ganho e EQ aplicados
 * ou o original, para comparação A/B. Somente leitura: nenhum arquivo é alterado.
 */
class PreviewPlayer(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** [onEnd] é chamado ao terminar, ser interrompido ou falhar (mensagem de erro, se houver). */
    fun play(track: TrackEntity, settings: NormalizationSettings, processed: Boolean, onEnd: (error: String?) -> Unit) {
        val previous = job
        job = scope.launch {
            previous?.cancelAndJoin()
            var error: String? = null
            try {
                run(track, settings, processed)
            } catch (ce: CancellationException) {
                // interrompido pelo usuário
            } catch (e: Throwable) {
                error = e.message ?: "Falha na pré-visualização"
            } finally {
                onEnd(error)
            }
        }
    }

    fun stop() { job?.cancel() }

    fun release() { scope.coroutineContext[Job]?.cancel() }

    private suspend fun run(t: TrackEntity, settings: NormalizationSettings, processed: Boolean) {
        val ctx = kotlin.coroutines.coroutineContext
        val start = (t.durationMs / 2 - CLIP_MS / 2).coerceAtLeast(0)
        val gain = if (processed) 10.0.pow((GainPlanner.forTrack(t, settings)?.gainDb ?: 0f) / 20.0).toFloat() else 1f
        // 10.5: pede o foco de áudio (ligação, outro app) e para a pré-visualização se o perder; o player do CD, se estiver
        // tocando, pausa durante o trecho e volta sozinho ao final (foco transitório).
        val am = context.getSystemService(AudioManager::class.java)
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
            }
            .build()
        if (am.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            throw IllegalStateException("Não foi possível tocar agora: outro app está usando o áudio (ligação?).")
        }
        var audio: AudioTrack? = null
        var eq: ThreeBandEqualizer? = null
        try {
            PcmDecoder.decode(
                context, Uri.parse(t.uri), startMs = start, maxMs = CLIP_MS,
                onFormat = { f ->
                    require(f.channels in 1..2) { "Formato de canais não suportado na pré-visualização" }
                    val mask = if (f.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
                    val fmt = AudioFormat.Builder().setSampleRate(f.sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(mask).build()
                    val min = AudioTrack.getMinBufferSize(f.sampleRate, mask, AudioFormat.ENCODING_PCM_FLOAT)
                    audio = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                        )
                        .setAudioFormat(fmt).setBufferSizeInBytes(maxOf(min, 16384))
                        .setTransferMode(AudioTrack.MODE_STREAM).build()
                    if (processed) eq = ThreeBandEqualizer(f.sampleRate, f.channels, t.bassDb, t.midDb, t.trebleDb)
                    audio!!.play()
                }
            ) { buf, n ->
                ctx.ensureActive()
                eq?.process(buf, n)
                for (i in 0 until n) buf[i] = (buf[i] * gain).coerceIn(-1f, 1f)
                audio!!.write(buf, 0, n, AudioTrack.WRITE_BLOCKING) >= 0
            }
            audio?.stop() // drena o buffer restante
        } finally {
            audio?.release()
            am.abandonAudioFocusRequest(focus)
        }
    }

    companion object { const val CLIP_MS = 15_000L }
}
