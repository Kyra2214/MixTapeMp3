package com.cdvirtual.app.feature.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cdvirtual.app.core.common.formatDuration
import com.cdvirtual.app.core.common.formatSize
import com.cdvirtual.app.core.common.rememberNotificationGate
import com.cdvirtual.app.core.storage.ExportCopies
import com.cdvirtual.app.core.storage.FolderExporter
import com.cdvirtual.app.core.storage.OutputFolder
import com.cdvirtual.app.core.storage.OutputFolderState
import com.cdvirtual.app.core.storage.ShareFiles
import com.cdvirtual.app.media.metadata.CueSheet
import com.cdvirtual.app.media.playback.Mp3Player
import com.cdvirtual.app.media.service.ExportState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun ExportScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val withNotifications = rememberNotificationGate() // T-503
    val folder = remember { OutputFolder(context) }
    var state by remember { mutableStateOf(folder.state()) }
    var cds by remember { mutableStateOf(emptyList<GeneratedCd>()) }
    val exportStatus by ExportState.status.collectAsState()
    // 10.2: a leitura das tags ID3 roda fora da thread principal (arquivos grandes/capas) e atualiza a lista quando termina.
    fun refreshCds() { scope.launch { cds = withContext(Dispatchers.IO) { GeneratedCds.list(context) } } }
    LaunchedEffect(exportStatus) { cds = withContext(Dispatchers.IO) { GeneratedCds.list(context) } } // atualiza quando uma geração termina
    var busy by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<GeneratedCd?>(null) }
    // T-402: nome gravado na pasta de destino e cópias (MP3/CUE) ficam em ExportCopies (persistem entre telas e reinícios).
    var deleting by remember { mutableStateOf<GeneratedCd?>(null) }
    var alsoDeleteCopies by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) state = folder.choose(uri)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Exportar", style = MaterialTheme.typography.titleLarge)
        Text("Pasta de destino", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        when (val s = state) {
            OutputFolderState.NotSet -> Text("Nenhuma pasta escolhida. Escolha onde salvar os MP3 exportados.", style = MaterialTheme.typography.bodyMedium)
            is OutputFolderState.Ready -> Text("Pasta: ${s.name}", style = MaterialTheme.typography.bodyMedium)
            is OutputFolderState.Unavailable -> Text(
                "A pasta escolhida não está mais acessível. Escolha novamente.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium
            )
        }
        Row(Modifier.padding(top = 8.dp)) {
            Button(onClick = { picker.launch(null) }) {
                Text(if (state is OutputFolderState.NotSet) "Escolher pasta" else "Trocar pasta")
            }
            if (state !is OutputFolderState.NotSet) {
                OutlinedButton(onClick = { folder.clear(); state = folder.state() }, Modifier.padding(start = 8.dp)) { Text("Esquecer") }
            }
        }

        Text("Playlists exportadas (${cds.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
        if (cds.isEmpty()) Text("Nenhuma playlist exportada ainda.", style = MaterialTheme.typography.bodyMedium)
        else Text("Ocupam ${formatSize(GeneratedCds.totalBytes(cds))} neste aparelho", style = MaterialTheme.typography.bodySmall)
        val canExport = busy == null && state is OutputFolderState.Ready

        cds.forEach { cd ->
            val f = cd.file
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(cd.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(
                            cd.artist,
                            "${cd.trackCount} faixa(s)",
                            cd.durationMs?.let { formatDuration(it) },
                            formatSize(cd.sizeBytes),
                            if (cd.hasCue) "com CUE" else null
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "${f.name} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(cd.modifiedAt))}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = canExport, onClick = {
                            val ready = state as? OutputFolderState.Ready ?: return@Button
                            busy = f.name; progress = 0f; message = null
                            scope.launch {
                                message = try {
                                    val r = FolderExporter.copy(context, f, ready.uri, "audio/mpeg") { progress = it }
                                    ExportCopies.addCopy(context, f.name, r.displayName, r.documentUri)
                                    "Exportado: ${r.displayName} em ${ready.name}"
                                } catch (e: CancellationException) { throw e }
                                catch (e: Throwable) { "Falha ao exportar: ${e.message}" }
                                busy = null
                            }
                        }) { Text("Exportar MP3") }
                        OutlinedButton(enabled = canExport, onClick = {
                            val ready = state as? OutputFolderState.Ready ?: return@OutlinedButton
                            val mp3Name = ExportCopies.displayName(context, f.name) ?: f.name
                            val text = CueSheet.fromMp3(f, mp3Name)
                            if (text == null) { message = "Este MP3 não tem faixas marcadas para gerar o CUE"; return@OutlinedButton }
                            busy = f.name; message = null
                            scope.launch {
                                message = try {
                                    val cueName = mp3Name.substringBeforeLast('.') + ".cue"
                                    val r = FolderExporter.writeBytes(context, ready.uri, cueName, "application/x-cue", CueSheet.encode(text))
                                    ExportCopies.addCopy(context, f.name, null, r.documentUri)
                                    "CUE exportado: ${r.displayName} em ${ready.name}"
                                } catch (e: CancellationException) { throw e }
                                catch (e: Throwable) { "Falha ao exportar CUE: ${e.message}" }
                                busy = null
                            }
                        }) { Text("CUE") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            try { context.startActivity(ShareFiles.chooser(context, f)) }
                            catch (e: Throwable) { message = "Não foi possível compartilhar: ${e.message}" }
                        }) { Text("Compartilhar") }
                        OutlinedButton(onClick = { withNotifications { Mp3Player.play(context, f) } }) { Text("Ouvir") }
                        OutlinedButton(onClick = { renaming = cd }) { Text("Renomear") }
                        OutlinedButton(onClick = { alsoDeleteCopies = false; deleting = cd }) { Text("Excluir") }
                    }
                    OutlinedButton(onClick = {
                        try { context.startActivity(ShareFiles.openWith(context, f)) }
                        catch (e: Throwable) { message = "Nenhum player disponível para abrir o MP3: ${e.message}" }
                    }) { Text("Abrir em outro player") }
                }
            }
        }
        if (state !is OutputFolderState.Ready && cds.isNotEmpty()) {
            Text("Escolha a pasta de destino para exportar.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        busy?.let { Text("Copiando $it: ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
    }

    deleting?.let { target ->
        val copies = ExportCopies.docs(context, target.file.name)
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir CD gerado?") },
            text = {
                Column {
                    Text("\"${target.title}\" (${formatSize(target.sizeBytes)}) será apagado deste aparelho. As músicas originais e a biblioteca não são afetadas.")
                    if (copies.isNotEmpty()) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            androidx.compose.material3.Checkbox(checked = alsoDeleteCopies, onCheckedChange = { alsoDeleteCopies = it })
                            Text("Excluir também ${copies.size} arquivo(s) exportado(s) na pasta de destino")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val removeCopies = alsoDeleteCopies
                    if (Mp3Player.state.value.file == target.file) Mp3Player.release()
                    val ok = GeneratedCds.delete(context, target)
                    refreshCds()
                    deleting = null
                    scope.launch {
                        var failed = 0
                        if (ok && removeCopies) {
                            copies.forEach { if (!FolderExporter.delete(context, it)) failed++ }
                        }
                        if (ok) ExportCopies.clear(context, target.file.name) // o arquivo some; o registro não pode valer para um futuro MP3 de mesmo nome
                        message = when {
                            !ok -> "Não foi possível excluir ${target.file.name}"
                            failed > 0 -> "CD excluído; $failed cópia(s) na pasta de destino não puderam ser apagadas"
                            else -> "CD excluído"
                        }
                    }
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }

    renaming?.let { target ->
        var name by remember(target) { mutableStateOf(target.file.nameWithoutExtension) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renomear CD gerado") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, singleLine = true, label = { Text("Nome do arquivo") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (Mp3Player.state.value.file == target.file) Mp3Player.release() // não renomeia o arquivo em reprodução
                    message = when (val r = GeneratedCds.rename(context, target, name)) {
                        is GeneratedCds.RenameResult.Ok -> "Renomeado para ${r.file.name}"
                        is GeneratedCds.RenameResult.Error -> r.message
                    }
                    refreshCds()
                    renaming = null
                }) { Text("Renomear") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } }
        )
    }
}
