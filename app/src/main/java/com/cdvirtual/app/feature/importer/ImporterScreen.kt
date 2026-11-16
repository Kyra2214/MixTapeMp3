package com.cdvirtual.app.feature.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdvirtual.app.core.common.formatSize
import com.cdvirtual.app.media.connectors.LinkKind

@Composable
fun ImporterScreen(vm: ImporterViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val err = s.error
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Importar por link", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Este é o único recurso que usa a internet; para músicas já no aparelho, use \"Importar músicas\" na Biblioteca (funciona offline).",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = s.input, onValueChange = vm::onInput, singleLine = true,
            label = { Text("Cole o link aqui") }, isError = err != null,
            supportingText = if (err != null) { { Text(err) } } else null,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = vm::submit, enabled = s.input.isNotBlank() && !s.analyzing, modifier = Modifier.fillMaxWidth()) {
            Text("Analisar link")
        }
        s.validUrl?.let { Text("Link válido: $it") }
        s.platform?.let { Text("Plataforma identificada: ${it.label}") }
        if (s.analyzing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Analisando…")
        }
        s.notice?.let { Text(it) }
        if (s.importing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Fila em processamento…")
        }
        s.result?.let { Text(it) }
        s.info?.let { i ->
            if (i.kind == LinkKind.Audio) Text("Áudio detectado", style = MaterialTheme.typography.titleMedium)
            i.message?.let { Text(it) }
            i.fileName?.let { Text("Arquivo: $it") }
            i.options.forEach { o ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = s.selected == o, onClick = { vm.select(o) }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = s.selected == o, onClick = { vm.select(o) })
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(o.label, style = MaterialTheme.typography.bodyMedium)
                        if (o.sizeBytes > 0L) {
                            Text("Tamanho aproximado: ${formatSize(o.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (i.kind == LinkKind.Audio) {
                Button(onClick = vm::importSelected, enabled = s.selected != null, modifier = Modifier.fillMaxWidth()) {
                    Text("Adicionar à fila e baixar")
                }
            }
        }
        if (s.queue.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Fila de downloads", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = vm::clearQueue) { Text("Limpar lista") }
            }
            s.queue.forEach { item ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium)
                    Text("${item.format} · ${item.status}", style = MaterialTheme.typography.bodySmall)
                    if (item.status == "Baixando" || item.status == "Importando") {
                        LinearProgressIndicator(progress = { item.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                    item.detail?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.status == "Falhou" || item.status == "Cancelado") {
                            OutlinedButton(onClick = { vm.retryDownload(item.id) }) {
                                Text("↻ Reiniciar")
                            }
                        }
                        OutlinedButton(onClick = { vm.removeDownload(item.id) }) {
                            Text("×")
                        }
                    }
                }
            }
        }
    }
}
