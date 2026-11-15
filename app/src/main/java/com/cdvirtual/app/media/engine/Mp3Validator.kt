package com.cdvirtual.app.media.engine

import android.content.Context
import android.net.Uri
import com.cdvirtual.app.media.decoder.PcmDecoder
import com.cdvirtual.app.media.encoder.Mp3FrameScanner
import com.cdvirtual.app.media.encoder.Mp3StreamInfo
import java.io.File
import kotlin.math.abs

data class ValidationReport(val info: Mp3StreamInfo?, val problems: List<String>) {
    val ok get() = problems.isEmpty()
    fun summary() = if (ok) "MP3 válido" else problems.joinToString("; ")
}

/**
 * Validação do MP3 final antes de entregá-lo: (1) arquivo existe e não está vazio; (2) todos os quadros são
 * MPEG-1 Layer III 44,1 kHz 320 kbps CBR, sem bytes soltos; (3) duração dos quadros compatível com o esperado
 * (tolerância para atraso/preenchimento do encoder); (4) início e fim realmente decodificam em áudio.
 */
object Mp3Validator {
    private const val DURATION_TOLERANCE_MS = 200L
    private const val PROBE_MS = 2000L

    fun validate(context: Context, file: File, expectedMs: Long): ValidationReport {
        val problems = ArrayList<String>()
        if (!file.exists() || file.length() == 0L) return ValidationReport(null, listOf("arquivo ausente ou vazio"))
        val info = Mp3FrameScanner.scan(file)
        if (info.frames == 0L) return ValidationReport(info, listOf("nenhum quadro MP3 encontrado"))
        if (!info.isCbr320) {
            problems += "fora da especificação (${info.bitrates} kbps, ${info.sampleRates} Hz, ${info.junkBytes} bytes soltos)"
        }
        if (abs(info.durationMs - expectedMs) > DURATION_TOLERANCE_MS) {
            problems += "duração ${info.durationMs} ms difere do esperado ($expectedMs ms)"
        }
        if (problems.isEmpty()) {
            val uri = Uri.fromFile(file)
            if (!decodesAudio(context, uri, 0)) problems += "o início do arquivo não decodifica"
            if (!decodesAudio(context, uri, (info.durationMs - 3000).coerceAtLeast(0))) problems += "o final do arquivo não decodifica"
        }
        return ValidationReport(info, problems)
    }

    private fun decodesAudio(context: Context, uri: Uri, startMs: Long): Boolean = try {
        var frames = 0L
        PcmDecoder.decode(context, uri, startMs = startMs, maxMs = PROBE_MS) { _, n -> frames += n; true }
        frames > 0
    } catch (_: Throwable) { false }
}
