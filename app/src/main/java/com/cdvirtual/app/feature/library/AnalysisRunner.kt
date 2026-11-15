package com.cdvirtual.app.feature.library

import android.content.Context
import android.net.Uri
import com.cdvirtual.app.media.normalization.LoudnessAnalyzer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Analisa em segundo plano as músicas ainda sem análise. Não altera os arquivos originais. */
class AnalysisRunner(private val context: Context, private val repo: TrackRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var again = false

    /** T-301: faixas cuja análise falhou nesta sessão e o motivo (só em memória; sem coluna nova no banco). */
    private val _failures = MutableStateFlow<Map<Long, String>>(emptyMap())
    val failures: StateFlow<Map<Long, String>> = _failures.asStateFlow()

    fun kick() {
        synchronized(this) {
            if (job?.isActive == true) { again = true; return }
            job = scope.launch {
                do { again = false; processPending() } while (again)
            }
        }
    }

    /** T-301: tenta de novo uma faixa que falhou. */
    fun retry(trackId: Long) {
        _failures.update { it - trackId }
        kick()
    }

    private suspend fun processPending() {
        val ctx = currentCoroutineContext()
        val failed = mutableSetOf<Long>()
        while (true) {
            val pending = repo.pendingAnalysis().filter { it.id !in failed && it.id !in _failures.value }
            if (pending.isEmpty()) return
            for (t in pending) {
                try {
                    val r = LoudnessAnalyzer.analyze(context, Uri.parse(t.uri)) { ctx.ensureActive() }
                    repo.edit(t.id) { it.copy(peakDb = r.peakDb, rmsDb = r.rmsDb, lufs = r.lufs, truePeakDb = r.truePeakDb,
                        bandBassDb = r.bandBassDb, bandMidDb = r.bandMidDb, bandTrebleDb = r.bandTrebleDb) }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Throwable) {
                    failed += t.id
                    val reason = e.message?.takeIf { it.isNotBlank() } ?: "arquivo ilegível ou formato não suportado"
                    _failures.update { it + (t.id to reason) }
                }
            }
        }
    }
}
