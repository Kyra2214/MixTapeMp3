package com.cdvirtual.app.feature.browser

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cdvirtual.app.feature.importer.ImporterViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val PREFS = "mixtape_browser"
private const val FORMAT_KEY = "preferred_audio_format"
private const val FORMAT_CONFIGURED_KEY = "audio_format_configured"
private val formatChoices = listOf(
    "BEST" to "Melhor qualidade disponível",
    "webm" to "WebM / Opus",
    "m4a" to "M4A / AAC"
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    webView: WebView,
    vm: ImporterViewModel,
    onHome: () -> Unit,
    startUrl: String = "https://www.youtube.com/",
    title: String = "Navegador"
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE) }
    var preferredFormat by remember { mutableStateOf(preferences.getString(FORMAT_KEY, "BEST") ?: "BEST") }
    var showFormatDialog by remember { mutableStateOf(false) }
    var formatConfigured by remember { mutableStateOf(preferences.getBoolean(FORMAT_CONFIGURED_KEY, false)) }
    var address by remember(webView) { mutableStateOf(webView.url ?: startUrl) }
    var currentUrl by remember(webView) { mutableStateOf(webView.url ?: startUrl) }
    var progress by remember(webView) { mutableFloatStateOf(if (webView.progress == 100) 1f else 0f) }
    var pageTitle by remember(webView) { mutableStateOf(webView.title?.takeIf { it.isNotBlank() } ?: title) }
    var canGoBack by remember(webView) { mutableStateOf(webView.canGoBack()) }
    var canGoForward by remember(webView) { mutableStateOf(webView.canGoForward()) }

    DisposableEffect(webView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.textZoom = 85
        webView.setInitialScale(90)
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.setSupportMultipleWindows(false)
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webView.webViewClient = object : WebViewClient() {
            private fun syncAddress(view: WebView, url: String?) {
                if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                    currentUrl = url
                    address = url
                }
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                syncAddress(view, url)
                progress = 0f
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                syncAddress(view, url)
            }
            override fun onPageFinished(view: WebView, url: String) {
                syncAddress(view, url)
                pageTitle = view.title?.takeIf { it.isNotBlank() } ?: title
                progress = 1f
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress = newProgress / 100f
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
            }
        }
        if (webView.url.isNullOrBlank()) webView.loadUrl(startUrl)
        onDispose { /* A sessão é mantida no AppNavHost. */ }
    }
    BackHandler(enabled = canGoBack) { webView.goBack() }
    val queue by vm.state.collectAsStateWithLifecycle()
    var sendMessage by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(27.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF9B7BFF), Color(0xFF52E0D0)))),
                contentAlignment = Alignment.Center
            ) { Text("◎", color = Color(0xFF111323), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            Column(Modifier.padding(start = 6.dp).weight(1f)) {
                Text("Explorar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(pageTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            OutlinedButton(onClick = onHome, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp), modifier = Modifier.height(34.dp)) { Text("⌂ Início", style = MaterialTheme.typography.labelSmall) }
        }

        Card(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = address, onValueChange = { address = it }, singleLine = true,
                    label = { Text("Endereço ou link", style = MaterialTheme.typography.labelSmall) }, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().height(52.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val raw = address.trim()
                        val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
                        webView.loadUrl(url)
                    }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.weight(1f).height(36.dp)) { Text("Abrir página  ↗", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(onClick = { webView.reload() }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(36.dp)) { Text("↻", style = MaterialTheme.typography.bodySmall) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (webView.canGoBack()) webView.goBack() }, enabled = canGoBack, modifier = Modifier.weight(1f).height(34.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) { Text("← Voltar", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(onClick = { if (webView.canGoForward()) webView.goForward() }, enabled = canGoForward, modifier = Modifier.weight(1f).height(34.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) { Text("Avançar →", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        if (progress < 1f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        if (!formatConfigured) {
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("CONFIGURAÇÃO INICIAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    Text("Escolha a qualidade padrão dos seus áudios", style = MaterialTheme.typography.bodySmall)
                    Text(formatChoices.firstOrNull { it.first == preferredFormat }?.second ?: "Melhor qualidade",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { showFormatDialog = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.fillMaxWidth().height(34.dp)) {
                        Text("Escolher qualidade", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Button(
            onClick = {
                vm.enqueueUrlAutomatically(currentUrl, preferredFormat)
                sendMessage = "Link enviado para a fila. Você pode continuar navegando."
            },
            enabled = currentUrl.startsWith("http"),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.fillMaxWidth().height(36.dp)
        ) { Text("↓  Enviar para downloads", style = MaterialTheme.typography.labelSmall) }
        sendMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
        val activeCount = queue.queue.count { it.status == "Analisando" || it.status == "Na fila" || it.status == "Baixando" || it.status == "Importando" }
        if (activeCount > 0 || queue.queue.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Downloads · $activeCount em andamento", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    queue.queue.takeLast(3).forEach { entry ->
                        Text("${entry.title} · ${entry.status}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        if (entry.status == "Baixando" || entry.status == "Importando") {
                            LinearProgressIndicator(progress = { entry.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().weight(1f))
    }

    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text("Formato e qualidade") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    formatChoices.forEach { (value, label) ->
                        TextButton(onClick = {
                            preferredFormat = value
                            preferences.edit().putString(FORMAT_KEY, value).putBoolean(FORMAT_CONFIGURED_KEY, true).apply()
                            formatConfigured = true
                            showFormatDialog = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (preferredFormat == value) "✓  $label" else label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFormatDialog = false }) { Text("Fechar") } }
        )
    }
}
