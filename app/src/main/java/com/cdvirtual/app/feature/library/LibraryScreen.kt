package com.cdvirtual.app.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdvirtual.app.core.common.formatDuration
import com.cdvirtual.app.core.common.formatSize
import com.cdvirtual.app.core.common.rememberNotificationGate
import com.cdvirtual.app.core.database.TrackEntity
import com.cdvirtual.app.core.storage.StorageAccess
import com.cdvirtual.app.core.ui.ButtonFlow
import com.cdvirtual.app.core.ui.ScreenScaffold
import com.cdvirtual.app.feature.export.GeneratedCd
import com.cdvirtual.app.feature.export.GeneratedCds
import com.cdvirtual.app.media.metadata.Id3Chapter
import com.cdvirtual.app.media.metadata.Id3Reader
import com.cdvirtual.app.media.playback.Mp3Player
import com.cdvirtual.app.media.service.ExportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LIBRARY_PREFS = "mixtape_browser"
private const val LIBRARY_FORMAT_KEY = "preferred_audio_format"
private const val LIBRARY_FORMAT_CONFIGURED_KEY = "audio_format_configured"
private val libraryFormatChoices = listOf(
    "BEST" to "Melhor qualidade disponível",
    "webm" to "WebM / Opus",
    "m4a" to "M4A / AAC"
)

@Composable
fun LibraryScreen(onBack: () -> Unit, vm: LibraryViewModel = viewModel()) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val failures by vm.analysisFailures.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val audioPreferences = remember { context.getSharedPreferences(LIBRARY_PREFS, android.content.Context.MODE_PRIVATE) }
    var preferredFormat by remember { mutableStateOf(audioPreferences.getString(LIBRARY_FORMAT_KEY, "BEST") ?: "BEST") }
    var showQualityDialog by remember { mutableStateOf(false) }
    val withNotifications = rememberNotificationGate()
    val player by Mp3Player.state.collectAsStateWithLifecycle()
    var position by remember(player.trackId) { mutableFloatStateOf(0f) }
    var dragging by remember(player.trackId) { mutableStateOf(false) }
    LaunchedEffect(player.trackId, player.playing) {
        while (player.trackId != null) {
            if (!dragging) position = Mp3Player.positionMs().toFloat()
            kotlinx.coroutines.delay(400)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.import(it) }

    // As playlists são os MP3 unificados gerados; a lista de músicas vem do próprio arquivo (capítulos),
    // então continua completa mesmo se os arquivos originais forem apagados depois de gerar.
    val exportStatus by ExportState.status.collectAsState()
    var playlists by remember { mutableStateOf(emptyList<GeneratedCd>()) }
    LaunchedEffect(exportStatus) { playlists = withContext(Dispatchers.IO) { GeneratedCds.list(context) } }

    var tab by remember { mutableStateOf(0) }
    var editingGenre by remember { mutableStateOf<TrackEntity?>(null) }
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    val groups = remember(tracks) { Genres.group(tracks) }

    ScreenScaffold("Biblioteca", onBack) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Músicas (${tracks.size})", maxLines = 1) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Playlists (${playlists.size})", maxLines = 1) })
        }
        if (tab == 0) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { picker.launch(StorageAccess.AUDIO_MIME_TYPES) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Importar músicas")
                        }
                        OutlinedButton(onClick = { showQualityDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Qualidade padrão dos downloads: " + (libraryFormatChoices.firstOrNull { it.first == preferredFormat }?.second ?: "Melhor qualidade"))
                        }
                        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        OutlinedTextField(
                            value = query, onValueChange = { vm.query.value = it }, singleLine = true,
                            label = { Text("Buscar por título, artista, álbum ou gênero") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Button(onClick = { withNotifications { Mp3Player.playLibrary(context, tracks, 0, false) } }, enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f)) {
                                Text("▶ Reproduzir tudo")
                            }
                            OutlinedButton(onClick = { withNotifications { Mp3Player.playLibrary(context, tracks, 0, true) } }, enabled = tracks.isNotEmpty(), modifier = Modifier.padding(start = 8.dp)) {
                                Text("🔀 Aleatório")
                            }
                        }
                        if (player.trackId != null) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(player.trackTitle ?: "Tocando da biblioteca", style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(if (player.loading) "Carregando…" else if (player.playing) "Reproduzindo" else "Pausado",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { Mp3Player.previousTrack() }) { Text("⏮") }
                                Button(onClick = { Mp3Player.togglePlayPause() }) { Text(if (player.playing) "Ⅱ" else "▶") }
                                TextButton(onClick = { Mp3Player.nextTrack() }) { Text("⏭") }
                            }
                            if (player.durationMs > 0L) {
                                Slider(
                                    value = position.coerceIn(0f, player.durationMs.toFloat()),
                                    onValueChange = { position = it; dragging = true },
                                    onValueChangeFinished = { Mp3Player.seekTo(position.toLong()); dragging = false },
                                    valueRange = 0f..player.durationMs.toFloat()
                                )
                                Text("${formatDuration(position.toLong())} / ${formatDuration(player.durationMs)}",
                                    style = MaterialTheme.typography.bodySmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🔈", style = MaterialTheme.typography.bodyMedium)
                                    Slider(value = player.volume, onValueChange = Mp3Player::setVolume, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                                    Text("${(player.volume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                if (tracks.isEmpty()) {
                    item {
                        Text(
                            if (query.isBlank()) "Nenhuma música na biblioteca. Toque em \"Importar músicas\"." else "Nenhuma música encontrada para a busca.",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
                groups.forEach { (genre, list) ->
                    val key = genre.lowercase()
                    val open = key !in collapsed || query.isNotBlank()
                    item(key = "genre:$key") {
                        Row(
                            Modifier.fillMaxWidth().clickable { collapsed = if (key in collapsed) collapsed - key else collapsed + key }
                                .padding(top = 16.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "$genre (${list.size})", style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(if (open) "▾" else "▸", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (open) {
                        items(list, key = { it.id }) { t ->
                            TrackRow(
                                t, failures[t.id],
                                onPlay = { withNotifications { Mp3Player.playLibrary(context, tracks, tracks.indexOfFirst { it.id == t.id }, false) } },
                                onRetry = { vm.retryAnalysis(t.id) },
                                onGenre = { editingGenre = t },
                                onDelete = { vm.remove(t) }
                            )
                        }
                    }
                }
            }
        } else {
            PlaylistsTab(playlists)
        }
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Qualidade padrão dos downloads") },
            text = {
                Column {
                    libraryFormatChoices.forEach { (value, label) ->
                        TextButton(
                            onClick = {
                                preferredFormat = value
                                audioPreferences.edit()
                                    .putString(LIBRARY_FORMAT_KEY, value)
                                    .putBoolean(LIBRARY_FORMAT_CONFIGURED_KEY, true)
                                    .apply()
                                showQualityDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (preferredFormat == value) "✓  $label" else label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) { Text("Fechar") }
            }
        )
    }

    editingGenre?.let { t ->
        var text by remember(t.id) { mutableStateOf(t.genre.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingGenre = null },
            title = { Text("Gênero da música") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    OutlinedTextField(
                        value = text, onValueChange = { text = it.take(40) }, singleLine = true,
                        label = { Text("Gênero (ex.: Funk)") }, modifier = Modifier.fillMaxWidth()
                    )
                    Text("Deixe vazio para tirar o gênero. Isso muda só o app, não o arquivo.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { vm.setGenre(t, text); editingGenre = null }) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { editingGenre = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun TrackRow(t: TrackEntity, failure: String?, onPlay: () -> Unit, onRetry: () -> Unit, onGenre: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverImage(t.coverPath, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    t.artist, t.album, formatDuration(t.durationMs), t.format,
                    t.sizeBytes.takeIf { it > 0L }?.let { formatSize(it) }, estimatedBitrate(t),
                    t.lufs?.let { "%.1f LUFS".format(it) }, t.truePeakDb?.let { "%.1f dBTP".format(it) }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            if (t.lufs == null && failure != null) {
                Text("Falha na análise: $failure", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Tentar de novo") }
            } else Text(analysisText(t), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onPlay) { Text("▶") }
        Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = onGenre) { Text("Gênero") }
            TextButton(onClick = onDelete) { Text("Excluir") }
        }
    }
}

@Composable
private fun PlaylistsTab(playlists: List<GeneratedCd>) {
    val context = LocalContext.current
    val withNotifications = rememberNotificationGate()
    val player by Mp3Player.state.collectAsState()
    var expanded by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (playlists.isEmpty()) {
            item {
                Text(
                    "Nenhuma playlist ainda. Monte uma em \"Playlists\" e toque em \"Gerar playlist\".",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        items(playlists, key = { it.file.absolutePath }) { cd ->
            val isOpen = expanded == cd.file.absolutePath
            val chapters by produceChapters(cd, isOpen)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { expanded = if (isOpen) null else cd.file.absolutePath },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(cd.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    "${cd.trackCount} música(s)", cd.durationMs?.let { formatDuration(it) }, formatSize(cd.sizeBytes)
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(if (isOpen) "▾" else "▸", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    if (isOpen) {
                        ButtonFlow {
                            val playingThis = player.file == cd.file
                            Button(onClick = { withNotifications { if (playingThis) Mp3Player.togglePlayPause() else Mp3Player.play(context, cd.file) } }) {
                                Text(if (playingThis && player.playing) "Pausar" else "Tocar playlist")
                            }
                        }
                        val list = chapters
                        when {
                            list == null -> Text("Carregando músicas…", style = MaterialTheme.typography.bodySmall)
                            list.isEmpty() -> Text("Este MP3 não tem a lista de músicas marcada.", style = MaterialTheme.typography.bodySmall)
                            else -> list.forEachIndexed { i, c ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { withNotifications { Mp3Player.play(context, cd.file, c.startMs) } }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${i + 1}. ${c.title.ifBlank { "Música ${i + 1}" }}",
                                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                                        maxLines = 2, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(formatDuration(c.startMs), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Lê os capítulos (músicas) do MP3 fora da thread principal, só quando a playlist é aberta. */
@Composable
private fun produceChapters(cd: GeneratedCd, open: Boolean): androidx.compose.runtime.State<List<Id3Chapter>?> =
    produceState<List<Id3Chapter>?>(initialValue = null, cd.file, open) {
        if (open && value == null) value = withContext(Dispatchers.IO) { Id3Reader.readChapters(cd.file) }
    }

private fun analysisText(t: TrackEntity): String =
    if (t.lufs == null) "Analisando volume…" else "%.1f LUFS · pico real %.1f dBTP".format(t.lufs, t.truePeakDb)

private fun estimatedBitrate(track: TrackEntity): String? {
    if (track.durationMs <= 0L || track.sizeBytes <= 0L) return null
    val kbps = (track.sizeBytes * 8.0 / track.durationMs).toInt()
    return "≈$kbps kbps"
}
