package com.cdvirtual.app.feature.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdvirtual.app.media.connectors.LinkKind

@Composable
fun ImporterScreen(vm: ImporterViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val err = s.error
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Importar por link", style = MaterialTheme.typography.headlineSmall)
        Text("Este é o único recurso que usa a internet; para músicas já no aparelho, use \"Importar músicas\" na Biblioteca (funciona offline).",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = s.input, onValueChange = vm::onInput, singleLine = true,
            label = { Text("Cole o link aqui") }, isError = err != null,
            supportingText = if (err != null) { { Text(err) } } else null,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = vm::submit, enabled = s.input.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
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
            if (s.progress > 0f) LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (s.progress >= 1f) "Importando para a biblioteca…" else "Baixando… ${(s.progress * 100).toInt()}%")
        }
        if (s.analyzing || s.importing) {
            OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        }
        s.result?.let { Text(it) }
        s.info?.let { i ->
            if (i.kind == LinkKind.Audio) Text("Áudio detectado")
            i.message?.let { Text(it) }
            i.fileName?.let { Text("Arquivo: $it") }
            i.options.forEach { o ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = s.selected == o, onClick = { vm.select(o) }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = s.selected == o, onClick = { vm.select(o) })
                    Text(o.label)
                }
            }
            if (i.kind == LinkKind.Audio) {
                Button(onClick = vm::importSelected, enabled = s.selected != null && !s.importing, modifier = Modifier.fillMaxWidth()) {
                    Text("Importar para a biblioteca")
                }
            }
        }
    }
}
