package com.cdvirtual.app.media.engine

import android.content.Context
import android.net.Uri
import com.cdvirtual.app.core.database.TrackEntity
import com.cdvirtual.app.media.decoder.PcmDecoder
import com.cdvirtual.app.media.decoder.PcmFormat
import kotlinx.coroutines.CancellationException

/** Falha ao decodificar uma faixa do CD; [message] já é legível para o usuário. */
class DecodeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Resultado da decodificação de uma faixa: formato original e total de quadros entregues. */
data class DecodeInfo(val sampleRate: Int, val channels: Int, val frames: Long)

/**
 * Decodificação de faixas para o motor de áudio (Fase 06): lê o original em modo somente leitura e entrega PCM
 * float intercalado no formato nativo da faixa, com progresso e cancelamento. Padronização (6.2), ganho/EQ (6.3)
 * e concatenação (6.4) são etapas posteriores sobre este fluxo.
 */
object EngineDecoder {
    /**
     * @param cancelCheck deve lançar [CancellationException] quando o trabalho for cancelado.
     * @param onProgress fração 0..1 estimada pela duração cadastrada.
     * @param sink recebe blocos PCM; o buffer é reutilizado, copie se precisar guardá-lo.
     * @throws DecodeException arquivo ausente, ilegível, formato não suportado ou sem áudio.
     */
    fun decode(
        context: Context,
        track: TrackEntity,
        cancelCheck: () -> Unit = {},
        onFormat: (PcmFormat) -> Unit = {},
        onProgress: (Float) -> Unit = {},
        sink: (samples: FloatArray, count: Int) -> Unit
    ): DecodeInfo {
        var format: PcmFormat? = null
        var frames = 0L
        var expected = 0L
        try {
            PcmDecoder.decode(
                context, Uri.parse(track.uri),
                onFormat = {
                    format = it
                    expected = track.durationMs * it.sampleRate / 1000
                    onFormat(it)
                }
            ) { buf, n ->
                cancelCheck()
                sink(buf, n)
                frames += n / format!!.channels
                if (expected > 0) onProgress((frames.toDouble() / expected).coerceIn(0.0, 1.0).toFloat())
                true
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: DecodeException) {
            throw e
        } catch (e: SecurityException) {
            throw DecodeException("Sem permissão para ler \"${track.title}\"", e)
        } catch (e: java.io.FileNotFoundException) {
            throw DecodeException("Arquivo de \"${track.title}\" não encontrado", e)
        } catch (e: IllegalArgumentException) {
            throw DecodeException("\"${track.title}\" não contém áudio decodificável", e)
        } catch (e: Throwable) {
            throw DecodeException("Falha ao decodificar \"${track.title}\"", e)
        }
        val f = format
        if (f == null || frames == 0L) throw DecodeException("\"${track.title}\" está vazio ou ilegível")
        onProgress(1f)
        return DecodeInfo(f.sampleRate, f.channels, frames)
    }
}
