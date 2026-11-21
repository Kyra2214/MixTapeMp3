package com.cdvirtual.app.media.youtube

import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.cdvirtual.app.CDVirtualApp
import com.cdvirtual.app.MainActivity
import com.cdvirtual.app.core.database.YouTubeHistoryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var loadingAudioOnly = false
    private var playbackRequestId = 0L
    private var playbackMonitorJob: Job? = null
    private var sponsorSegments: List<SponsorSegment> = emptyList()
    private val skippedSponsorSegments = mutableSetOf<Int>()
    private var hasStartedCurrentItem = false
    private var resumingVideoId: String? = null
    private var resumingRequestId = 0L

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
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                hasStartedCurrentItem = false
                val resumedId = mediaItem?.mediaId
                if (resumedId != null && resumedId == resumingVideoId) {
                    val requestId = resumingRequestId
                    resumingVideoId = null
                    loadingVideoId = null
                    startPlaybackMonitor(resumedId, requestId)
                    val sponsorEnabled = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                        .getBoolean(KEY_SPONSOR_BLOCK, false)
                    if (sponsorEnabled) requestSponsorSegments(resumedId, requestId)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    hasStartedCurrentItem = true
                } else if (hasStartedCurrentItem && player.playbackState != Player.STATE_ENDED) {
                    persistCurrentPosition()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    persistCurrentPosition(resetToStart = true)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                persistCurrentPosition()
                YouTubePlaybackStatus.reportError(error.toYouTubePlaybackMessage())
            }
        })
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(playbackResumptionCallback)
            .build()
    }

    private val playbackResumptionCallback = object : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                try {
                    val history = (application as CDVirtualApp).database
                        .youtubeHistoryDao().findMostRecent()
                        ?: throw IllegalStateException("Nenhum vídeo disponível no histórico para retomar")
                    val audioOnly = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                        .getBoolean(KEY_LAST_AUDIO_ONLY, false)
                    val info = if (isForPlayback) {
                        YouTubePlaybackRepository.resolve(history.videoId)
                    } else {
                        null
                    }
                    val metadata = MediaMetadata.Builder()
                        .setTitle(info?.title ?: history.title)
                        .setArtist(info?.uploader ?: history.uploader)
                        .setArtworkUri((info?.thumbnailUrl ?: history.thumbnailUrl)?.let(android.net.Uri::parse))
                        .setExtras(Bundle().apply { putBoolean(EXTRA_AUDIO_ONLY, audioOnly) })
                        .build()
                    val itemBuilder = MediaItem.Builder()
                        .setMediaId(history.videoId)
                        .setMediaMetadata(metadata)
                    if (info != null) {
                        val streamUrl = if (audioOnly) info.audioStreamUrl
                            ?: throw YouTubeUnavailableException(YouTubeFailure.NoAudio)
                        else info.streamUrl
                        val mimeType = if (audioOnly) info.audioMimeType else info.mimeType
                        itemBuilder.setUri(streamUrl)
                        mimeType?.let(itemBuilder::setMimeType)
                    }
                    if (isForPlayback) {
                        val requestId = ++playbackRequestId
                        resumingVideoId = history.videoId
                        resumingRequestId = requestId
                        loadingVideoId = history.videoId
                        loadingAudioOnly = audioOnly
                    }
                    result.set(
                        MediaSession.MediaItemsWithStartPosition(
                            listOf(itemBuilder.build()),
                            0,
                            if (isForPlayback) history.lastPositionMs.coerceAtLeast(0L) else C.TIME_UNSET
                        )
                    )
                } catch (cancelled: CancellationException) {
                    result.cancel(false)
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w(TAG, "Não foi possível preparar a retomada do YouTube", failure)
                    result.setException(failure)
                }
            }
            return result
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_PLAY) {
            YouTubePlaybackStatus.clearError()
            val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)?.takeIf { it.isNotBlank() }
            val audioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)
            getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_LAST_AUDIO_ONLY, audioOnly).apply()
            if (videoId != null) {
                val currentAudioOnly = player.currentMediaItem?.mediaMetadata?.extras
                    ?.getBoolean(EXTRA_AUDIO_ONLY, false) ?: false
                if (
                    player.currentMediaItem?.mediaId == videoId &&
                    currentAudioOnly == audioOnly &&
                    player.playbackState != Player.STATE_IDLE
                ) {
                    val requestId = ++playbackRequestId
                    loadingVideoId = null
                    if (player.playbackState == Player.STATE_ENDED) {
                        // Uma reprodução concluída que começa de novo deve poder
                        // pular novamente os mesmos segmentos do SponsorBlock.
                        skippedSponsorSegments.clear()
                        player.seekTo(0L)
                    }
                    player.play()
                    startPlaybackMonitor(videoId, requestId)
                    val sponsorEnabled = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                        .getBoolean(KEY_SPONSOR_BLOCK, false)
                    if (sponsorEnabled && sponsorSegments.isEmpty()) {
                        requestSponsorSegments(videoId, requestId)
                    }
                } else if (videoId != loadingVideoId || audioOnly != loadingAudioOnly) {
                    // Evita que o vídeo anterior continue tocando enquanto o novo stream é resolvido.
                    player.pause()
                    loadingVideoId = videoId
                    loadingAudioOnly = audioOnly
                    val requestId = ++playbackRequestId
                    scope.launch { loadAndPlay(videoId, audioOnly, requestId) }
                }
            }
        }
        // Não há restauração de sessão para um processo morto; evite recriar um serviço vazio.
        return START_NOT_STICKY
    }

    private suspend fun loadAndPlay(videoId: String, audioOnly: Boolean, requestId: Long) {
        try {
            val info = YouTubePlaybackRepository.resolve(videoId)
            if (!isCurrentRequest(videoId, audioOnly, requestId)) return
            sponsorSegments = emptyList()
            skippedSponsorSegments.clear()
            // O histórico é complementar: falha no Room não pode impedir a reprodução.
            val historyDao = runCatching {
                (application as CDVirtualApp).database.youtubeHistoryDao()
            }.onFailure { failure ->
                android.util.Log.w(TAG, "Histórico indisponível; a reprodução continuará", failure)
            }.getOrNull()
            val previous = historyDao?.let { dao ->
                runCatching { dao.find(videoId) }.onFailure { failure ->
                    android.util.Log.w(TAG, "Não foi possível consultar o histórico", failure)
                }.getOrNull()
            }
            if (!isCurrentRequest(videoId, audioOnly, requestId)) return
            historyDao?.let { dao ->
                runCatching {
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
                }.onFailure { failure ->
                    android.util.Log.w(TAG, "Não foi possível registrar o vídeo no histórico", failure)
                }
            }
            if (!isCurrentRequest(videoId, audioOnly, requestId)) return
            val metadata = MediaMetadata.Builder()
                .setTitle(info.title)
                .setArtist(info.uploader)
                .setArtworkUri(info.thumbnailUrl?.let(android.net.Uri::parse))
                .setExtras(Bundle().apply { putBoolean(EXTRA_AUDIO_ONLY, audioOnly) })
                .build()
            if (!isCurrentRequest(videoId, audioOnly, requestId)) return
            val selectedUrl = if (audioOnly) info.audioStreamUrl
                ?: throw YouTubeUnavailableException(YouTubeFailure.NoAudio) else info.streamUrl
            val selectedMimeType = if (audioOnly) info.audioMimeType else info.mimeType
            player.setMediaItem(
                MediaItem.Builder()
                    .setMediaId(videoId)
                    .setUri(selectedUrl)
                    .apply { selectedMimeType?.let { setMimeType(it) } }
                    .setMediaMetadata(metadata)
                    .build()
            )
            player.prepare()
            if ((previous?.lastPositionMs ?: 0L) > 0L) {
                player.seekTo(previous!!.lastPositionMs)
            }
            player.play()
            startPlaybackMonitor(videoId, requestId)
            val sponsorEnabled = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(KEY_SPONSOR_BLOCK, false)
            if (sponsorEnabled) requestSponsorSegments(videoId, requestId)
            if (isCurrentRequest(videoId, audioOnly, requestId)) loadingVideoId = null
        } catch (cancelled: CancellationException) {
            // Encerrar o serviço ou cancelar a tarefa não deve aparecer como erro ao usuário.
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.e(TAG, "Não foi possível iniciar o vídeo $videoId", failure)
            if (isCurrentRequest(videoId, audioOnly, requestId)) {
                YouTubePlaybackStatus.reportError(failure.toYouTubePlaybackMessage())
                loadingVideoId = null
            }
        }
    }

    private fun requestSponsorSegments(videoId: String, requestId: Long) {
        scope.launch {
            val segments = SponsorBlockRepository.getSkippableSegments(videoId)
            // A resolução do stream já terminou e loadingVideoId pode estar limpo.
            // Geração + mediaId identificam a reprodução ativa.
            if (playbackRequestId == requestId && player.currentMediaItem?.mediaId == videoId) {
                sponsorSegments = segments
            }
        }
    }

    private fun startPlaybackMonitor(videoId: String, requestId: Long) {
        playbackMonitorJob?.cancel()
        playbackMonitorJob = scope.launch { monitorPlayback(videoId, requestId) }
    }

    private fun persistCurrentPosition(resetToStart: Boolean = false) {
        val videoId = player.currentMediaItem?.mediaId ?: return
        val position = if (resetToStart) 0L else player.currentPosition.coerceAtLeast(0L)
        scope.launch {
            runCatching {
                (application as CDVirtualApp).database.youtubeHistoryDao()
                    .updateProgress(videoId, position)
            }.onFailure { failure ->
                android.util.Log.w(TAG, "Não foi possível salvar o progresso de $videoId", failure)
            }
        }
    }

    private fun isCurrentRequest(videoId: String, audioOnly: Boolean, requestId: Long): Boolean =
        playbackRequestId == requestId && loadingVideoId == videoId && loadingAudioOnly == audioOnly

    private suspend fun monitorPlayback(videoId: String, requestId: Long) {
        val dao = runCatching {
            (application as CDVirtualApp).database.youtubeHistoryDao()
        }.onFailure { failure ->
            android.util.Log.w(TAG, "Monitor de histórico indisponível", failure)
        }.getOrNull() ?: return
        var lastProgressWrite = SystemClock.elapsedRealtime()
        while (playbackRequestId == requestId && player.currentMediaItem?.mediaId == videoId) {
            delay(500)
            if (playbackRequestId != requestId || player.currentMediaItem?.mediaId != videoId) break
            // Um erro de reprodução normalmente devolve o player a STATE_IDLE.
            // Não mantenha um monitor acordando a cada meio segundo após a falha.
            if (player.playbackState == Player.STATE_IDLE) break

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
                if (playbackRequestId != requestId || player.currentMediaItem?.mediaId != videoId) break
                runCatching {
                    dao.updateProgress(videoId, savedPosition)
                }.onFailure { failure ->
                    android.util.Log.w(TAG, "Não foi possível atualizar o progresso do histórico", failure)
                }
                lastProgressWrite = now
            }
            if (ended) break
        }
    }

    override fun onDestroy() {
        // Faz uma última tentativa de salvar o ponto atual em um escopo independente,
        // pois o escopo principal será cancelado logo abaixo.
        player.currentMediaItem?.mediaId?.let { videoId ->
            val position = if (player.playbackState == Player.STATE_ENDED) {
                0L
            } else {
                player.currentPosition.coerceAtLeast(0L)
            }
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    (application as CDVirtualApp).database.youtubeHistoryDao()
                        .updateProgress(videoId, position)
                }.onFailure { failure ->
                    android.util.Log.w(TAG, "Não foi possível salvar a posição ao encerrar o serviço", failure)
                }
            }
        }
        playbackMonitorJob?.cancel()
        loadingVideoId = null
        playbackRequestId++
        mediaSession?.release()
        mediaSession = null
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "YouTubePlayback"
        private const val ACTION_PLAY = "com.cdvirtual.app.youtube.PLAY"
        private const val EXTRA_VIDEO_ID = "youtube_video_id"
        const val EXTRA_AUDIO_ONLY = "youtube_audio_only"
        const val PREFERENCES = "youtube_preferences"
        const val KEY_SPONSOR_BLOCK = "sponsor_block_enabled"
        private const val KEY_LAST_AUDIO_ONLY = "last_audio_only"

        fun play(context: Context, videoId: String, audioOnly: Boolean = false) {
            // Chamado pela interface enquanto o app está visível. O MediaSessionService
            // assume o foreground automaticamente quando a reprodução efetivamente começa.
            context.startService(
                Intent(context, YouTubePlaybackService::class.java)
                    .setAction(ACTION_PLAY)
                    .putExtra(EXTRA_VIDEO_ID, videoId)
                    .putExtra(EXTRA_AUDIO_ONLY, audioOnly)
            )
        }
    }
}
