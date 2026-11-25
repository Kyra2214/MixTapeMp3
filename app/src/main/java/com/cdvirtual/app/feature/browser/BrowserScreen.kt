package com.cdvirtual.app.feature.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.ComponentName
import android.net.Uri
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.cdvirtual.app.BuildConfig
import com.cdvirtual.app.media.browser.BrowserMediaPlaybackService
import com.cdvirtual.app.media.youtube.YouTubePlaybackService
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
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
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var nativeVideoId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, YouTubePlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        var active = true
        future.addListener({
            val connected = runCatching { future.get() }.getOrNull()
            if (active && connected != null) {
                mediaController = connected
                nativeVideoId = connected.currentMediaItem?.mediaId
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            active = false
            MediaController.releaseFuture(future)
            mediaController = null
            nativeVideoId = null
        }
    }

    DisposableEffect(mediaController) {
        val controller = mediaController
        if (controller == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    nativeVideoId = mediaItem?.mediaId
                }
            }
            nativeVideoId = controller.currentMediaItem?.mediaId
            controller.addListener(listener)
            onDispose { controller.removeListener(listener) }
        }
    }
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
        fun startNativeYouTubePlayback(videoId: String?) {
            val normalized = videoId?.trim()?.takeIf {
                it.matches(Regex("^[A-Za-z0-9_-]{11}$"))
            } ?: return
            BrowserMediaPlaybackService.update(context.applicationContext, false, null)
            YouTubePlaybackService.play(context.applicationContext, normalized, audioOnly = false)
        }

        fun startNativePlaybackFromUrl(rawUrl: String?) {
            val uri = runCatching { Uri.parse(rawUrl ?: return) }.getOrNull() ?: return
            val host = uri.host?.lowercase().orEmpty()
            val videoId = when {
                host == "youtu.be" -> uri.lastPathSegment
                host == "youtube.com" || host.endsWith(".youtube.com") -> uri.getQueryParameter("v")
                else -> null
            }
            startNativeYouTubePlayback(videoId)
        }

        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onMediaStateChanged(playing: Boolean, title: String?) {
                    BrowserMediaPlaybackService.update(context.applicationContext, playing, title)
                }

                @JavascriptInterface
                fun onYouTubeVideoSelected(videoId: String) {
                    startNativeYouTubePlayback(videoId)
                }

                @JavascriptInterface
                fun onPlaylistDiagnostic(host: String, panel: String, item: String, scroller: String) {
                    android.util.Log.i(
                        "MixTapePlaylist",
                        "host=$host panel=$panel item=$item scroller=$scroller"
                    )
                }
            },
            "MixTapeMediaBridge"
        )
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.textZoom = 110
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
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                startNativePlaybackFromUrl(request.url.toString())
                return false
            }
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                syncAddress(view, url)
                startNativePlaybackFromUrl(url)
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
                // Acompanha a playlist em layouts desktop e mobile. A conclusão só é
                // registrada depois de confirmar que o item realmente chegou ao topo.
                view.evaluateJavascript(
                    """
                    (function() {
                      if (window.__mixTapePlaylistFollowV3) return;
                      window.__mixTapePlaylistFollowV3 = true;
                      var lastCompletedKey = null;
                      var retryKey = null;
                      var retries = 0;
                      var scheduled = false;
                      var lastDiagnostic = '';

                      function videoIdFromUrl(value) {
                        try { return new URL(value, location.href).searchParams.get('v'); }
                        catch (_) { return null; }
                      }

                      function report(panel, item, scroller) {
                        var state = [
                          location.host,
                          panel ? panel.tagName.toLowerCase() : 'no-panel',
                          item ? item.tagName.toLowerCase() : 'no-item',
                          scroller ? (scroller.id || scroller.tagName.toLowerCase()) : 'no-scroll'
                        ].join('|');
                        if (state === lastDiagnostic) return;
                        lastDiagnostic = state;
                        try {
                          window.MixTapeMediaBridge &&
                            window.MixTapeMediaBridge.onPlaylistDiagnostic(
                              location.host,
                              panel ? panel.tagName.toLowerCase() : 'none',
                              item ? item.tagName.toLowerCase() : 'none',
                              scroller ? (scroller.id || scroller.tagName.toLowerCase()) : 'none'
                            );
                        } catch (_) {}
                      }

                      function findCurrentItem() {
                        var id = videoIdFromUrl(location.href);
                        if (!id) return null;
                        var panel = document.querySelector(
                          'ytd-playlist-panel-renderer, ytm-playlist-panel-renderer, ' +
                          'ytm-playlist-video-list-renderer, ytd-playlist-video-list-renderer'
                        );
                        var selectors = [
                          'ytd-playlist-panel-video-renderer',
                          'ytm-playlist-video-renderer',
                          'ytm-playlist-panel-video-renderer',
                          'ytd-playlist-video-renderer'
                        ];
                        var renderers = [];
                        if (panel) {
                          selectors.forEach(function(selector) {
                            panel.querySelectorAll(selector).forEach(function(node) {
                              renderers.push(node);
                            });
                          });
                        }
                        for (var i = 0; i < renderers.length; i++) {
                          var links = renderers[i].querySelectorAll('a[href]');
                          for (var j = 0; j < links.length; j++) {
                            if (videoIdFromUrl(links[j].href) === id) {
                              return { item: renderers[i], panel: panel, key: id };
                            }
                          }
                          if (renderers[i].getAttribute('data-video-id') === id) {
                            return { item: renderers[i], panel: panel, key: id };
                          }
                        }
                        return { item: null, panel: panel, key: id };
                      }

                      function isScrollable(node) {
                        if (!node || node === document.body || node === document.documentElement) return false;
                        var style = getComputedStyle(node);
                        return /(auto|scroll|overlay)/.test(style.overflowY) &&
                          node.scrollHeight > node.clientHeight + 3 && node.clientHeight > 60;
                      }

                      function findScrollContainer(item, panel) {
                        var preferred = panel && panel.querySelector(
                          '#items, #items-container, #playlist-items, .playlist-items'
                        );
                        if (isScrollable(preferred)) return preferred;
                        var node = item;
                        while (node && node !== document.body) {
                          if (isScrollable(node)) return node;
                          node = node.parentElement;
                        }
                        node = panel;
                        while (node && node !== document.body) {
                          if (isScrollable(node)) return node;
                          node = node.parentElement;
                        }
                        return null;
                      }

                      function followCurrent(force) {
                        scheduled = false;
                        var current = findCurrentItem();
                        if (!current) return;
                        if (current.key !== retryKey) {
                          retryKey = current.key;
                          retries = 0;
                          lastCompletedKey = null;
                        }
                        if (!current.item) {
                          report(current.panel, null, null);
                          retry();
                          return;
                        }
                        var scroller = findScrollContainer(current.item, current.panel);
                        report(current.panel, current.item, scroller);
                        if (scroller) {
                          var itemRect = current.item.getBoundingClientRect();
                          var box = scroller.getBoundingClientRect();
                          var delta = itemRect.top - box.top - 8;
                          if (Math.abs(delta) > 14) {
                            scroller.scrollTo({
                              top: Math.max(0, scroller.scrollTop + delta),
                              behavior: 'smooth'
                            });
                          }
                          window.setTimeout(function() {
                            var after = current.item.getBoundingClientRect();
                            var visible = after.top >= box.top - 3 && after.top < box.bottom;
                            var aligned = Math.abs(after.top - box.top - 8) < 28;
                            if (visible && aligned) {
                              lastCompletedKey = current.key;
                              retries = 0;
                            } else {
                              retry();
                            }
                          }, 260);
                        } else {
                          current.item.scrollIntoView({ block: 'start', behavior: 'smooth' });
                          window.setTimeout(function() {
                            var rect = current.item.getBoundingClientRect();
                            if (rect.bottom > 0 && rect.top < window.innerHeight) {
                              lastCompletedKey = current.key;
                              retries = 0;
                            } else {
                              retry();
                            }
                          }, 300);
                        }
                      }

                      function retry() {
                        if (retries >= 15) return;
                        retries++;
                        window.setTimeout(function() { schedule(true); }, 450);
                      }

                      function schedule(force) {
                        if (scheduled) return;
                        if (!force && retryKey === lastCompletedKey) return;
                        scheduled = true;
                        requestAnimationFrame(function() { followCurrent(!!force); });
                      }

                      var observer = new MutationObserver(function() {
                        if (retryKey !== lastCompletedKey) schedule(false);
                      });
                      observer.observe(document.documentElement, {
                        subtree: true,
                        childList: true,
                        attributes: true,
                        attributeFilter: ['selected', 'aria-current', 'aria-selected', 'is-active', 'href', 'data-video-id']
                      });
                      window.addEventListener('yt-navigate-finish', function() {
                        retryKey = null;
                        lastCompletedKey = null;
                        retries = 0;
                        schedule(true);
                      });
                      window.addEventListener('yt-page-data-updated', function() {
                        retryKey = null;
                        lastCompletedKey = null;
                        retries = 0;
                        schedule(true);
                      });
                      document.addEventListener('click', function(event) {
                        var target = event.target;
                        if (target && target.closest &&
                            target.closest('ytd-playlist-panel-video-renderer, ytm-playlist-video-renderer, ytm-playlist-panel-video-renderer')) {
                          lastCompletedKey = null;
                          retries = 0;
                          window.setTimeout(function() { schedule(true); }, 350);
                          window.setTimeout(function() { schedule(true); }, 900);
                        }
                      }, true);
                      window.setInterval(function() {
                        if (retryKey !== lastCompletedKey) schedule(false);
                        else {
                          var current = findCurrentItem();
                          if (current && current.key !== lastCompletedKey) schedule(true);
                        }
                      }, 650);
                      schedule(true);
                    })();
                    """.trimIndent(),
                    null
                )
                // Monitora elementos HTML5 de áudio/vídeo. O serviço foreground mantém
                // o processo apto a continuar a mídia quando o app vai para segundo plano.
                view.evaluateJavascript(
                    """
                    (function() {
                      if (window.__mixTapeBrowserMediaMonitor) return;
                      window.__mixTapeBrowserMediaMonitor = true;
                      var lastPlaying = null;
                      var lastTitle = '';
                      var lastNativeVideoId = null;

                      function handoffYouTubeVideo() {
                        var host = location.hostname.toLowerCase();
                        if (host !== 'youtube.com' && !host.endsWith('.youtube.com')) return;
                        var videoId = null;
                        try { videoId = new URL(location.href).searchParams.get('v'); } catch (_) {}
                        if (!videoId || !/^[A-Za-z0-9_-]{11}$/.test(videoId)) return;
                        if (videoId !== lastNativeVideoId) {
                          lastNativeVideoId = videoId;
                          try {
                            if (window.MixTapeMediaBridge) {
                              window.MixTapeMediaBridge.onYouTubeVideoSelected(videoId);
                            }
                          } catch (_) {}
                        }
                        window.setTimeout(function() {
                          document.querySelectorAll('video').forEach(function(video) {
                            try { if (!video.paused) video.pause(); } catch (_) {}
                          });
                        }, 180);
                      }

                      window.addEventListener('yt-navigate-finish', function() {
                        window.setTimeout(handoffYouTubeVideo, 250);
                      });
                      window.addEventListener('yt-page-data-updated', function() {
                        window.setTimeout(handoffYouTubeVideo, 250);
                      });
                      document.addEventListener('playing', function(event) {
                        if (event.target && event.target.tagName === 'VIDEO') {
                          handoffYouTubeVideo();
                          window.setTimeout(function() {
                            try { event.target.pause(); } catch (_) {}
                          }, 120);
                        }
                      }, true);
                      window.setTimeout(handoffYouTubeVideo, 700);
                      window.setInterval(handoffYouTubeVideo, 1200);

                      function syncMediaState() {
                        var elements = document.querySelectorAll('video, audio');
                        var active = false;
                        for (var i = 0; i < elements.length; i++) {
                          var media = elements[i];
                          if (!media.paused && !media.ended && media.readyState >= 2) {
                            active = true;
                            break;
                          }
                        }
                        var title = (document.title || 'Mídia do navegador').slice(0, 120);
                        if (active !== lastPlaying || title !== lastTitle) {
                          lastPlaying = active;
                          lastTitle = title;
                          try {
                            if (window.MixTapeMediaBridge) {
                              window.MixTapeMediaBridge.onMediaStateChanged(active, title);
                            }
                          } catch (_) {}
                        }
                      }
                      document.addEventListener('play', syncMediaState, true);
                      document.addEventListener('playing', syncMediaState, true);
                      document.addEventListener('pause', function() { setTimeout(syncMediaState, 150); }, true);
                      document.addEventListener('ended', syncMediaState, true);
                      document.addEventListener('emptied', syncMediaState, true);
                      window.addEventListener('pagehide', function() {
                        try {
                          if (window.MixTapeMediaBridge) {
                            window.MixTapeMediaBridge.onMediaStateChanged(false, '');
                          }
                        } catch (_) {}
                      });
                      window.setInterval(syncMediaState, 1000);
                      syncMediaState();
                    })();
                    """.trimIndent(),
                    null
                )
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
        Card(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(Color(0xFF174E4B), Color(0xFF202B35), MaterialTheme.colorScheme.surface))
                ).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF58D8D0).copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center
                ) { Text("♫", color = Color(0xFF58D8D0), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("Central de Áudios YT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Encontre links e envie para baixar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onHome, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp), modifier = Modifier.height(34.dp)) { Text("⌂", style = MaterialTheme.typography.titleMedium) }
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ENCONTRAR ÁUDIO", style = MaterialTheme.typography.labelSmall, color = Color(0xFF58BFB8), fontWeight = FontWeight.Bold)
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
                    Text("QUALIDADE DO DOWNLOAD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
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
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Sua fila · $activeCount em andamento", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    queue.queue.takeLast(3).forEach { entry ->
                        Text("${entry.title} · ${entry.status}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        if (entry.status == "Baixando" || entry.status == "Importando") {
                            LinearProgressIndicator(progress = { entry.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        val controller = mediaController
        if (nativeVideoId != null && controller != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "PLAYER DO YOUTUBE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 10.dp, top = 6.dp)
                    )
                    AndroidView(
                        factory = { viewContext ->
                            PlayerView(viewContext).apply {
                                player = controller
                                useController = true
                                controllerShowTimeoutMs = 2500
                            }
                        },
                        update = { it.player = controller },
                        onRelease = { it.player = null },
                        modifier = Modifier.fillMaxWidth().height(190.dp)
                    )
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
