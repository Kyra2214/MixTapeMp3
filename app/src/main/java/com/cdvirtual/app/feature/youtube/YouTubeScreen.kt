package com.cdvirtual.app.feature.youtube

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.cdvirtual.app.CDVirtualApp
import com.cdvirtual.app.core.database.YouTubeHistoryEntity
import com.cdvirtual.app.core.database.YouTubeChannelEntity
import com.cdvirtual.app.core.database.VideoCacheStatus
import com.cdvirtual.app.core.database.VideoCacheQuality
import com.cdvirtual.app.media.youtube.YouTubePlaybackService
import com.cdvirtual.app.media.youtube.YouTubePlaybackStatus
import com.cdvirtual.app.media.youtube.toYouTubePlaybackMessage
import com.cdvirtual.app.media.youtube.YouTubeSearchRepository
import com.cdvirtual.app.media.youtube.YouTubeVideo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.schabi.newpipe.extractor.Page
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun YouTubeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CDVirtualApp
    val repository = remember { YouTubeSearchRepository() }
    val history by app.database.youtubeHistoryDao().observeAll().collectAsState(initial = emptyList())
    val savedChannels by app.database.youtubeChannelDao().observeAll().collectAsState(initial = emptyList())
    val cachedVideos by app.videoCacheManager.items.collectAsState(initial = emptyList())
    var cacheCapacity by remember { mutableStateOf<VideoCacheCapacity?>(null) }
    var videoForCache by remember { mutableStateOf<YouTubeVideo?>(null) }
    var selectedCacheQuality by remember { mutableStateOf(VideoCacheQuality.DEFAULT) }
    var availableCacheQualities by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var loadingCacheQualities by remember { mutableStateOf(false) }
    var cachePreparing by remember { mutableStateOf(false) }
    var cachePreparationJob by remember { mutableStateOf<Job?>(null) }
    var cacheProgress by remember { mutableStateOf<VideoCacheTransferProgress?>(null) }
    var cacheActionError by remember { mutableStateOf<String?>(null) }
    var cacheActionNotice by remember { mutableStateOf<String?>(null) }
    var pendingCachePreparation by remember { mutableStateOf<Pair<YouTubeVideo, Int>?>(null) }
    var notificationPermissionDeclined by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun beginCachePreparation(video: YouTubeVideo, quality: Int, withoutNotificationPermission: Boolean = false) {
        if (cachePreparing) return
        cachePreparing = true
        cacheActionError = null
        cacheActionNotice = if (withoutNotificationPermission) {
            "Preparação iniciada sem permissão de notificações. O Android poderá exibir o serviço apenas no gerenciador de tarefas."
        } else null
        cacheProgress = null
        cachePreparationJob = scope.launch {
            try {
                val source = app.videoCacheSourceRegistry.resolve("youtube", video.videoId, quality)
                    ?: throw IllegalStateException("A fonte não disponibilizou essa qualidade.")
                VideoCacheDownloadService.start(context, source)
                if (!withoutNotificationPermission) {
                    cacheActionNotice = "Preparação iniciada em segundo plano. Você pode sair da tela; o progresso ficará na notificação."
                }
                videoForCache = null
            } catch (cancelled: CancellationException) {
                cacheActionNotice = "Preparação cancelada. O arquivo parcial foi preservado quando já havia transferência iniciada."
                throw cancelled
            } catch (failure: Exception) {
                cacheActionError = failure.message ?: "Não foi possível preparar o vídeo."
            } finally {
                cachePreparing = false
                cachePreparationJob = null
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingCachePreparation
        pendingCachePreparation = null
        if (pending != null) {
            if (granted) {
                beginCachePreparation(pending.first, pending.second)
            } else {
                // POST_NOTIFICATIONS não é requisito para iniciar um foreground service.
                // Sem permissão, o Android pode ocultar a notificação da gaveta, mas
                // mantém a informação do serviço no gerenciador de tarefas.
                notificationPermissionDeclined = true
                beginCachePreparation(pending.first, pending.second, withoutNotificationPermission = true)
            }
        }
    }
    val playbackError by YouTubePlaybackStatus.error.collectAsState()
    LaunchedEffect(cachedVideos) {
        cacheCapacity = runCatching { app.videoCacheManager.capacity() }.getOrNull()
    }
    LaunchedEffect(videoForCache?.videoId) {
        val video = videoForCache
        availableCacheQualities = emptySet()
        if (video != null && app.videoCacheSourceRegistry.hasProvider("youtube")) {
            loadingCacheQualities = true
            try {
                availableCacheQualities = app.videoCacheSourceRegistry.availableQualities("youtube", video.videoId)
                if (selectedCacheQuality !in availableCacheQualities) {
                    selectedCacheQuality = if (VideoCacheQuality.DEFAULT in availableCacheQualities) VideoCacheQuality.DEFAULT else availableCacheQualities.minOrNull() ?: VideoCacheQuality.DEFAULT
                }
            } catch (failure: Exception) {
                cacheActionError = failure.message ?: "Não foi possível consultar as qualidades disponíveis."
            } finally {
                loadingCacheQualities = false
            }
        } else {
            loadingCacheQualities = false
        }
    }
    var query by remember { mutableStateOf("") }
    var activeQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<YouTubeVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var nextPage by remember { mutableStateOf<Page?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedVideoId by remember { mutableStateOf<String?>(null) }
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var isVideoFullscreen by remember { mutableStateOf(false) }
    var showFullscreenRestoreButton by remember { mutableStateOf(true) }
    var audioOnlyMode by remember { mutableStateOf(false) }
    var audioIsPlaying by remember { mutableStateOf(false) }
    var audioPositionMs by remember { mutableStateOf(0L) }
    var audioDurationMs by remember { mutableStateOf(0L) }
    var audioSeeking by remember { mutableStateOf(false) }
    var audioTitle by remember { mutableStateOf("") }
    var audioUploader by remember { mutableStateOf("") }
    var audioThumbnail by remember { mutableStateOf<android.net.Uri?>(null) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf("Início") }
    var selectedChannel by remember { mutableStateOf<YouTubeChannelEntity?>(null) }
    var channelVideos by remember { mutableStateOf<List<YouTubeVideo>>(emptyList()) }
    var channelNextPage by remember { mutableStateOf<Page?>(null) }
    var channelTabHandler by remember { mutableStateOf<ListLinkHandler?>(null) }
    var channelLoading by remember { mutableStateOf(false) }
    var homeVideos by remember { mutableStateOf<List<YouTubeVideo>>(emptyList()) }
    var homeLoading by remember { mutableStateOf(false) }
    var sponsorBlockEnabled by remember {
        mutableStateOf(context.getSharedPreferences(YouTubePlaybackService.PREFERENCES, 0)
            .getBoolean(YouTubePlaybackService.KEY_SPONSOR_BLOCK, false))
    }

    LaunchedEffect(savedChannels) {
        if (savedChannels.isNotEmpty()) {
            homeLoading = true
            val collected = mutableListOf<YouTubeVideo>()
            savedChannels.take(5).forEach { channel ->
                runCatching { repository.loadChannel(channel.channelUrl).videos }
                    .getOrNull()?.let { collected.addAll(it) }
            }
            homeVideos = collected.distinctBy { it.videoId }
            homeLoading = false
        } else {
            homeVideos = emptyList()
        }
    }

    fun openChannel(channelUrl: String, channelName: String, avatarUrl: String?) {
        section = "Canais"
        channelLoading = true
        selectedChannel = YouTubeChannelEntity(channelUrl, channelName, avatarUrl)
        channelVideos = emptyList()
        channelNextPage = null
        channelTabHandler = null
        scope.launch {
            try {
                val page = repository.loadChannel(channelUrl)
                selectedChannel = YouTubeChannelEntity(page.channelUrl, page.channelName, page.avatarUrl)
                channelVideos = page.videos
                channelNextPage = page.nextPage
                channelTabHandler = page.tabHandler
                error = if (page.videos.isEmpty()) "Este canal ainda não retornou vídeos disponíveis." else null
            } catch (failure: Exception) {
                error = failure.message ?: "Não foi possível abrir este canal."
            } finally { channelLoading = false }
        }
    }

    fun addChannel(video: YouTubeVideo) {
        val url = video.channelUrl ?: return
        if (video.channel.isBlank()) return
        scope.launch {
            app.database.youtubeChannelDao().add(YouTubeChannelEntity(url, video.channel, video.channelAvatarUrl))
        }
    }

    if (videoForCache != null) {
        AlertDialog(
            onDismissRequest = { if (!cachePreparing) videoForCache = null },
            title = { Text("Preparar vídeo temporariamente") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(videoForCache?.title.orEmpty())
                    Text("Escolha uma qualidade confirmada pela fonte.")
                    when {
                        loadingCacheQualities -> Text("Consultando qualidades disponíveis…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        availableCacheQualities.isEmpty() -> Text("A fonte não informou qualidades autorizadas para este vídeo.", color = MaterialTheme.colorScheme.error)
                        else -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            availableCacheQualities.sorted().forEach { height ->
                                if (selectedCacheQuality == height) Button(onClick = { selectedCacheQuality = height }) { Text("${height}p") }
                                else OutlinedButton(onClick = { selectedCacheQuality = height }) { Text("${height}p") }
                            }
                        }
                    }
                    cacheProgress?.let { progress ->
                        val fraction = progress.fraction
                        if (fraction != null) {
                            androidx.compose.material3.LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth())
                        } else {
                            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text("${formatBytes(progress.downloadedBytes)} de ${progress.totalBytes?.let(::formatBytes) ?: "tamanho desconhecido"}")
                    }
                    cacheActionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    cacheActionNotice?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    if (!app.videoCacheSourceRegistry.hasProvider("youtube")) {
                        Text("Nenhum provedor autorizado está conectado. A preparação ficará disponível quando uma fonte compatível for integrada.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !cachePreparing && !loadingCacheQualities && selectedCacheQuality in availableCacheQualities && app.videoCacheSourceRegistry.hasProvider("youtube"), onClick = {
                    val video = videoForCache ?: return@TextButton
                    val quality = selectedCacheQuality
                    val permissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (permissionMissing && !notificationPermissionDeclined) {
                        pendingCachePreparation = video to quality
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return@TextButton
                    }
                    beginCachePreparation(video, quality, withoutNotificationPermission = permissionMissing)
                }) { Text(if (cachePreparing) "Preparando..." else "Preparar") }
            },
            dismissButton = {
                if (cachePreparing) {
                    TextButton(onClick = { cachePreparationJob?.cancel() }) { Text("Cancelar preparação") }
                } else {
                    TextButton(onClick = { videoForCache = null }) { Text("Fechar") }
                }
            }
        )
    }
    fun startYouTubePlayback(videoId: String, audioOnly: Boolean = false) {
        error = null
        selectedVideoId = videoId
        audioOnlyMode = audioOnly
        audioIsPlaying = false
        audioSeeking = false
        audioPositionMs = 0L
        audioDurationMs = 0L
        audioTitle = ""
        audioUploader = ""
        audioThumbnail = null
        YouTubePlaybackService.play(context, videoId, audioOnly)
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Limpar histórico?") },
            text = { Text("Todos os vídeos e posições salvas serão removidos deste aparelho.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearHistory = false
                        scope.launch { app.database.youtubeHistoryDao().clear() }
                    }
                ) { Text("Limpar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text("Cancelar") }
            }
        )
    }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, YouTubePlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        var active = true
        future.addListener({
            val connected = runCatching { future.get() }.getOrNull()
            if (active && connected != null) {
                mediaController = connected
                connected.currentMediaItem?.mediaId?.let { selectedVideoId = it }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            active = false
            MediaController.releaseFuture(future)
            mediaController = null
        }
    }

    LaunchedEffect(mediaController, audioOnlyMode) {
        val controller = mediaController ?: return@LaunchedEffect
        while (audioOnlyMode) {
            if (!audioSeeking) {
                audioPositionMs = controller.currentPosition.coerceAtLeast(0L)
                audioDurationMs = controller.duration.takeIf { it > 0L } ?: 0L
                audioIsPlaying = controller.isPlaying
            }
            delay(500)
        }
    }

    DisposableEffect(mediaController) {
        val controller = mediaController
        if (controller == null) {
            onDispose { }
        } else {
            fun syncPlaybackDisplay() {
                val metadata = controller.currentMediaItem?.mediaMetadata
                audioOnlyMode = metadata?.extras?.getBoolean(
                    YouTubePlaybackService.EXTRA_AUDIO_ONLY,
                    false
                ) ?: false
                audioTitle = metadata?.title?.toString().orEmpty()
                audioUploader = metadata?.artist?.toString().orEmpty()
                audioThumbnail = metadata?.artworkUri
                audioIsPlaying = controller.isPlaying
                audioPositionMs = controller.currentPosition.coerceAtLeast(0L)
                audioDurationMs = controller.duration.takeIf { it > 0L } ?: 0L
            }
            val listener = object : Player.Listener {
                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int
                ) {
                    selectedVideoId = mediaItem?.mediaId
                    syncPlaybackDisplay()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    audioIsPlaying = isPlaying
                }

                override fun onPlayerError(playbackError: PlaybackException) {
                    val message = playbackError.toYouTubePlaybackMessage()
                    error = message
                    // Erros de rede encapsulados pelo Media3 recebem orientação clara.
                    YouTubePlaybackStatus.reportError(message)
                }
            }
            syncPlaybackDisplay()
            controller.addListener(listener)
            onDispose { controller.removeListener(listener) }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("YouTube", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Pesquisa e reprodução independentes do login Google.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onBack) { Text("Voltar") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Pular trechos patrocinados", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Ativar vale para o próximo vídeo. Desativar interrompe os pulos atuais.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = sponsorBlockEnabled,
                onCheckedChange = { enabled ->
                    sponsorBlockEnabled = enabled
                    context.getSharedPreferences(YouTubePlaybackService.PREFERENCES, 0)
                        .edit()
                        .putBoolean(YouTubePlaybackService.KEY_SPONSOR_BLOCK, enabled)
                        .apply()
                }
            )
        }

        if (section == "Buscar") OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Pesquisar vídeos") }
        )

        if (section == "Buscar") Button(
            onClick = {
                scope.launch {
                    loading = true
                    error = null
                    nextPage = null
                    results = emptyList()
                    activeQuery = query.trim()
                    try {
                        val page = repository.search(activeQuery)
                        results = page.videos
                        nextPage = page.nextPage
                        if (results.isEmpty()) error = "Nenhum vídeo encontrado."
                    } catch (failure: Exception) {
                        error = failure.message ?: "Não foi possível pesquisar agora."
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = query.isNotBlank() && !loading && !loadingMore,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Pesquisando..." else "Pesquisar")
        }

        if (loading || loadingMore) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        playbackError?.let { message ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                selectedVideoId?.let { failedVideoId ->
                    TextButton(
                        onClick = { startYouTubePlayback(failedVideoId, audioOnly = audioOnlyMode) }
                    ) { Text("Tentar novamente") }
                }
            }
        }
        if (selectedVideoId != null) {
            mediaController?.let { controller ->
                if (audioOnlyMode) {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = audioThumbnail,
                                contentDescription = "Miniatura do áudio",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(88.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text("REPRODUÇÃO SOMENTE ÁUDIO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(audioTitle.ifBlank { "Áudio do YouTube" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(audioUploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (audioIsPlaying) "Reproduzindo" else "Pausado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = if (audioDurationMs > 0L) {
                                        (audioPositionMs.toFloat() / audioDurationMs).coerceIn(0f, 1f)
                                    } else 0f,
                                    onValueChange = { fraction ->
                                        audioSeeking = true
                                        if (audioDurationMs > 0L) {
                                            audioPositionMs = (fraction * audioDurationMs).toLong()
                                        }
                                    },
                                    onValueChangeFinished = {
                                        controller.seekTo(audioPositionMs)
                                        audioSeeking = false
                                    },
                                    enabled = audioDurationMs > 0L
                                )
                                Text(
                                    "${formatDuration(audioPositionMs / 1000)} / ${formatDuration(audioDurationMs / 1000)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        if (audioIsPlaying) controller.pause() else controller.play()
                                    }) {
                                        Text(if (audioIsPlaying) "Pausar" else "Reproduzir")
                                    }
                                    OutlinedButton(onClick = { controller.seekTo(0L) }) {
                                        Text("Reiniciar")
                                    }
                                }
                            }
                        }
                    }
                } else if (!isVideoFullscreen) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(220.dp).padding(bottom = 4.dp)
                    ) {
                        AndroidView(
                            factory = { viewContext -> PlayerView(viewContext).apply { player = controller } },
                            update = { it.player = controller },
                            onRelease = { it.player = null },
                            modifier = Modifier.fillMaxSize()
                        )
                        Button(
                            onClick = { showFullscreenRestoreButton = true; isVideoFullscreen = true },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) {
                            Text("⛶ Tela cheia")
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (section == "Cache") {
                item {
                    Text("Cache temporário", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Os vídeos ficam neste aparelho apenas para assistir. Pausar, minimizar ou sair da tela não apaga o arquivo; a exclusão automática ocorre ao terminar naturalmente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!app.videoCacheSourceRegistry.hasProvider("youtube")) {
                        Text(
                            "A preparação offline do YouTube ainda não está habilitada: falta um provedor que autorize oficialmente o armazenamento local. A reprodução online continua disponível.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        "Itens: ${cacheCapacity?.currentItems ?: cachedVideos.size}/${cacheCapacity?.maxItems ?: app.videoCacheManager.maxItems()} • Ocupado: ${formatBytes(cacheCapacity?.usedBytes ?: cachedVideos.sumOf { it.sizeBytes })}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (cachedVideos.isEmpty()) {
                    item { Text("Nenhum vídeo preparado para assistir offline.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(cachedVideos, key = { it.cacheId }) { cached ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!cached.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = cached.thumbnailUrl,
                                        contentDescription = "Miniatura de ${cached.title}",
                                        modifier = Modifier.size(width = 112.dp, height = 64.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(cached.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${cached.qualityHeight}p • ${formatBytes(cached.sizeBytes)} • ${cacheStatusLabel(cached.status)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        formatDuration(cached.durationMs / 1000L),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (cached.durationMs > 0L && cached.lastPositionMs > 0L &&
                                cached.status in setOf(VideoCacheStatus.PLAYING, VideoCacheStatus.PAUSED)
                            ) {
                                val playbackProgress = (cached.lastPositionMs.toFloat() / cached.durationMs.toFloat()).coerceIn(0f, 1f)
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = playbackProgress,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "${formatDuration(cached.lastPositionMs / 1000L)} / ${formatDuration(cached.durationMs / 1000L)} assistidos",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (cached.status == VideoCacheStatus.READY || cached.status == VideoCacheStatus.PAUSED) {
                                    Button(onClick = { YouTubePlaybackService.playCached(context, cached.cacheId) }) {
                                        Text(if (cached.lastPositionMs > 0L) "Retomar" else "Assistir")
                                    }
                                }
                                if (cached.status == VideoCacheStatus.INTERRUPTED) {
                                    TextButton(
                                        enabled = app.videoCacheSourceRegistry.hasProvider(cached.sourceId),
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    val source = app.videoCacheSourceRegistry.resolve(
                                                        cached.sourceId,
                                                        cached.sourceVideoId,
                                                        cached.qualityHeight
                                                    ) ?: throw IllegalStateException("A fonte não disponibilizou novamente este vídeo.")
                                                    VideoCacheDownloadService.start(context, source)
                                                } catch (failure: Exception) {
                                                    error = failure.message ?: "Não foi possível retomar a preparação."
                                                }
                                            }
                                        }
                                    ) { Text("Retomar preparação") }
                                }
                                TextButton(
                                    enabled = cached.status != VideoCacheStatus.DOWNLOADING &&
                                        cached.status != VideoCacheStatus.QUEUED,
                                    onClick = {
                                        scope.launch {
                                            if (cached.status == VideoCacheStatus.PLAYING) mediaController?.stop()
                                            app.videoCacheManager.removeManually(cached.cacheId)
                                            cacheCapacity = runCatching { app.videoCacheManager.capacity() }.getOrNull()
                                        }
                                    }
                                ) { Text("Apagar") }
                            }
                        }
                    }
                }
            }
            if (section == "Início") {
                item { Text("Sua seleção", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                if (savedChannels.isEmpty()) item { Text("Quando encontrar um canal de que goste, toque em + Canal para guardá-lo aqui.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else {
                    item { Text("Seus canais", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(savedChannels, key = { it.channelUrl }) { channel ->
                        SavedChannelRow(channel, { openChannel(channel.channelUrl, channel.channelName, channel.avatarUrl) }, { scope.launch { app.database.youtubeChannelDao().remove(channel.channelUrl) } })
                    }
                }
                if (homeLoading) item { CircularProgressIndicator() }
                if (homeVideos.isNotEmpty()) {
                    item { Text("Novidades dos seus canais", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(homeVideos, key = { "followed-" + it.videoId }) { video ->
                        VideoResultRow(video, { startYouTubePlayback(video.videoId) }, { startYouTubePlayback(video.videoId, true) }, {}, {
                            video.channelUrl?.let { openChannel(it, video.channel, video.channelAvatarUrl) }
                        }, if (app.videoCacheSourceRegistry.hasProvider("youtube")) ({ videoForCache = video; cacheActionError = null; cacheActionNotice = null; cacheProgress = null }) else null)
                    }
                } else if (results.isNotEmpty()) item { Text("Vídeos da última pesquisa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            }
            if (section == "Buscar" && results.isNotEmpty()) {
                item { Text("Resultados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            }
            if (section == "Buscar" || section == "Início") items(results, key = { it.videoId }) { video ->
                VideoResultRow(video, { startYouTubePlayback(video.videoId) }, { startYouTubePlayback(video.videoId, true) }, { addChannel(video) }, {
                    video.channelUrl?.let { openChannel(it, video.channel, video.channelAvatarUrl) }
                }, if (app.videoCacheSourceRegistry.hasProvider("youtube")) ({ videoForCache = video; cacheActionError = null; cacheProgress = null }) else null)
            }
            if (section == "Canais") {
                item { Text(selectedChannel?.channelName ?: "Seus canais", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                if (selectedChannel == null) {
                    if (savedChannels.isEmpty()) item { Text("Sua coleção está vazia. Pesquise vídeos e adicione os canais que quiser acompanhar.") }
                    items(savedChannels, key = { it.channelUrl }) { channel ->
                        SavedChannelRow(channel, { openChannel(channel.channelUrl, channel.channelName, channel.avatarUrl) }, { scope.launch { app.database.youtubeChannelDao().remove(channel.channelUrl) } })
                    }
                } else {
                    item { OutlinedButton(onClick = { selectedChannel = null; channelVideos = emptyList() }) { Text("Voltar aos canais") } }
                    if (channelLoading) item { CircularProgressIndicator() }
                    items(channelVideos, key = { it.videoId }) { video ->
                        VideoResultRow(video, { startYouTubePlayback(video.videoId) }, { startYouTubePlayback(video.videoId, true) }, {}, {}, if (app.videoCacheSourceRegistry.hasProvider("youtube")) ({ videoForCache = video; cacheActionError = null; cacheProgress = null }) else null)
                    }
                    if (channelNextPage != null && channelTabHandler != null) item {
                        OutlinedButton(onClick = {
                            val cursor = channelNextPage ?: return@OutlinedButton
                            val handler = channelTabHandler ?: return@OutlinedButton
                            scope.launch {
                                channelLoading = true
                                try {
                                    val page = repository.loadMoreChannel(handler, cursor)
                                    channelVideos = (channelVideos + page.videos).distinctBy { it.videoId }
                                    channelNextPage = page.nextPage
                                } catch (failure: Exception) { error = failure.message ?: "Não foi possível carregar mais vídeos." }
                                finally { channelLoading = false }
                            }
                        }, enabled = !channelLoading, modifier = Modifier.fillMaxWidth()) { Text("Carregar mais vídeos") }
                    }
                }
            }
            if ((section == "Buscar" || section == "Início") && nextPage != null) {
                item {
                    OutlinedButton(
                        onClick = {
                            val cursor = nextPage ?: return@OutlinedButton
                            scope.launch {
                                loadingMore = true
                                error = null
                                try {
                                    val page = repository.loadMore(activeQuery, cursor)
                                    results = (results + page.videos).distinctBy { it.videoId }
                                    nextPage = page.nextPage
                                } catch (failure: Exception) {
                                    error = failure.message ?: "Não foi possível carregar mais resultados."
                                } finally {
                                    loadingMore = false
                                }
                            }
                        },
                        enabled = !loading && !loadingMore,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (loadingMore) "Carregando..." else "Carregar mais") }
                }
            }
            if (section == "Biblioteca" && history.isEmpty()) item { Text("Seu histórico ainda está vazio.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (section == "Biblioteca" && history.isNotEmpty()) {
                item { Text("Seu histórico", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(history, key = { it.videoId }) { historyItem ->
                    HistoryRow(
                        item = historyItem,
                        onPlay = { startYouTubePlayback(historyItem.videoId) },
                        onListen = { startYouTubePlayback(historyItem.videoId, audioOnly = true) },
                        onDelete = {
                            scope.launch { app.database.youtubeHistoryDao().delete(historyItem.videoId) }
                        }
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { confirmClearHistory = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Limpar histórico") }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Início", "Buscar", "Canais", "Biblioteca", "Cache").forEach { destination ->
                TextButton(onClick = { section = destination; selectedChannel = null }) { Text(destination, fontWeight = if (section == destination) FontWeight.Bold else FontWeight.Normal) }
            }
        }
    }

    if (isVideoFullscreen) {
        Dialog(
            onDismissRequest = { isVideoFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val dialogView = LocalView.current
            val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
            DisposableEffect(dialogWindow) {
                if (dialogWindow != null) {
                    val insetsController = WindowInsetsControllerCompat(
                        dialogWindow,
                        dialogWindow.decorView
                    )
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                onDispose {
                    if (dialogWindow != null) {
                        WindowInsetsControllerCompat(
                            dialogWindow,
                            dialogWindow.decorView
                        ).show(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black)
            ) {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            player = mediaController
                            useController = true
                            controllerShowTimeoutMs = 2_000
                            setControllerVisibilityListener(
                                PlayerView.ControllerVisibilityListener { visibility ->
                                    showFullscreenRestoreButton = visibility == View.VISIBLE
                                }
                            )
                            showController()
                        }
                    },
                    update = {
                        it.player = mediaController
                        it.controllerShowTimeoutMs = 2_000
                    },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize()
                )
                if (showFullscreenRestoreButton) {
                    Button(
                        onClick = { isVideoFullscreen = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    ) {
                        Text("Restaurar")
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoResultRow(video: YouTubeVideo, onPlay: () -> Unit, onListen: () -> Unit, onAddChannel: () -> Unit, onOpenChannel: () -> Unit, onPrepareCache: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            AsyncImage(model = video.thumbnailUrl, contentDescription = "Miniatura de " + video.title,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Text(video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(video.channel.ifBlank { "Canal não informado" } + " • " + formatDuration(video.durationSeconds),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Button(onClick = onPlay) { Text("Assistir") }
                if (onPrepareCache != null) TextButton(onClick = onPrepareCache) { Text("Preparar offline") }
                TextButton(onClick = onListen) { Text("Só áudio") }
                if (video.channelUrl != null) TextButton(onClick = onAddChannel) { Text("+ Canal") }
                if (video.channelUrl != null) TextButton(onClick = onOpenChannel) { Text("Ver canal") }
            }
        }
    }
}

@Composable
private fun SavedChannelRow(channel: YouTubeChannelEntity, onOpen: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(model = channel.avatarUrl, contentDescription = "Imagem do canal " + channel.channelName,
                contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.channelName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Canal salvo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onOpen) { Text("Abrir") }
            TextButton(onClick = onRemove) { Text("Remover") }
        }
    }
}

@Composable
private fun HistoryRow(item: YouTubeHistoryEntity, onPlay: () -> Unit, onListen: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = "Miniatura de " + item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 104.dp, height = 64.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(item.uploader.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Reproduzido " + item.watchCount + (if (item.watchCount == 1) " vez" else " vezes"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.lastPositionMs > 0) {
                    Text("Retomar em " + formatDuration(item.lastPositionMs / 1000), style = MaterialTheme.typography.labelSmall)
                }
                Text(formatWatchedAt(item.watchedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    TextButton(onClick = onPlay) { Text(if (item.lastPositionMs > 0) "Retomar" else "Assistir") }
                    TextButton(onClick = onListen) { Text("Ouvir") }
                    TextButton(onClick = onDelete) { Text("Excluir") }
                }
            }
        }
    }
}

private fun formatWatchedAt(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(Locale.getDefault(), bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(Locale.getDefault(), bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0f KB".format(Locale.getDefault(), bytes / 1024.0)
    else -> "$bytes B"
}

private fun cacheStatusLabel(status: String): String = when (status) {
    VideoCacheStatus.QUEUED -> "Na fila"
    VideoCacheStatus.DOWNLOADING -> "Preparando"
    VideoCacheStatus.INTERRUPTED -> "Interrompido"
    VideoCacheStatus.READY -> "Pronto"
    VideoCacheStatus.PLAYING -> "Assistindo"
    VideoCacheStatus.PAUSED -> "Pausado"
    VideoCacheStatus.COMPLETED -> "Concluído"
    VideoCacheStatus.REMOVED -> "Removido"
    else -> status
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "Duração indisponível"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%d:%02d".format(minutes, remainder)
}
