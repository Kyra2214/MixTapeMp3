package com.cdvirtual.app.feature.equalizer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdvirtual.app.core.database.TrackEntity
import com.cdvirtual.app.media.normalization.GainPlanner
import com.cdvirtual.app.media.normalization.NormalizationSettings

@Composable
fun EqualizerScreen(vm: EqualizerViewModel = viewModel()) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val originals by vm.originals.collectAsStateWithLifecycle()
    val previewError by vm.previewError.collectAsStateWithLifecycle()
    val cds by vm.cds.collectAsStateWithLifecycle()
    val scopeId by vm.scopeCdId.collectAsStateWithLifecycle()
    val scopeName = cds.firstOrNull { it.id == scopeId }?.name
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Volume e equalização", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Switch(checked = settings.enabled, onCheckedChange = vm::setEnabled)
            Text("Normalizar volume", Modifier.padding(start = 8.dp))
        }
        if (settings.enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { vm.setTarget(settings.targetLufs - 1) }) { Text("−") }
                Text("Alvo: ${settings.targetLufs.toInt()} LUFS", Modifier.padding(horizontal = 12.dp))
                OutlinedButton(onClick = { vm.setTarget(settings.targetLufs + 1) }) { Text("+") }
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("EQ automática e zerar valem para: ${scopeName ?: "biblioteca inteira"}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = vm::cycleScope, enabled = cds.isNotEmpty()) { Text("Trocar") }
        }
        Text("Presets para o escopo selecionado", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)
        listOf(
            Triple("Acústico", 0f, 2f) to 1.5f,
            Triple("Grave", 6f, 0f) to 0f,
            Triple("Agudo", 0f, 0f) to 5f,
            Triple("Funk", 6f, 1f) to 2f,
            Triple("Pagode", 1f, 3f) to 2f,
            Triple("Blues", 2f, 3f) to 1f,
            Triple("Rock", 4f, 2f) to 3f
        ).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (base, treble) ->
                    OutlinedButton(
                        onClick = { vm.applyPreset(base.second, base.third, treble) },
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) { Text(base.first) }
                }
                if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
        Row(Modifier.padding(top = 4.dp)) {
            OutlinedButton(onClick = vm::autoEqualize, enabled = tracks.any { it.bandBassDb != null }) { Text("EQ automática do CD") }
            OutlinedButton(onClick = vm::resetEq, Modifier.padding(start = 8.dp)) { Text("Zerar EQ") }
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = vm::verifyOriginals, enabled = tracks.isNotEmpty()) { Text("Verificar originais") }
            Text(originals ?: "Ajustes não alteram os arquivos originais", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (tracks.isEmpty()) Text("Importe músicas na Biblioteca para ajustar o volume.", Modifier.padding(top = 16.dp))
        previewError?.let { Text("Pré-visualização: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(tracks, key = { it.id }) { t -> TrackCard(t, settings, vm, preview?.takeIf { it.first == t.id }?.second) }
        }
    }
}

@Composable
private fun TrackCard(t: TrackEntity, settings: NormalizationSettings, vm: EqualizerViewModel, previewing: Boolean?) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(t.title, style = MaterialTheme.typography.bodyLarge)
            val plan = GainPlanner.forTrack(t, settings)
            Text(
                if (t.lufs == null) "Analisando…" else "%.1f LUFS · ganho total %+.1f dB".format(t.lufs, plan?.gainDb ?: 0f),
                style = MaterialTheme.typography.bodySmall
            )
            if (plan?.limitedByPeak == true) {
                Text("Ganho limitado para evitar clipping", style = MaterialTheme.typography.bodySmall)
            }
            GainSlider("Ajuste individual", t.userGainDb, -12f..12f, 47) { vm.setUserGain(t.id, it) }
            GainSlider("Graves", t.bassDb, -12f..12f, 23) { vm.setEq(t.id, it, t.midDb, t.trebleDb) }
            GainSlider("Médios", t.midDb, -12f..12f, 23) { vm.setEq(t.id, t.bassDb, it, t.trebleDb) }
            GainSlider("Agudos", t.trebleDb, -12f..12f, 23) { vm.setEq(t.id, t.bassDb, t.midDb, it) }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { vm.preview(t, false) }, enabled = previewing != false) { Text("Original") }
                OutlinedButton(onClick = { vm.preview(t, true) }, Modifier.padding(start = 8.dp), enabled = previewing != true) { Text("Processado") }
                if (previewing != null) OutlinedButton(onClick = vm::stopPreview, Modifier.padding(start = 8.dp)) { Text("Parar") }
            }
            Text("Pré-visualização de 15 s do meio da faixa", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GainSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onCommit: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Text("$label: %+.1f dB".format(local), style = MaterialTheme.typography.bodySmall)
    Slider(
        value = local, onValueChange = { local = it }, onValueChangeFinished = { onCommit(local) },
        valueRange = range, steps = steps
    )
}
