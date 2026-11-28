package com.cdvirtual.app.feature.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cdvirtual.app.core.diagnostics.AppDiagnostics
import com.cdvirtual.app.core.ui.ScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf("Carregando registros…") }
    var loading by remember { mutableStateOf(true) }

    fun refresh() {
        scope.launch {
            loading = true
            report = withContext(Dispatchers.IO) { AppDiagnostics.readAll(context) }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(report.toByteArray(Charsets.UTF_8))
                        } ?: error("Não foi possível abrir o arquivo de destino")
                    }
                }
                Toast.makeText(
                    context,
                    if (result.isSuccess) "Relatório salvo" else "Falha ao salvar relatório: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    ScreenScaffold(title = "Logs de diagnóstico", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                "Histórico persistente do uso do aplicativo. Atualize após reproduzir um erro.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("MixTapeMp3 logs", report))
                    Toast.makeText(context, "Logs copiados", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f), enabled = !loading) { Text("Copiar logs") }
                OutlinedButton(onClick = {
                    exportLauncher.launch("MixTapeMp3-logs.md")
                }, modifier = Modifier.weight(1f), enabled = !loading) { Text("Exportar .md") }
            }
            OutlinedButton(onClick = { refresh() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (loading) "Atualizando…" else "Atualizar registros")
            }
            Text(
                "O relatório inclui eventos recentes e uma geração anterior. Dados digitados e URLs não são registrados intencionalmente.",
                Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(report.lines(), key = { index -> index.toString() }) { line ->
                    Text(
                        line,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
