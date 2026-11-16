package com.cdvirtual.app.feature.settings

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cdvirtual.app.core.common.AppSettings
import com.cdvirtual.app.core.storage.OutputFolder
import com.cdvirtual.app.core.storage.OutputFolderState
import com.cdvirtual.app.core.ui.ButtonFlow
import com.cdvirtual.app.core.ui.ScreenScaffold
import com.cdvirtual.app.media.metadata.CueSettings
import com.cdvirtual.app.media.normalization.NormalizationSettingsStore

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val outputFolder = remember { OutputFolder(context) }
    var folder by remember { mutableStateOf(outputFolder.state()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) folder = outputFolder.choose(uri)
    }
    var autoSave by remember { mutableStateOf(AppSettings.autoSaveToFolder(context)) }
    var writeCue by remember { mutableStateOf(CueSettings.isEnabled(context)) }
    val normStore = remember { NormalizationSettingsStore(context) }
    var norm by remember { mutableStateOf(normStore.load()) }
    val version = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Throwable) { null }
    }

    ScreenScaffold("Configurações", onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Section("Pasta de destino") {
                when (val s = folder) {
                    OutputFolderState.NotSet -> Text("Nenhuma pasta escolhida. As playlists ficam só dentro do app até você escolher uma.", style = MaterialTheme.typography.bodyMedium)
                    is OutputFolderState.Ready -> Text("Pasta: ${s.name}", style = MaterialTheme.typography.bodyMedium)
                    is OutputFolderState.Unavailable -> Text(
                        "A pasta escolhida não está mais acessível. Escolha novamente.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium
                    )
                }
                ButtonFlow {
                    Button(onClick = { picker.launch(null) }) { Text(if (folder is OutputFolderState.NotSet) "Escolher pasta" else "Trocar pasta") }
                    if (folder !is OutputFolderState.NotSet) {
                        OutlinedButton(onClick = { outputFolder.clear(); folder = outputFolder.state() }) { Text("Esquecer") }
                    }
                }
                SwitchRow(
                    "Salvar automaticamente ao gerar",
                    "Quando a playlist ficar pronta, o MP3 é copiado sozinho para essa pasta.",
                    autoSave
                ) { autoSave = it; AppSettings.setAutoSaveToFolder(context, it) }
                SwitchRow(
                    "Gerar também arquivo CUE",
                    "Cria um .cue com o início de cada música, ao lado do MP3.",
                    writeCue
                ) { writeCue = it; CueSettings.setEnabled(context, it) }
            }

            Section("Volume") {
                SwitchRow(
                    "Normalizar volume (tela Volume e equalização)",
                    "Vale para a análise e a pré-visualização. No MP3 final, cada playlist tem sua própria opção em \"Personalizar áudio\".",
                    norm.enabled
                ) { norm = norm.copy(enabled = it); normStore.save(norm) }
                if (norm.enabled) {
                    Text("Volume alvo: ${norm.targetLufs.toInt()} LUFS (padrão -14; quanto mais perto de 0, mais alto)", style = MaterialTheme.typography.bodyMedium)
                    ButtonFlow {
                        OutlinedButton(onClick = { norm = norm.copy(targetLufs = (norm.targetLufs - 1).coerceIn(-23f, -8f)); normStore.save(norm) }) { Text("− Mais baixo") }
                        OutlinedButton(onClick = { norm = norm.copy(targetLufs = (norm.targetLufs + 1).coerceIn(-23f, -8f)); normStore.save(norm) }) { Text("+ Mais alto") }
                    }
                }
                Text("Ajustes por música (ganho, graves, médios e agudos) ficam em Volume e equalização; o equalizador e os presets de cada playlist ficam na tela Playlists.", style = MaterialTheme.typography.bodySmall)
            }

            Section("Sobre") {
                Text("MixTape MP3" + (version?.let { " · versão $it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                Text("Seus arquivos originais nunca são alterados, a não ser que você peça para apagá-los ao gerar uma playlist.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
