package com.cdvirtual.app.media.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cdvirtual.app.CDVirtualApp
import com.cdvirtual.app.core.common.formatSize
import com.cdvirtual.app.core.database.TrackEntity
import com.cdvirtual.app.media.encoder.Mp3Spec
import com.cdvirtual.app.media.engine.DecodeException
import com.cdvirtual.app.media.engine.Mp3Exporter
import com.cdvirtual.app.media.metadata.CdMetadata
import com.cdvirtual.app.media.metadata.CueSettings
import com.cdvirtual.app.media.metadata.CueSheet
import com.cdvirtual.app.media.metadata.CueTrack
import com.cdvirtual.app.media.normalization.LoudnessAnalyzer
import com.cdvirtual.app.media.normalization.NormalizationSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Serviço em primeiro plano que gera o MP3 do CD sem depender da tela aberta: mostra notificação com progresso,
 * mantém a CPU ativa (wake lock com limite) e publica o estado em [ExportState].
 * Cancelamento e recuperação: 6.8.
 */
class ExportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) { job?.cancel(); return START_NOT_STICKY }
        // 10.1: quem usa startForegroundService() precisa chamar startForeground() em até 5 s em QUALQUER caminho,
        // inclusive nos de saída antecipada (início duplicado/intent inválida), senão o sistema derruba o app.
        ensureChannel()
        startForegroundCompat(buildNotification("Preparando…", 0f, indeterminate = true))
        val cdId = intent?.getLongExtra(EXTRA_CD_ID, -1L) ?: -1L
        if (intent?.action != ACTION_START || cdId < 0) { leaveForeground(); stopSelf(startId); return START_NOT_STICKY }
        if (job?.isActive == true) return START_NOT_STICKY // já existe uma geração em andamento (notificação mantida)
        acquireWakeLock()
        ExportRecovery.markStarted(this, cdId, "CD")
        job = scope.launch { run(cdId) }
        return START_NOT_STICKY
    }

    /** T-602: na API 35+ o serviço dataSync tem limite de tempo; ao estourar, cancela a geração (sem arquivo parcial) e encerra. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        job?.cancel()
        stopSelf(startId)
    }

    private fun leaveForeground() { stopForeground(STOP_FOREGROUND_REMOVE) }

    private suspend fun run(cdId: Long) {
        val app = application as CDVirtualApp
        var name = "CD"
        try {
            val cd = app.cdRepository.find(cdId) ?: throw IllegalStateException("CD não encontrado")
            name = cd.name
            ExportRecovery.markStarted(this, cdId, name)
            var tracks = app.cdRepository.tracksOf(cdId).first()
            if (tracks.isEmpty()) throw IllegalStateException("O CD não tem músicas")
            update(cdId, name, "Analisando volume", 0f)
            // Faixas ainda sem análise de loudness são analisadas antes, para o ganho automático valer.
            val pending = tracks.filter { it.lufs == null }
            pending.forEachIndexed { i, t ->
                val r = LoudnessAnalyzer.analyze(this, Uri.parse(t.uri)) { job?.ensureActive() }
                app.trackRepository.edit(t.id) {
                    it.copy(peakDb = r.peakDb, rmsDb = r.rmsDb, lufs = r.lufs, truePeakDb = r.truePeakDb,
                        bandBassDb = r.bandBassDb, bandMidDb = r.bandMidDb, bandTrebleDb = r.bandTrebleDb)
                }
                update(cdId, name, "Analisando volume", (i + 1f) / pending.size)
            }
            if (pending.isNotEmpty()) tracks = app.cdRepository.tracksOf(cdId).first()
            val settings = NormalizationSettingsStore(this).load()
            val target = ExportNaming.targetFor(this, cdId, name) // T-401: nunca sobrescreve o MP3 de outro CD
            checkFreeSpace(tracks, target)
            var lastPct = -1
            val result = Mp3Exporter.export(this, tracks, settings, target, cancelCheck = { job?.ensureActive() }, metadata = CdMetadata(title = name, artist = cd.artist, coverJpeg = readCover(cd.coverPath)), onProgress = { p ->
                val pct = (p * 100).toInt()
                if (pct != lastPct) { lastPct = pct; update(cdId, name, "Gerando MP3", p) }
            })
            ExportNaming.claim(this, result.file, cdId)
            if (CueSettings.isEnabled(this)) {
                // CUE opcional: falha aqui não invalida o MP3 já pronto.
                try {
                    val titles = tracks.associate { it.id to it.title }
                    CueSheet.writeNextTo(result.file, name, cd.artist,
                        result.concat.spans.map { CueTrack(titles[it.trackId].orEmpty(), it.startMs) })
                } catch (_: Throwable) { }
            }
            ExportState.set(ExportStatus.Done(cdId, name, result.file.absolutePath, result.file.length(), result.concat.totalMs))
            notifyFinal("MP3 pronto", name)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            ExportState.set(ExportStatus.Cancelled(cdId, name)) // o parcial ".part" já foi removido pelo Mp3Exporter
            throw ce
        } catch (e: DecodeException) {
            fail(cdId, name, e.message ?: "Falha ao decodificar")
        } catch (e: Throwable) {
            fail(cdId, name, e.message ?: "Falha ao gerar o MP3")
        } finally {
            ExportRecovery.markFinished(this)
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * 10.3: CDs longos geram arquivos de centenas de MB (320 kbps ≈ 144 MB/h). No pico coexistem o ".part", o ".tagged" e, ao
     * regerar, o MP3 antigo; sem espaço a geração falharia só no fim, depois de muito processamento. Falha logo, com mensagem clara.
     */
    private fun checkFreeSpace(tracks: List<TrackEntity>, target: File) {
        val estimated = tracks.sumOf { it.durationMs.coerceAtLeast(0) } * Mp3Spec.KBPS / 8
        if (estimated <= 0) return // durações desconhecidas: não há como estimar
        val needed = estimated * 2 + (if (target.isFile) target.length() else 0L) + SPACE_MARGIN_BYTES
        val dir = target.absoluteFile.parentFile?.apply { mkdirs() } ?: filesDir
        val free = dir.usableSpace
        if (free < needed) {
            throw IllegalStateException(
                "Espaço insuficiente: o MP3 terá cerca de ${formatSize(estimated)} e a geração precisa de até ${formatSize(needed)} livres; " +
                    "há ${formatSize(free)}. Libere espaço (por exemplo, excluindo CDs gerados) e tente de novo."
            )
        }
    }

    private fun fail(cdId: Long, name: String, message: String) {
        ExportState.set(ExportStatus.Failed(cdId, name, message))
        notifyFinal("Falha ao gerar o MP3", message)
    }

    private fun update(cdId: Long, name: String, phase: String, progress: Float) {
        ExportState.set(ExportStatus.Running(cdId, name, phase, progress))
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification("$phase — $name", progress, false))
    }

    private fun buildNotification(text: String, progress: Float, indeterminate: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("MixTape MP3")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, (progress * 100).toInt(), indeterminate)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel, "Cancelar",
                PendingIntent.getService(
                    this, 0, Intent(this, ExportService::class.java).setAction(ACTION_CANCEL),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    private fun notifyFinal(title: String, text: String) {
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title).setContentText(text).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID + 1, n)
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIF_ID, n)
    }

    /** Lê a capa do CD (JPEG gerado pelo CoverPrepare); devolve null se ausente, ilegível ou não for JPEG. */
    private fun readCover(path: String?): ByteArray? {
        if (path == null) return null
        val bytes = try { File(path).takeIf { it.isFile && it.length() in 1..MAX_COVER_BYTES }?.readBytes() } catch (_: Throwable) { null }
        return bytes?.takeIf { it.size > 3 && it[0] == 0xFF.toByte() && it[1] == 0xD8.toByte() }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Geração de CD", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CDVirtual:export").apply { acquire(WAKE_LOCK_MAX_MS) }
    }

    private fun releaseWakeLock() { wakeLock?.takeIf { it.isHeld }?.release(); wakeLock = null }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.cdvirtual.app.action.EXPORT_START"
        private const val ACTION_CANCEL = "com.cdvirtual.app.action.EXPORT_CANCEL"
        private const val EXTRA_CD_ID = "cdId"
        private const val CHANNEL_ID = "export"
        private const val NOTIF_ID = 1001
        private const val WAKE_LOCK_MAX_MS = 3L * 60 * 60 * 1000
        private const val SPACE_MARGIN_BYTES = 64L * 1024 * 1024

        fun start(context: Context, cdId: Long) {
            if (ExportState.isRunning) return
            // 10.1: marca "em andamento" já no pedido, fechando a janela entre o toque e a primeira atualização do serviço.
            ExportState.set(ExportStatus.Running(cdId, "CD", "Preparando", 0f))
            val i = Intent(context, ExportService::class.java).setAction(ACTION_START).putExtra(EXTRA_CD_ID, cdId)
            try {
                ContextCompat.startForegroundService(context, i)
            } catch (e: Throwable) {
                // 10.1: se o sistema recusar o serviço, o estado não pode ficar preso em "em andamento".
                ExportState.set(ExportStatus.Failed(cdId, "CD", "Não foi possível iniciar a geração em segundo plano: ${e.message}"))
            }
        }

        fun cancel(context: Context) {
            if (ExportState.isRunning) context.startService(Intent(context, ExportService::class.java).setAction(ACTION_CANCEL))
        }

        private const val MAX_COVER_BYTES = 8L * 1024 * 1024

        fun safeFileName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.').ifEmpty { "CD" }.take(80)
    }
}
