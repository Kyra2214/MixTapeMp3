package com.cdvirtual.app.media.youtube

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.cdvirtual.app.CDVirtualApp
import com.cdvirtual.app.core.database.YouTubeHistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object YouTubePlaybackStatus {
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    fun clearError() {
        mutableError.value = null
    }

    fun reportError(message: String) {
        mutableError.value = message
    }
}

/**
 * Player isolado da reprodução de MP3 local. O MediaSessionService mantém a reprodução
 * em segundo plano e fornece controles de mídia do Android.
 */
class YouTubePlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var loadingVideoId: String? = null
    private var sponsorSegments: List<SponsorSegment> = emptyList()
    private val skippedSponsorSegments = mutableSetOf<Int>()

    override fun onCreate() {
        super.onCreate()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
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
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.setAudioAttributes(AudioAttributes.DEFAULT, true)
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_PLAY) {
            YouTubePlaybackStatus.clearError()
            val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)?.takeIf { it.isNotBlank() }
            if (videoId != null) {
                if (player.currentMediaItem?.mediaId == videoId) {
                    loadingVideoId = null
                    if (player.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                        player.seekTo(0L)
                    } else if (player.playbackState == androidx.media3.common.Player.STATE_IDLE) {
                        player.prepare()
                    }
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
            val info = YouTubePlaybackRepository.resolve(videoId)
            if (loadingVideoId != videoId) return
            sponsorSegments = emptyList()
            skippedSponsorSegments.clear()
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
            if (loadingVideoId != videoId) return
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
            scope.launch { monitorPlayback(videoId) }
            val sponsorEnabled = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(KEY_SPONSOR_BLOCK, false)
            if (sponsorEnabled) {
                scope.launch {
                    val segments = SponsorBlockRepository().getSkippableSegments(videoId)
                    if (player.currentMediaItem?.mediaId == videoId) {
                        sponsorSegments = segments
                    }
                }
            }
            if (loadingVideoId == videoId) loadingVideoId = null
        } catch (failure: Exception) {
            android.util.Log.e(TAG, "Não foi possível iniciar o vídeo $videoId", failure)
            if (loadingVideoId == videoId) {
                YouTubePlaybackStatus.reportError(
                    "Não foi possível obter a transmissão deste vídeo. Ele pode estar indisponível ou o YouTube pode ter alterado o formato."
                )
                loadingVideoId = null
            }
        }
    }

    private suspend fun monitorPlayback(videoId: String) {
        val dao = (application as CDVirtualApp).database.youtubeHistoryDao()
        var lastProgressWrite = SystemClock.elapsedRealtime()
        while (player.currentMediaItem?.mediaId == videoId) {
            delay(500)
            if (player.currentMediaItem?.mediaId != videoId) break

            val sponsorEnabled = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(KEY_SPONSOR_BLOCK, false)
            if (!sponsorEnabled) sponsorSegments = emptyList()

            if (player.isPlaying && sponsorSegments.isNotEmpty()) {
                val positionSeconds = player.currentPosition / 1000.0
                val segmentIndex = sponsorSegments.indices.firstOrNull { index ->
                    index !in skippedSponsorSegments &&
                        positionSeconds >= sponsorSegments[index].startSeconds &&
                        positionSeconds < sponsorSegments[index].endSeconds
                }
                if (segmentIndex != null) {
                    skippedSponsorSegments.add(segmentIndex)
                    player.seekTo((sponsorSegments[segmentIndex].endSeconds * 1000.0).toLong())
                }
            }

            val now = SystemClock.elapsedRealtime()
            val ended = player.playbackState == androidx.media3.common.Player.STATE_ENDED
            if (now - lastProgressWrite >= 5_000L || ended) {
                // Ao concluir, zera a posição para que Retomar recomece do início.
                val savedPosition = if (ended) 0L else player.currentPosition.coerceAtLeast(0L)
                dao.updateProgress(videoId, savedPosition, System.currentTimeMillis())
                lastProgressWrite = now
            }
            if (ended) break
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
        const val PREFERENCES = "youtube_preferences"
        const val KEY_SPONSOR_BLOCK = "sponsor_block_enabled"

        fun play(context: Context, videoId: String) {
            context.startService(
                Intent(context, YouTubePlaybackService::class.java)
                    .setAction(ACTION_PLAY)
                    .putExtra(EXTRA_VIDEO_ID, videoId)
            )
        }
    }
}
