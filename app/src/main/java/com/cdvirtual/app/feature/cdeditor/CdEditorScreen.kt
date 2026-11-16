package com.cdvirtual.app.feature.cdeditor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.cdvirtual.app.media.service.ExportService
import com.cdvirtual.app.media.service.ExportState
import com.cdvirtual.app.media.service.ExportStatus
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdvirtual.app.core.common.formatDuration
import com.cdvirtual.app.core.common.rememberNotificationGate
import com.cdvirtual.app.core.common.formatSize
import com.cdvirtual.app.core.database.CdEntity
import com.cdvirtual.app.core.ui.ButtonFlow
import com.cdvirtual.app.core.ui.ScreenScaffold
import com.cdvirtual.app.feature.library.CoverImage
import androidx.compose.ui.text.style.TextOverflow
import com.cdvirtual.app.media.metadata.CueSettings
import com.cdvirtual.app.media.equalization.CdAudioSettings
import com.cdvirtual.app.media.equalization.CdAudioSettingsStore

private const val FAT32_MAX_BYTES = 4L * 1024 * 1024 * 1024 - 1

@Composable
fun CdEditorScreen(onBack: () -> Unit, vm: CdEditorViewModel = viewModel()) {
    ScreenScaffold("Playlists", onBack) { CdEditorContent(vm) }
}

@Composable
private fun CdEditorContent(vm: CdEditorViewModel) {
    val cds by vm.cds.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val selected = cds.firstOrNull { it.id == selectedId }
    // T-501: uma única lista rolável (antes, Column fixa + LazyColumn: com CD longo os botões e a lista de CDs ficavam inacessíveis).
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            // Vários elementos num mesmo item precisam de uma Column; sem ela eles ficam um sobre o outro.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Minhas playlists", style = MaterialTheme.typography.headlineSmall)
                Text("Organize suas faixas e monte a sequência perfeita.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = vm::createCd) { Text("＋  Nova playlist") }
                if (selected != null) CdDetails(selected, vm)
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (cds.isEmpty()) Text("Nenhuma playlist ainda. Toque em \"Nova playlist\" para começar.")
                else Text("Suas playlists (${cds.size})", style = MaterialTheme.typography.titleSmall)
            }
        }
            items(cds, key = { it.id }) { cd ->
                val isSel = cd.id == selectedId
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { vm.select(cd.id) },
                    colors = if (isSel) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    else CardDefaults.cardColors()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverImage(cd.coverPath, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(cd.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text((if (isSel) "Playlist atual · " else "") + "atualizado em " + formatDate(cd.updatedAt), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
    }
}

@Composable
private fun CdDetails(cd: CdEntity, vm: CdEditorViewModel) {
    var name by remember(cd.id, cd.name) { mutableStateOf(cd.name) }
    var artist by remember(cd.id, cd.artist) { mutableStateOf(cd.artist.orEmpty()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.setCover(cd.id, uri)
    }
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverImage(cd.coverPath, Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)))
                Column(Modifier.padding(start = 12.dp)) {
                    OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }) { Text("Escolher capa") }
                    if (cd.coverPath != null) {
                        OutlinedButton(onClick = { vm.removeCover(cd.id) }, Modifier.padding(top = 4.dp)) { Text("Remover capa") }
                    }
                }
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(CdRepository.MAX_NAME) }, label = { Text("Nome da playlist") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Button(onClick = { vm.rename(cd.id, name) }, Modifier.padding(top = 4.dp), enabled = name.trim() != cd.name) {
                Text("Salvar nome")
            }
            OutlinedTextField(
                value = artist, onValueChange = { artist = it.take(CdRepository.MAX_NAME) }, label = { Text("Artista (opcional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Button(onClick = { vm.setArtist(cd.id, artist) }, Modifier.padding(top = 4.dp), enabled = artist.trim() != cd.artist.orEmpty()) {
                Text("Salvar artista")
            }
            val cdTracks by vm.cdTracks.collectAsStateWithLifecycle()
            val export by ExportState.status.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val withNotifications = rememberNotificationGate() // T-503: gera mesmo se a notificação for negada
            var picking by remember { mutableStateOf(false) }
            Text("Músicas (${cdTracks.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            if (cdTracks.isEmpty()) Text("Esta playlist ainda não tem músicas.", style = MaterialTheme.typography.bodySmall)
            else Text("Duração total: ${formatDuration(CdStats.totalDurationMs(cdTracks))} · " +
                "MP3 estimado (${CdStats.MP3_KBPS} kbps): ${formatSize(CdStats.estimatedSizeBytes(CdStats.totalDurationMs(cdTracks)))}",
                style = MaterialTheme.typography.bodySmall)
            if (CdStats.estimatedSizeBytes(CdStats.totalDurationMs(cdTracks)) > FAT32_MAX_BYTES) {
                // 10.3: aviso antecipado; o app gera o arquivo normalmente, mas cartões FAT32 não aceitam >= 4 GiB.
                Text("Esta playlist passa de 4 GB: ao exportar, a pasta de destino não pode ser FAT32 (alguns cartões SD).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            cdTracks.forEachIndexed { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. ${t.title} (${formatDuration(t.durationMs)})", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { vm.moveTrack(cd.id, t.id, -1) }, enabled = i > 0) { Text("↑") }
                    TextButton(onClick = { vm.moveTrack(cd.id, t.id, 1) }, enabled = i < cdTracks.lastIndex) { Text("↓") }
                    TextButton(onClick = { vm.removeTrack(cd.id, t.id) }) { Text("✕") }
                }
            }
            ButtonFlow(Modifier.padding(top = 4.dp)) {
                OutlinedButton(onClick = { picking = true }) { Text("Adicionar músicas") }
                Button(onClick = { vm.saveProject(cd.id) }) { Text("Salvar projeto") }
            }
            var confirmDelete by remember(cd.id) { mutableStateOf(false) }
            OutlinedButton(onClick = { confirmDelete = true }, Modifier.padding(top = 4.dp)) { Text("Excluir playlist") }
            if (confirmDelete) AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Excluir \"${cd.name}\"?") },
                text = { Text("Remove só este projeto de playlist. As músicas da biblioteca, os arquivos originais e os MP3 já gerados não são apagados.") },
                confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteCd(cd.id) }) { Text("Excluir") } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
            )
            CdAudioOptions(cd.id, context)
            val busy = export is ExportStatus.Running
            var confirmGenerate by remember(cd.id) { mutableStateOf(false) }
            var deleteOriginals by remember(cd.id) { mutableStateOf(false) }
            Button(
                onClick = { deleteOriginals = false; confirmGenerate = true },
                enabled = cdTracks.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) { Text("Gerar playlist (MP3 único)") }
            if (confirmGenerate) AlertDialog(
                onDismissRequest = { confirmGenerate = false },
                title = { Text("Gerar playlist") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("As ${cdTracks.size} música(s) serão unidas em um único MP3 e salvas na pasta de destino escolhida (se houver uma).")
                        Row(
                            Modifier.fillMaxWidth().clickable { deleteOriginals = !deleteOriginals },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = deleteOriginals, onCheckedChange = { deleteOriginals = it })
                            Text("Apagar os arquivos originais depois de gerar e manter só a playlist unificada", Modifier.weight(1f))
                        }
                        if (deleteOriginals) {
                            Text(
                                "Atenção: os arquivos das músicas serão apagados do aparelho e saem da biblioteca; esta playlist não poderá ser gerada de novo. " +
                                    "Só acontece depois que o MP3 estiver pronto e salvo. Em alguns arquivos o Android não permite apagar; nesse caso o app avisa.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmGenerate = false
                        val del = deleteOriginals
                        withNotifications { ExportService.start(context, cd.id, del) }
                    }) { Text("Gerar") }
                },
                dismissButton = { TextButton(onClick = { confirmGenerate = false }) { Text("Cancelar") } }
            )
            var writeCue by remember { mutableStateOf(CueSettings.isEnabled(context)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = writeCue, onCheckedChange = { writeCue = it; CueSettings.setEnabled(context, it) })
                Text("Gerar também arquivo CUE (opcional)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
            if (busy) OutlinedButton(onClick = { ExportService.cancel(context) }) { Text("Cancelar geração") }
            when (val e = export) {
                is ExportStatus.Running -> Text("${e.phase} (${e.cdName}): ${(e.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                is ExportStatus.Done -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Playlist pronta (${e.cdName}): ${formatSize(e.sizeBytes)} · ${formatDuration(e.durationMs)}", style = MaterialTheme.typography.bodySmall)
                    e.savedTo?.let { Text("Salva na pasta: $it", style = MaterialTheme.typography.bodySmall) }
                    e.saveNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    if (e.deletedOriginals > 0 || e.failedDeletes > 0) {
                        Text(
                            "Originais apagados: ${e.deletedOriginals}" +
                                (if (e.failedDeletes > 0) " · ${e.failedDeletes} não puderam ser apagados (apague pelo gerenciador de arquivos)" else ""),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                is ExportStatus.Failed -> Text("Falha ao gerar (${e.cdName}): ${e.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                is ExportStatus.Cancelled -> Text("Geração cancelada (${e.cdName}). Nenhum arquivo parcial foi mantido.", style = MaterialTheme.typography.bodySmall)
                ExportStatus.Idle -> {}
            }
            if (picking) TrackPickerDialog(cdTracks.map { it.id }.toSet(), vm, onDismiss = { picking = false }) { vm.addTracks(cd.id, it); picking = false }
        }
    }
}

@Composable
private fun TrackPickerDialog(inCd: Set<Long>, vm: CdEditorViewModel, onDismiss: () -> Unit, onConfirm: (List<Long>) -> Unit) {
    val library by vm.library.collectAsStateWithLifecycle()
    val available = library.filter { it.id !in inCd }
    var chosen by remember { mutableStateOf(setOf<Long>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecionar músicas") },
        text = {
            if (available.isEmpty()) Text("Não há músicas disponíveis na biblioteca.")
            else LazyColumn {
                items(available, key = { it.id }) { t ->
                    Row(Modifier.fillMaxWidth().clickable { chosen = if (t.id in chosen) chosen - t.id else chosen + t.id },
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = t.id in chosen, onCheckedChange = { chosen = if (it) chosen + t.id else chosen - t.id })
                        Column {
                            Text(t.title, style = MaterialTheme.typography.bodyMedium)
                            t.artist?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(available.filter { it.id in chosen }.map { it.id }) }, enabled = chosen.isNotEmpty()) {
                Text("Adicionar (${chosen.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun formatDate(millis: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(millis))


@Composable
private fun CdAudioOptions(cdId: Long, context: android.content.Context) {
    val store = remember(context) { CdAudioSettingsStore(context) }
    var settings by remember(cdId) { mutableStateOf(store.load(cdId)) }
    fun update(next: CdAudioSettings) {
        settings = next
        store.save(cdId, next)
    }
    val presets = listOf(
        "original" to "Original",
        "acoustic" to "Acústico",
        "bass" to "Grave",
        "treble" to "Agudo",
        "funk" to "Funk",
        "pagode" to "Pagode",
        "blues" to "Blues",
        "rock" to "Rock"
    )
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Personalizar áudio", style = MaterialTheme.typography.titleMedium)
            Text("Escolha como o som será processado no MP3 final. As músicas originais da biblioteca não serão alteradas.",
                style = MaterialTheme.typography.bodySmall)
            Text("Equalizador", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            presets.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEach { (id, label) ->
                        OutlinedButton(
                            onClick = { update(CdAudioSettings.preset(id, settings.normalize)) },
                            modifier = Modifier.weight(1f).padding(end = 4.dp)
                        ) { Text(if (settings.presetId == id) "✓ $label" else label) }
                    }
                    if (pair.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
            Text("Personalizado", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text("Graves: ${settings.bassDb.toInt()} dB")
            Slider(settings.bassDb, { update(settings.copy(presetId = "custom", bassDb = it)) }, valueRange = -12f..12f)
            Text("Médios: ${settings.midDb.toInt()} dB")
            Slider(settings.midDb, { update(settings.copy(presetId = "custom", midDb = it)) }, valueRange = -12f..12f)
            Text("Agudos: ${settings.trebleDb.toInt()} dB")
            Slider(settings.trebleDb, { update(settings.copy(presetId = "custom", trebleDb = it)) }, valueRange = -12f..12f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = settings.normalize, onCheckedChange = { update(settings.copy(normalize = it)) })
                Column {
                    Text("Normalizar volume das músicas")
                    Text("Ajusta o volume percebido entre as faixas e limita picos.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
