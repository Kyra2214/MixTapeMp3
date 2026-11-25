package com.cdvirtual.app.media.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Serviço de apoio para mídia HTML5 de sites externos ao YouTube.
 * Ele eleva a prioridade do processo enquanto há mídia ativa, mas não substitui
 * uma MediaSession nem garante que o WebView continue tocando após ser ocultado.
 * A reprodução do YouTube usa YouTubePlaybackService/Media3.
 */
class BrowserMediaPlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?.take(120) ?: "Áudio ou vídeo no navegador"
        ensureChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("MixTape MP3")
            .setContentText("Reproduzindo: $title")
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Mídia do navegador", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    companion object {
        private const val ACTION_PLAYING = "com.cdvirtual.app.browser.MEDIA_PLAYING"
        private const val ACTION_STOP = "com.cdvirtual.app.browser.MEDIA_STOP"
        private const val EXTRA_TITLE = "browser_media_title"
        private const val CHANNEL_ID = "browser_media_playback"
        private const val NOTIFICATION_ID = 7302

        fun update(context: Context, playing: Boolean, title: String?) {
            val intent = Intent(context, BrowserMediaPlaybackService::class.java)
                .setAction(if (playing) ACTION_PLAYING else ACTION_STOP)
                .putExtra(EXTRA_TITLE, title)
            if (playing) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }
    }
}
