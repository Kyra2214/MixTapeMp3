package com.cdvirtual.app.feature.youtube

import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import org.schabi.newpipe.extractor.Page
import com.cdvirtual.app.CDVirtualApp
import com.cdvirtual.app.core.database.YouTubeHistoryEntity
import com.cdvirtual.app.media.youtube.YouTubePlaybackService
import com.cdvirtual.app.media.youtube.YouTubeSearchRepository
import com.cdvirtual.app.media.youtube.YouTubeVideo
import kotlinx.coroutines.launch

@Composable
fun YouTubeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CDVirtualApp
    val repository = remember { YouTubeSearchRepository() }
    val history by app.database.youtubeHistoryDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<YouTubeVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var nextPage by remember { mutableStateOf<Page?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedVideoId by remember { mutableStateOf<String?>(null) }
    var mediaController by remember { mutableStateOf<MediaController?>(null) }

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
                    try {
                        val page = repository.search(query)
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
            enabled = query.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Pesquisando..." else "Pesquisar")
        }

        if (loading || loadingMore) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (selectedVideoId != null) {
            mediaController?.let { controller ->
                AndroidView(
                    factory = { viewContext -> PlayerView(viewContext).apply { player = controller } },
                    update = { it.player = controller },
                    modifier = Modifier.fillMaxWidth().height(220.dp).padding(bottom = 4.dp)
                )
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
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(video.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(video.channel.ifBlank { "Canal não informado" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDuration(video.durationSeconds), style = MaterialTheme.typography.labelSmall)
                        Button(onClick = {
                            selectedVideoId = video.videoId
                            YouTubePlaybackService.play(context, video.videoId)
                        }) { Text("Reproduzir") }
                    }
                }
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
                                    val page = repository.loadMore(query, cursor)
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
                        onDelete = {
                            scope.launch { app.database.youtubeHistoryDao().delete(historyItem.videoId) }
                        }
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { scope.launch { app.database.youtubeHistoryDao().clear() } },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Limpar histórico") }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: YouTubeHistoryEntity, onPlay: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(item.uploader.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.lastPositionMs > 0) {
                    Text("Retomar em " + formatDuration(item.lastPositionMs / 1000), style = MaterialTheme.typography.labelSmall)
                }
            }
            Column {
                TextButton(onClick = onPlay) { Text("Retomar") }
                TextButton(onClick = onDelete) { Text("Excluir") }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "Duração indisponível"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%d:%02d".format(minutes, remainder)
}
