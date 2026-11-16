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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun BrowserScreen(onSendToDownloads: (String, String) -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE) }
    var preferredFormat by remember { mutableStateOf(preferences.getString(FORMAT_KEY, "BEST") ?: "BEST") }
    var showFormatDialog by remember { mutableStateOf(false) }
    var formatConfigured by remember { mutableStateOf(preferences.getBoolean(FORMAT_CONFIGURED_KEY, false)) }
    var address by remember { mutableStateOf("https://www.youtube.com/") }
    var currentUrl by remember { mutableStateOf("https://www.youtube.com/") }
    var progress by remember { mutableFloatStateOf(0f) }
    var pageTitle by remember { mutableStateOf("Navegador") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
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
                    // YouTube navega como SPA: troca a URL via History API sem recarregar a página.
                    syncAddress(view, url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    syncAddress(view, url)
                    pageTitle = view.title?.takeIf { it.isNotBlank() } ?: "Navegador"
                    progress = 1f
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress / 100f
                    canGoBack = view.canGoBack()
                    canGoForward = view.canGoForward()
                }
            }
            loadUrl("https://www.youtube.com/")
        }
    }
    BackHandler(enabled = canGoBack) { webView.goBack() }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF9B7BFF), Color(0xFF52E0D0)))),
                contentAlignment = Alignment.Center
            ) { Text("◎", color = Color(0xFF111323), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                Text("Explorar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(pageTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Text("WEB", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall)
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address, onValueChange = { address = it }, singleLine = true,
                    label = { Text("Endereço ou link") }, modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val raw = address.trim()
                        val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
                        webView.loadUrl(url)
                    }, modifier = Modifier.weight(1f)) { Text("Abrir página  ↗") }
                    OutlinedButton(onClick = { webView.reload() }) { Text("↻") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (webView.canGoBack()) webView.goBack() }, enabled = canGoBack, modifier = Modifier.weight(1f)) { Text("← Voltar") }
                    OutlinedButton(onClick = { if (webView.canGoForward()) webView.goForward() }, enabled = canGoForward, modifier = Modifier.weight(1f)) { Text("Avançar →") }
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
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CONFIGURAÇÃO INICIAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    Text("Escolha a qualidade padrão dos seus áudios", style = MaterialTheme.typography.titleSmall)
                    Text(formatChoices.firstOrNull { it.first == preferredFormat }?.second ?: "Melhor qualidade",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { showFormatDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Escolher qualidade")
                    }
                }
            }
        }
        Button(
            onClick = { onSendToDownloads(currentUrl, preferredFormat) },
            enabled = currentUrl.startsWith("http"),
            modifier = Modifier.fillMaxWidth()
        ) { Text("↓  Enviar para downloads") }
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().weight(1f))
    }

    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text("Formato e qualidade") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
