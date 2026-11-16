package com.cdvirtual.app.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdvirtual.app.core.common.formatDuration
import com.cdvirtual.app.core.common.formatSize
import com.cdvirtual.app.core.database.TrackEntity
import com.cdvirtual.app.core.storage.StorageAccess

@Composable
fun LibraryScreen(vm: LibraryViewModel = viewModel()) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val failures by vm.analysisFailures.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.import(it) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { picker.launch(StorageAccess.AUDIO_MIME_TYPES) }, modifier = Modifier.fillMaxWidth()) {
            Text("Importar músicas")
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        OutlinedTextField(
            value = query, onValueChange = { vm.query.value = it }, singleLine = true,
            label = { Text("Buscar por título, artista ou álbum") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(tracks, key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverImage(t.coverPath, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(t.artist, t.album, formatDuration(t.durationMs), t.format, t.sizeBytes.takeIf { it > 0L }?.let { formatSize(it) }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall
                        )
                        val failure = failures[t.id]
                        if (t.lufs == null && failure != null) {
                            Text("Falha na análise: $failure", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { vm.retryAnalysis(t.id) }) { Text("Tentar de novo") }
                        } else Text(analysisText(t), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { vm.remove(t) }) { Text("Excluir") }
                }
            }
        }
    }
}

private fun analysisText(t: TrackEntity): String =
    if (t.lufs == null) "Analisando volume…" else "%.1f LUFS · pico real %.1f dBTP".format(t.lufs, t.truePeakDb)