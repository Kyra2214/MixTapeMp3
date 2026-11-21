package com.cdvirtual.app.feature.youtube

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cdvirtual.app.media.youtube.YouTubeSearchRepository
import com.cdvirtual.app.media.youtube.YouTubeVideo
import kotlinx.coroutines.launch

@Composable
fun YouTubeScreen(onBack: () -> Unit) {
    val repository = remember { YouTubeSearchRepository() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<YouTubeVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("YouTube", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Pesquisa independente, sem depender do login Google.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onBack) { Text("Voltar") }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Pesquisar vídeos") }
        }

        Button(
            onClick = {
                scope.launch {
                    loading = true
                    error = null
                    try {
                        results = repository.search(query)
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

        if (loading) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results, key = { it.videoId }) { video ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(video.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(video.channel.ifBlank { "Canal não informado" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDuration(video.durationSeconds), style = MaterialTheme.typography.labelSmall)
                    }
                }
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
