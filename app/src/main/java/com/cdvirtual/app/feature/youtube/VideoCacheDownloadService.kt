package com.cdvirtual.app.feature.youtube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cdvirtual.app.CDVirtualApp
import com.cdvirtual.app.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Serviço de primeiro plano exclusivo dos downloads temporários da Central de Vídeos.
 * Não reutiliza o DownloadService da Central de Áudio.
 */
class VideoCacheDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val activeCount = AtomicInteger(0)
    private val cancelledCount = AtomicInteger(0)
    private val lastFailureMessage = AtomicReference<String?>(null)
    private lateinit var notifications: NotificationManager
    private var latestStartId: Int = 0

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == ACTION_CANCEL_ALL) {
            jobs.values.toList().forEach { it.cancel(CancellationException("Cancelado pelo usuário")) }
            return START_NOT_STICKY
        }

        val source = intent?.let(::readSource)
        if (source == null) {
            // Uma Intent inválida não pode derrubar outras transferências já ativas.
            // startForegroundService também pode entregar uma Intent nula após recriação;
            // nesse caso, encerra apenas quando não há trabalho em andamento.
            if (activeCount.get() == 0) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            } else {
                notifications.notify(
                    NOTIFICATION_ID,
                    buildNotification(
                        "${activeCount.get()} preparação(ões) em andamento",
                        true
                    )
                )
            }
            return START_NOT_STICKY
        }

        // A notificação é publicada antes de iniciar qualquer operação de rede.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Preparando vídeo(s)…", true),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else 0
        )

        val cacheKey = "${source.sourceId}:${source.sourceVideoId}:${source.qualityHeight}"
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val app = application as CDVirtualApp
                app.videoCacheDownloadCoordinator.prepare(source) { progress ->
                    val total = progress.totalBytes?.let(::formatBytes) ?: "tamanho desconhecido"
                    notifications.notify(
                        NOTIFICATION_ID,
                        buildNotification(
                            "Recebidos ${formatBytes(progress.downloadedBytes)} de $total",
                            true,
                            progress.fraction
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                cancelledCount.incrementAndGet()
                throw cancelled
            } catch (failure: Exception) {
                android.util.Log.w(TAG, "Falha ao preparar vídeo temporário", failure)
                lastFailureMessage.set(failure.message ?: "Falha ao preparar um vídeo.")
            } finally {
                jobs.remove(cacheKey)
                if (activeCount.decrementAndGet() <= 0) {
                    val failure = lastFailureMessage.getAndSet(null)
                    val wasCancelled = cancelledCount.getAndSet(0) > 0
                    val finalMessage = when {
                        failure != null -> "Falha na preparação: $failure. Consulte o item no cache."
                        wasCancelled -> "Preparação cancelada. Arquivos parciais foram preservados."
                        else -> "Preparação finalizada. Consulte a Central de Vídeos."
                    }
                    notifications.notify(
                        NOTIFICATION_ID,
                        buildNotification(finalMessage, false)
                    )
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelfResult(latestStartId)
                } else {
                    notifications.notify(
                        NOTIFICATION_ID,
                        buildNotification("${activeCount.get()} preparação(ões) em andamento", true)
                    )
                }
            }
        }
        val existing = jobs.putIfAbsent(cacheKey, job)
        if (existing != null) {
            job.cancel()
            return START_NOT_STICKY
        }
        activeCount.incrementAndGet()
        job.start()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** O Android pode encerrar serviços dataSync após o limite de execução em segundo plano. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        jobs.values.toList().forEach {
            it.cancel(CancellationException("Limite de execução do serviço de cache atingido"))
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(message: String, ongoing: Boolean, progress: Float? = null): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Cache de vídeo")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (ongoing) {
            if (progress == null) builder.setProgress(0, 0, true)
            else builder.setProgress(100, (progress * 100).toInt().coerceIn(0, 100), false)
            val cancel = PendingIntent.getService(
                this,
                2,
                Intent(this, VideoCacheDownloadService::class.java).setAction(ACTION_CANCEL_ALL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", cancel)
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Cache de vídeo", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Preparação temporária de vídeos autorizados" }
            )
        }
    }

    private fun readSource(intent: Intent): VideoCacheResolvedSource? = runCatching {
        val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID) ?: return null
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID) ?: return null
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return null
        val mime = intent.getStringExtra(EXTRA_MIME) ?: return null
        val extension = intent.getStringExtra(EXTRA_EXTENSION) ?: return null
        val url = intent.getStringExtra(EXTRA_URL) ?: return null
        val quality = intent.getIntExtra(EXTRA_QUALITY, 0)
        val headers = intent.getBundleExtra(EXTRA_HEADERS)?.keySet()
            ?.associateWith { key -> intent.getBundleExtra(EXTRA_HEADERS)?.getString(key).orEmpty() }
            .orEmpty()
        VideoCacheResolvedSource(
            sourceId = sourceId,
            sourceVideoId = videoId,
            title = title,
            uploader = intent.getStringExtra(EXTRA_UPLOADER),
            thumbnailUrl = intent.getStringExtra(EXTRA_THUMBNAIL),
            durationMs = intent.getLongExtra(EXTRA_DURATION, 0L),
            qualityHeight = quality,
            transferRequest = VideoCacheTransferRequest(
                mediaUrl = url,
                mimeType = mime,
                extension = extension,
                headers = headers,
                expectedBytes = intent.getLongExtra(EXTRA_EXPECTED_BYTES, 0L)
            )
        )
    }.getOrNull()

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    companion object {
        private const val TAG = "VideoCacheDownload"
        private const val CHANNEL_ID = "video_cache_downloads"
        private const val NOTIFICATION_ID = 4801
        private const val ACTION_CANCEL_ALL = "com.cdvirtual.app.video_cache.CANCEL_ALL"
        private const val EXTRA_SOURCE_ID = "source_id"
        private const val EXTRA_VIDEO_ID = "video_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_UPLOADER = "uploader"
        private const val EXTRA_THUMBNAIL = "thumbnail"
        private const val EXTRA_DURATION = "duration"
        private const val EXTRA_QUALITY = "quality"
        private const val EXTRA_URL = "media_url"
        private const val EXTRA_MIME = "mime"
        private const val EXTRA_EXTENSION = "extension"
        private const val EXTRA_HEADERS = "headers"
        private const val EXTRA_EXPECTED_BYTES = "expected_bytes"

        fun start(context: Context, source: VideoCacheResolvedSource) {
            val headers = Bundle().apply {
                source.transferRequest.headers.forEach { (key, value) -> putString(key, value) }
            }
            val intent = Intent(context, VideoCacheDownloadService::class.java).apply {
                putExtra(EXTRA_SOURCE_ID, source.sourceId)
                putExtra(EXTRA_VIDEO_ID, source.sourceVideoId)
                putExtra(EXTRA_TITLE, source.title)
                putExtra(EXTRA_UPLOADER, source.uploader)
                putExtra(EXTRA_THUMBNAIL, source.thumbnailUrl)
                putExtra(EXTRA_DURATION, source.durationMs)
                putExtra(EXTRA_QUALITY, source.qualityHeight)
                putExtra(EXTRA_URL, source.transferRequest.mediaUrl)
                putExtra(EXTRA_MIME, source.transferRequest.mimeType)
                putExtra(EXTRA_EXTENSION, source.transferRequest.extension)
                putExtra(EXTRA_HEADERS, headers)
                putExtra(EXTRA_EXPECTED_BYTES, source.transferRequest.expectedBytes)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
