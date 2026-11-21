package com.cdvirtual.app.media.youtube

import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.cdvirtual.app.CDVirtualApp
import com.cdvirtual.app.core.database.YouTubeHistoryEntity
import com.cdvirtual.app.media.connectors.YouTubeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Player isolado da reprodução de MP3 local. O MediaSessionService mantém a reprodução
 * em segundo plano e fornece controles de mídia do Android.
 */
class YouTubePlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var loadingVideoId: String? = null

    override fun onCreate() {
        super.onCreate()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("com.google.android.youtube/20.10.38 (Linux; U; Android 11)")
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://www.youtube.com/",
                    "Origin" to "https://www.youtube.com",
                    "Accept" to "*/*"
                )
            )
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpFactory)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        player.setAudioAttributes(AudioAttributes.DEFAULT, true)
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_PLAY) {
            val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)?.takeIf { it.isNotBlank() }
            if (videoId != null) {
                if (player.currentMediaItem?.mediaId == videoId) {
                    if (player.playbackState == androidx.media3.common.Player.STATE_ENDED) player.seekTo(0L)
                    player.play()
                } else if (videoId != loadingVideoId) {
                    loadingVideoId = videoId
                    scope.launch { loadAndPlay(videoId) }
                }
            }
        }
        return START_STICKY
    }

    private suspend fun loadAndPlay(videoId: String) {
        try {
            val info = YouTubeApi.resolvePlayback(videoId)
            val dao = (application as CDVirtualApp).database.youtubeHistoryDao()
            val previous = dao.find(videoId)
            dao.save(
                YouTubeHistoryEntity(
                    videoId = videoId,
                    title = info.title,
                    uploader = info.uploader,
                    thumbnailUrl = info.thumbnailUrl,
                    durationSeconds = info.durationSeconds,
                    lastPositionMs = previous?.lastPositionMs ?: 0L,
                    watchedAt = System.currentTimeMillis(),
                    watchCount = (previous?.watchCount ?: 0) + 1
                )
            )
            val metadata = MediaMetadata.Builder()
                .setTitle(info.title)
                .setArtist(info.uploader)
                .setArtworkUri(info.thumbnailUrl?.let(android.net.Uri::parse))
                .build()
            player.setMediaItem(
                MediaItem.Builder()
                    .setMediaId(videoId)
                    .setUri(info.streamUrl)
                    .apply { info.mimeType?.let { setMimeType(it) } }
                    .setMediaMetadata(metadata)
                    .build()
            )
            player.prepare()
            if ((previous?.lastPositionMs ?: 0L) > 0L) {
                player.seekTo(previous!!.lastPositionMs)
            }
            player.play()
            scope.launch { persistProgress(videoId) }
            if (loadingVideoId == videoId) loadingVideoId = null
        } catch (failure: Exception) {
            android.util.Log.e(TAG, "Não foi possível iniciar o vídeo $videoId", failure)
            loadingVideoId = null
        }
    }

    private suspend fun persistProgress(videoId: String) {
        val dao = (application as CDVirtualApp).database.youtubeHistoryDao()
        while (player.currentMediaItem?.mediaId == videoId) {
            delay(5_000)
            if (player.currentMediaItem?.mediaId == videoId && player.currentPosition >= 0L) {
                dao.updateProgress(videoId, player.currentPosition, System.currentTimeMillis())
            }
            if (player.playbackState == androidx.media3.common.Player.STATE_ENDED) break
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        } ?: player.release()
        scope.cancel()
        loadingVideoId = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "YouTubePlayback"
        private const val ACTION_PLAY = "com.cdvirtual.app.youtube.PLAY"
        private const val EXTRA_VIDEO_ID = "youtube_video_id"

        fun play(context: Context, videoId: String) {
            context.startService(
                Intent(context, YouTubePlaybackService::class.java)
                    .setAction(ACTION_PLAY)
                    .putExtra(EXTRA_VIDEO_ID, videoId)
            )
        }
    }
}
