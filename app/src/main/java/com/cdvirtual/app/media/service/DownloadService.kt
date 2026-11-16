package com.cdvirtual.app.media.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class DownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = build(this, "Preparando downloads…", 0f)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, n)
        return START_STICKY
    }
    companion object {
        private const val CHANNEL = "downloads"
        private const val ID = 1101
        private fun build(c: Context, message: String, progress: Float): Notification =
            NotificationCompat.Builder(c, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("MixTape MP3 · Downloads").setContentText(message).setOngoing(true)
                .setOnlyAlertOnce(true).setProgress(100, (progress.coerceIn(0f,1f)*100).toInt(), progress <= 0f).build()
        fun start(c: Context) { ContextCompat.startForegroundService(c, Intent(c, DownloadService::class.java)) }
        fun update(c: Context, message: String, progress: Float) {
            c.getSystemService(NotificationManager::class.java).notify(ID, build(c, message, progress))
        }
        fun finish(c: Context) {
            c.stopService(Intent(c, DownloadService::class.java))
            c.getSystemService(NotificationManager::class.java).cancel(ID)
        }
    }
}