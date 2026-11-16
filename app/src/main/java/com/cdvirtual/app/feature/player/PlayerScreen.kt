package com.cdvirtual.app.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.media.AudioManager
import com.cdvirtual.app.core.common.formatDuration
import com.cdvirtual.app.core.ui.ScreenScaffold
import androidx.compose.ui.text.style.TextOverflow
import com.cdvirtual.app.core.common.rememberNotificationGate
import com.cdvirtual.app.media.playback.Mp3Player
import com.cdvirtual.app.media.service.ExportState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.cdvirtual.app.core.common.CoverDecoder
import com.cdvirtual.app.media.service.ExportRecovery
import com.cdvirtual.app.media.metadata.Id3Reader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private const val SKIP_MS = 15_000L

/** Decodifica a capa já reduzida (máx. ~512 px de lado) ou null se ausente/inválida. */
private fun decodeCover(bytes: ByteArray?): ImageBitmap? {
    if (bytes == null) return null
    return try {
        CoverDecoder.fromBytes(bytes, 512)?.asImageBitmap()
    } catch (_: Throwable) { null }
}

/** Lista os MP3 gerados no aparelho (filesDir/exports) para reprodução offline. */
fun exportedMp3s(context: android.content.Context): List<File> =
    ExportRecovery.exportsDir(context).listFiles { f -> f.isFile && f.extension.equals("mp3", true) }
        ?.sortedByDescending { it.lastModified() } ?: emptyList()

@Composable
fun PlayerScreen(onBack: () -> Unit) {
    ScreenScaffold("Player", onBack) { PlayerContent() }
}

/** Volume da mídia do aparelho (o mesmo dos botões físicos): o controle acompanha os botões e vice-versa. */
@Composable
private fun DeviceVolumeControl() {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    if (audio == null) return // aparelho sem serviço de áudio: não mostra o controle
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var dragging by remember { mutableStateOf(false) }
    // acompanha mudanças feitas pelos botões físicos enquanto a tela está aberta
    LaunchedEffect(Unit) {
        while (true) {
            if (!dragging) volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            delay(400)
        }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Volume do aparelho: ${volume * 100 / maxVolume}%", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                volume = if (volume == 0) (maxVolume / 2).coerceAtLeast(1) else 0
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
            }) { Text(if (volume == 0) "Ativar som" else "Silenciar") }
        }
        Slider(
            value = volume.toFloat(), valueRange = 0f..maxVolume.toFloat(), steps = (maxVolume - 1).coerceAtLeast(0),
            onValueChange = { v ->
                dragging = true
                volume = v.toInt()
                try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0) } catch (_: Throwable) { }
            },
            onValueChangeFinished = { dragging = false },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PlayerContent() {
    val context = LocalContext.current
    val withNotifications = rememberNotificationGate() // T-503
    val exportStatus by ExportState.status.collectAsState()
    var files by remember { mutableStateOf(emptyList<File>()) }
    LaunchedEffect(exportStatus) { files = exportedMp3s(context) }
    val player by Mp3Player.state.collectAsState()
    var position by remember { mutableStateOf(0L) }
    LaunchedEffect(player.file, player.playing) {
        while (true) { position = Mp3Player.positionMs(); delay(500) }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DeviceVolumeControl()
        player.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        player.file?.let {
            Text("${if (player.loading) "Carregando" else if (player.playing) "Tocando" else "Pausado"}: ${it.nameWithoutExtension} · ${formatDuration(player.durationMs)}", style = MaterialTheme.typography.bodyMedium)
            // 10.2: capa lida/decodificada fora da thread principal; trocar de arquivo descarta a anterior.
            val cover by produceState<ImageBitmap?>(initialValue = null, it) {
                value = withContext(Dispatchers.IO) { decodeCover(Id3Reader.readCover(it)) }
            }
            cover?.let { bmp ->
                Image(bmp, contentDescription = "Capa da playlist", modifier = Modifier.size(180.dp).clip(RoundedCornerShape(24.dp)))
            }
            Text(
                player.chapterAt(position)?.title?.ifBlank { null } ?: it.nameWithoutExtension,
                style = MaterialTheme.typography.titleMedium
            )
            var dragging by remember(it) { mutableStateOf<Float?>(null) }
            val total = player.durationMs.coerceAtLeast(1L)
            val shown = dragging ?: position.coerceIn(0, total).toFloat()
            Slider(
                value = shown, valueRange = 0f..total.toFloat(),
                onValueChange = { v -> dragging = v },
                onValueChangeFinished = { dragging?.let { v -> Mp3Player.seekTo(v.toLong()); position = v.toLong() }; dragging = null },
                modifier = Modifier.fillMaxWidth()
            )
            Text("${formatDuration(shown.toLong())} / ${formatDuration(player.durationMs)}", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔈", style = MaterialTheme.typography.bodyMedium)
                Slider(value = player.volume, onValueChange = Mp3Player::setVolume, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                Text("${(player.volume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Mp3Player.previousChapter() }, enabled = player.chapters.isNotEmpty()) { Text("⏮") }
                Button(onClick = { Mp3Player.nextChapter() }, enabled = player.chapters.isNotEmpty()) { Text("⏭") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Mp3Player.seekBy(-SKIP_MS) }) { Text("⏪ 15 s") }
                Button(onClick = { Mp3Player.togglePlayPause() }) { Text(if (player.playing) "Pausar" else "Continuar") }
                Button(onClick = { Mp3Player.seekBy(SKIP_MS) }) { Text("15 s ⏩") }
            }
        }
        if (player.chapters.isNotEmpty()) {
            Text("Músicas (toque para ir até ela)", style = MaterialTheme.typography.titleSmall)
            val current = player.chapterIndexAt(position)
            player.chapters.forEachIndexed { i, c ->
                TextButton(onClick = { Mp3Player.seekTo(c.startMs); Mp3Player.resume() }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${i + 1}. ${c.title.ifBlank { "Faixa ${i + 1}" }} · ${formatDuration(c.startMs)}",
                        modifier = Modifier.fillMaxWidth(),
                        style = if (i == current) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        }
    }
    // T-502: lista dos MP3 gerados no mesmo LazyColumn dos controles (a tela inteira rola).
    if (files.isEmpty()) {
        item { Text("Nenhuma playlist gerada ainda. Monte uma em \"Playlists\" e use \"Gerar playlist\".", style = MaterialTheme.typography.bodyMedium) }
    } else {
        items(files, key = { it.absolutePath }) { f ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.nameWithoutExtension, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${f.length() / (1024 * 1024)} MB", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { withNotifications { Mp3Player.play(context, f) } }) { Text("Reproduzir") }
                }
            }
        }
    }
    }
}
