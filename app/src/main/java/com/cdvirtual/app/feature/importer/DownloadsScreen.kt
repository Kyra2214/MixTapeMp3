package com.cdvirtual.app.feature.importer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DownloadsScreen(vm: ImporterViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val active = state.queue.count { it.status in listOf("Analisando", "Na fila", "Baixando", "Importando") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Downloads", style = MaterialTheme.typography.headlineSmall)
                Text("$active em andamento · ${state.queue.size} no histórico", style = MaterialTheme.typography.bodySmall)
            }
            if (state.queue.isNotEmpty()) OutlinedButton(onClick = vm::clearQueue) { Text("Limpar") }
        }
        if (state.queue.isEmpty()) Text("Sua fila está vazia. Envie um áudio pelo Navegador ou pela tela Importar.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.queue, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        Text("${item.format} · ${item.status}", style = MaterialTheme.typography.bodySmall)
                        if (item.status in listOf("Baixando", "Importando"))
                            LinearProgressIndicator(progress = { item.progress.coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth())
                        item.detail?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (item.status == "Falhou" || item.status == "Cancelado")
                                Button(onClick = { vm.retryDownload(item.id) }) { Text("↻ Tentar novamente") }
                            OutlinedButton(onClick = { vm.removeDownload(item.id) }) { Text("Remover") }
                        }
                    }
                }
            }
        }
    }
}