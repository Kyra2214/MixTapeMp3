package com.cdvirtual.app.media.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.cdvirtual.app.core.common.CoverDecoder
import com.cdvirtual.app.media.metadata.Id3Reader
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano (tipo mediaPlayback) que mantém o app vivo enquanto há um MP3 carregado no [Mp3Player]
 * (8.7). Para sozinho quando o player é liberado. A notificação (8.8) usa Notification.MediaStyle do
 * framework, com controles de pausar/continuar, ±15 s e parar.
 */
class PlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private var receiverRegistered = false
    private var observing = false
    private var coverKey: String? = null
    private var coverBitmap: Bitmap? = null

    /** Fones/Bluetooth desconectados: pausa em vez de tocar no alto-falante. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { Mp3Player.pause() }
    }

    /** Sessão de mídia: botões de fones/Bluetooth/volante (AVRCP) e metadados para o display do carro. */
    private fun ensureSession(): MediaSession = session ?: MediaSession(this, "CDVirtual").also { s ->
        s.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { Mp3Player.resume() }
            override fun onPause() { Mp3Player.pause() }
            override fun onStop() { Mp3Player.release() }
            override fun onSeekTo(pos: Long) { Mp3Player.seekTo(pos) }
            override fun onFastForward() { Mp3Player.seekBy(SKIP_MS) }
            override fun onRewind() { Mp3Player.seekBy(-SKIP_MS) }
            override fun onSkipToNext() { if (!Mp3Player.nextChapter()) Mp3Player.nextTrack() }
            override fun onSkipToPrevious() { if (!Mp3Player.previousChapter()) Mp3Player.previousTrack() }
        })
        s.isActive = true
        session = s
    }

    private fun updateSession(s: PlayerState) {
        val ms = ensureSession()
        val pos = Mp3Player.positionMs()
        ms.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(if (s.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, pos, if (s.playing) 1f else 0f)
                .build()
        )
        val newCoverKey = s.file?.absolutePath ?: s.trackCoverPath
        if (coverKey != newCoverKey) {
            coverKey = newCoverKey
            coverBitmap = when {
                s.file != null -> loadCover(s.file)
                s.trackCoverPath != null -> loadCoverPath(s.trackCoverPath)
                else -> null
            }
        }
        ms.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, s.chapterAt(pos)?.title?.ifBlank { null } ?: s.trackTitle ?: s.file?.nameWithoutExtension ?: "MixTape MP3")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, s.trackArtist ?: "")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, s.file?.nameWithoutExtension ?: s.trackArtist ?: "")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, s.durationMs)
                .apply { coverBitmap?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build()
        )
    }

    private fun loadCover(file: java.io.File): Bitmap? {
        val bytes = Id3Reader.readCover(file) ?: return null
        return try {
            CoverDecoder.fromBytes(bytes, 512)
        } catch (_: Throwable) { null }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { Mp3Player.release(); stopSelf(); return START_NOT_STICKY }
            ACTION_TOGGLE -> { Mp3Player.togglePlayPause(); return START_NOT_STICKY }
            ACTION_BACK -> { Mp3Player.seekBy(-SKIP_MS); return START_NOT_STICKY }
            ACTION_FWD -> { Mp3Player.seekBy(SKIP_MS); return START_NOT_STICKY }
        }
        ensureChannel()
        ensureSession()
        updateSession(Mp3Player.state.value)
        if (!receiverRegistered) { registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)); receiverRegistered = true }
        startForegroundCompat(buildNotification(Mp3Player.state.value))
        if (!observing) {
            observing = true
            scope.launch {
                Mp3Player.state.collect { s ->
                    if (s.file == null && s.trackId == null) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        updateSession(s)
                        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(s))
                    }
                }
            }
            // Atualiza o título (faixa atual) enquanto toca, pois ele muda com a posição.
            scope.launch {
                while (true) {
                    kotlinx.coroutines.delay(2000)
                    val s = Mp3Player.state.value
                    if ((s.file != null || s.trackId != null) && s.playing) {
                        updateSession(s)
                        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(s))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        if (receiverRegistered) { try { unregisterReceiver(noisyReceiver) } catch (_: Throwable) { }; receiverRegistered = false }
        session?.let { it.isActive = false; it.release() }
        session = null
        super.onDestroy()
    }

    private fun action(icon: Int, label: String, action: String, requestCode: Int): Notification.Action =
        Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, icon), label,
            PendingIntent.getService(
                this, requestCode, Intent(this, PlaybackService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        ).build()

    /** Notificação de mídia (framework Notification.MediaStyle): retroceder 15 s, pausar/continuar, avançar 15 s, parar. */
    private fun buildNotification(s: PlayerState): Notification {
        val name = s.chapterAt(Mp3Player.positionMs())?.title?.ifBlank { null } ?: s.trackTitle ?: s.file?.nameWithoutExtension ?: "MixTape MP3"
        val album = s.file?.nameWithoutExtension ?: s.trackArtist ?: "MixTape MP3"
        val openApp = PendingIntent.getActivity(
            this, 10, Intent(this, com.cdvirtual.app.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(name)
            .setContentText(album)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(android.R.drawable.ic_media_rew, "Voltar 15 s", ACTION_BACK, 1))
            .addAction(
                if (s.playing) action(android.R.drawable.ic_media_pause, "Pausar", ACTION_TOGGLE, 2)
                else action(android.R.drawable.ic_media_play, "Continuar", ACTION_TOGGLE, 2)
            )
            .addAction(action(android.R.drawable.ic_media_ff, "Avançar 15 s", ACTION_FWD, 3))
            .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Parar", ACTION_STOP, 4))
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2).apply { session?.let { setMediaSession(it.sessionToken) } })
            .build()
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIF_ID, n)
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Reprodução", NotificationManager.IMPORTANCE_LOW))
        }
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIF_ID = 2001
        private const val ACTION_START = "com.cdvirtual.app.PLAYBACK_START"
        private const val ACTION_STOP = "com.cdvirtual.app.PLAYBACK_STOP"
        private const val ACTION_TOGGLE = "com.cdvirtual.app.PLAYBACK_TOGGLE"
        private const val ACTION_BACK = "com.cdvirtual.app.PLAYBACK_BACK"
        private const val ACTION_FWD = "com.cdvirtual.app.PLAYBACK_FWD"
        private const val SKIP_MS = 15_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, PlaybackService::class.java).setAction(ACTION_START)
            )
        }
    }
}
