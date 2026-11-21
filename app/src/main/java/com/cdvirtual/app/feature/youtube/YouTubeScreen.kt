package com.cdvirtual.app.feature.youtube

import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.cdvirtual.app.CDVirtualApp
import com.cdvirtual.app.core.database.YouTubeHistoryEntity
import com.cdvirtual.app.media.youtube.YouTubePlaybackService
import com.cdvirtual.app.media.youtube.YouTubePlaybackStatus
import com.cdvirtual.app.media.youtube.YouTubeSearchRepository
import com.cdvirtual.app.media.youtube.YouTubeVideo
import kotlinx.coroutines.launch
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
    val playbackError by YouTubePlaybackStatus.error.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var activeQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<YouTubeVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var nextPage by remember { mutableStateOf<Page?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedVideoId by remember { mutableStateOf<String?>(null) }
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var audioOnlyMode by remember { mutableStateOf(false) }
    var audioTitle by remember { mutableStateOf("") }
    var audioUploader by remember { mutableStateOf("") }
    var audioThumbnail by remember { mutableStateOf<android.net.Uri?>(null) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var sponsorBlockEnabled by remember {
        mutableStateOf(context.getSharedPreferences(YouTubePlaybackService.PREFERENCES, 0)
            .getBoolean(YouTubePlaybackService.KEY_SPONSOR_BLOCK, false))
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

    DisposableEffect(selectedVideoId) {
        if (selectedVideoId == null) {
            onDispose { mediaController = null }
        } else {
            val token = SessionToken(context, ComponentName(context, YouTubePlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({
                mediaController = runCatching { future.get() }.getOrNull()
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                MediaController.releaseFuture(future)
                mediaController = null
            }
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
            }
            val listener = object : Player.Listener {
                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int
                ) {
                    syncPlaybackDisplay()
                }

                override fun onPlayerError(playbackError: PlaybackException) {
                    error = "Falha na reprodução: " + playbackError.errorCodeName
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

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Pesquisar vídeos") }
        )

        Button(
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
        playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
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
                                    if (controller.isPlaying) "Reproduzindo em segundo plano" else "Áudio pronto para reproduzir",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    AndroidView(
                        factory = { viewContext -> PlayerView(viewContext).apply { player = controller } },
                        update = { it.player = controller },
                        modifier = Modifier.fillMaxWidth().height(220.dp).padding(bottom = 4.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (results.isNotEmpty()) {
                item { Text("Resultados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            }
            items(results, key = { it.videoId }) { video ->
                VideoResultRow(
                    video = video,
                    onPlay = {
                        selectedVideoId = video.videoId
                        YouTubePlaybackService.play(context, video.videoId)
                    },
                    onListen = {
                        selectedVideoId = video.videoId
                        YouTubePlaybackService.play(context, video.videoId, audioOnly = true)
                    }
                )
            }
            if (nextPage != null) {
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
            if (history.isNotEmpty()) {
                item { Text("Seu histórico", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(history, key = { it.videoId }) { historyItem ->
                    HistoryRow(
                        item = historyItem,
                        onPlay = {
                            selectedVideoId = historyItem.videoId
                            YouTubePlaybackService.play(context, historyItem.videoId)
                        },
                        onListen = {
                            selectedVideoId = historyItem.videoId
                            YouTubePlaybackService.play(context, historyItem.videoId, audioOnly = true)
                        },
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
    }
}

@Composable
private fun VideoResultRow(video: YouTubeVideo, onPlay: () -> Unit, onListen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = "Miniatura de " + video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 116.dp, height = 72.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(video.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(video.channel.ifBlank { "Canal não informado" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(video.durationSeconds), style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = onPlay) { Text("Assistir") }
                    TextButton(onClick = onListen) { Text("Só áudio") }
                }
            }
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
                    TextButton(onClick = onPlay) { Text("Assistir") }
                    TextButton(onClick = onListen) { Text("Ouvir") }
                    TextButton(onClick = onDelete) { Text("Excluir") }
                }
            }
        }
    }
}

private fun formatWatchedAt(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "Duração indisponível"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%d:%02d".format(minutes, remainder)
}
