package com.cdvirtual.app.feature.browser

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val PREFS = "mixtape_browser"
private const val FORMAT_KEY = "preferred_audio_format"
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
    var preferredFormat by remember {
        mutableStateOf(preferences.getString(FORMAT_KEY, "BEST") ?: "BEST")
    }
    var showFormatDialog by remember { mutableStateOf(false) }
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
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
                override fun onPageFinished(view: WebView, url: String) {
                    currentUrl = url
                    address = url
                    pageTitle = view.title?.takeIf { it.isNotBlank() } ?: "Navegador"
                    canGoBack = view.canGoBack()
                    canGoForward = view.canGoForward()
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
    BackHandler(enabled = canGoBack) {
        webView.goBack()
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pageTitle, style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                singleLine = true,
                label = { Text("Endereço ou link") },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                val raw = address.trim()
                val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
                webView.loadUrl(url)
            }) { Text("Ir") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { if (webView.canGoBack()) webView.goBack() }, enabled = canGoBack) { Text("Voltar") }
            OutlinedButton(onClick = { if (webView.canGoForward()) webView.goForward() }, enabled = canGoForward) { Text("Avançar") }
            OutlinedButton(onClick = { webView.reload() }) { Text("Atualizar") }
        }
        if (progress < 1f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showFormatDialog = true }, modifier = Modifier.weight(1f)) {
                Text("Áudio: ${formatChoices.firstOrNull { it.first == preferredFormat }?.second ?: "Melhor qualidade"}")
            }
            Button(
                onClick = { onSendToDownloads(currentUrl, preferredFormat) },
                enabled = currentUrl.startsWith("http"),
                modifier = Modifier.weight(1f)
            ) { Text("Enviar para downloads") }
        }
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().weight(1f))
    }

    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text("Formato e qualidade preferidos") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    formatChoices.forEach { (value, label) ->
                        TextButton(onClick = {
                            preferredFormat = value
                            preferences.edit().putString(FORMAT_KEY, value).apply()
                            showFormatDialog = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (preferredFormat == value) "✓ $label" else label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFormatDialog = false }) { Text("Fechar") } }
        )
    }
}
