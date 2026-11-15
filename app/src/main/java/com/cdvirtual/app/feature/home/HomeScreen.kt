package com.cdvirtual.app.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cdvirtual.app.core.storage.OutputFolder
import com.cdvirtual.app.core.storage.OutputFolderState
import com.cdvirtual.app.navigation.Destination

@Composable
fun HomeScreen(onNavigate: (Destination) -> Unit) {
    val context = LocalContext.current
    // T-203: mesma porta de escolha da tela Exportar (valida a pasta e libera a permissão da anterior).
    val outputFolder = remember { OutputFolder(context) }
    var folder by remember { mutableStateOf(outputFolder.state()) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            folder = outputFolder.choose(uri)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("MixTape MP3", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text("Monte seu CD em um único MP3.", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { pickFolder.launch(null) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (folder is OutputFolderState.Ready) "Pasta de saída definida" else "Escolher pasta de saída")
        }
        Destination.entries.filter { it != Destination.Home && it != Destination.Welcome }.forEach { d ->
            Button(onClick = { onNavigate(d) }, modifier = Modifier.fillMaxWidth()) { Text(d.label) }
        }
    }
}
