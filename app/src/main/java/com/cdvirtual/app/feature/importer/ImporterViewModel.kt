package com.cdvirtual.app.feature.importer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cdvirtual.app.CDVirtualApp
import com.cdvirtual.app.core.common.AppResult
import com.cdvirtual.app.core.common.NetworkStatus
import com.cdvirtual.app.feature.library.ImportOutcome
import com.cdvirtual.app.media.connectors.ConnectorRegistry
import com.cdvirtual.app.media.connectors.LinkInfo
import com.cdvirtual.app.media.connectors.MediaOption
import com.cdvirtual.app.media.connectors.Platform
import com.cdvirtual.app.media.connectors.PlatformDetector
import com.cdvirtual.app.media.connectors.SourceConnector
import com.cdvirtual.app.media.connectors.toUserMessage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadQueueEntry(
    val id: String,
    val title: String,
    val format: String,
    val status: String = "Na fila",
    val progress: Float = 0f,
    val detail: String? = null
)

private data class PendingImport(
    val id: String,
    val connector: SourceConnector,
    val option: MediaOption,
    val title: String?
)

data class ImporterUiState(
    val input: String = "",
    val error: String? = null,
    val validUrl: String? = null,
    val platform: Platform? = null,
    val analyzing: Boolean = false,
    val info: LinkInfo? = null,
    val selected: MediaOption? = null,
    val notice: String? = null,
    val importing: Boolean = false,
    val progress: Float = 0f,
    val result: String? = null,
    val queue: List<DownloadQueueEntry> = emptyList()
)

class ImporterViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(ImporterUiState())
    val state: StateFlow<ImporterUiState> = _state.asStateFlow()
    private var analysisJob: Job? = null
    private var queueWorker: Job? = null
    private var activeItemJob: Job? = null
    private var activeItemId: String? = null
    private val pending = ArrayDeque<PendingImport>()
    private val queueItems = mutableMapOf<String, PendingImport>()
    private val autoAnalysisJobs = mutableMapOf<String, Job>()
    private val app = app as CDVirtualApp

    fun onInput(value: String) {
        analysisJob?.cancel()
        _state.update { it.copy(input = value, error = null) }
    }

    fun cancel() {
        analysisJob?.cancel()
        activeItemJob?.cancel()
        queueWorker?.cancel()
    }

    fun select(option: MediaOption) {
        _state.update { it.copy(selected = option) }
    }

    fun submit() {
        val url = when (val r = UrlInput.parse(_state.value.input)) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> {
                _state.update { it.copy(error = r.message, validUrl = null, platform = null, info = null, selected = null, notice = null) }
                return
            }
        }
        val platform = PlatformDetector.detect(url)
        _state.update { it.copy(error = null, validUrl = url.toString(), platform = platform, info = null, selected = null, notice = null) }
        val connector = ConnectorRegistry.default.forPlatform(platform)
        if (connector == null) {
            _state.update { it.copy(notice = "Conector para ${platform.label} ainda não disponível.") }
            return
        }
        if (!NetworkStatus.isOnline(app)) {
            _state.update { it.copy(error = NetworkStatus.OFFLINE_MESSAGE) }
            return
        }
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _state.update { it.copy(analyzing = true, notice = null) }
            try {
                val info = connector.analyze(url)
                _state.update { it.copy(info = info, selected = info.options.firstOrNull()) }
            } catch (ce: CancellationException) {
                _state.update { it.copy(notice = "Análise cancelada") }
                throw ce
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.toUserMessage()) }
            } finally {
                _state.update { it.copy(analyzing = false) }
            }
        }
        activeConnector = connector
    }

    /**
     * Analisa um link recebido do navegador, escolhe o áudio conforme a preferência global
     * e inicia o download sem exigir uma segunda confirmação.
     */
    fun importUrlAutomatically(rawUrl: String, preferredFormat: String) {
        onInput(rawUrl)
        val url = when (val parsed = UrlInput.parse(rawUrl)) {
            is AppResult.Success -> parsed.value
            is AppResult.Failure -> {
                _state.update { it.copy(error = parsed.message) }
                return
            }
        }
        val platform = PlatformDetector.detect(url)
        val connector = ConnectorRegistry.default.forPlatform(platform)
        if (connector == null) {
            _state.update { it.copy(platform = platform, error = "Conector para ${platform.label} ainda não disponível.") }
            return
        }
        if (!NetworkStatus.isOnline(app)) {
            _state.update { it.copy(error = NetworkStatus.OFFLINE_MESSAGE) }
            return
        }
        analysisJob?.cancel()
        activeConnector = connector
        analysisJob = viewModelScope.launch {
            _state.update { it.copy(analyzing = true, error = null, validUrl = url.toString(), platform = platform, notice = "Analisando opções de áudio…") }
            try {
                val info = connector.analyze(url)
                val best = info.options.maxWithOrNull(
                    compareBy<MediaOption> { it.bitrateKbps ?: 0 }
                        .thenBy { it.sizeBytes }
                )
                val preferred = when (preferredFormat.lowercase()) {
                    "webm", "opus" -> info.options.firstOrNull {
                        it.format.equals("webm", true) || it.mimeType?.contains("opus", true) == true
                    }
                    "m4a", "aac" -> info.options.firstOrNull {
                        it.format.equals("m4a", true) || it.format.equals("aac", true) ||
                            it.mimeType?.contains("mp4", true) == true || it.mimeType?.contains("aac", true) == true
                    }
                    else -> best
                }
                val selected = preferred ?: best
                if (selected == null) {
                    _state.update { it.copy(error = "Nenhuma opção de áudio compatível foi encontrada.", info = info) }
                } else {
                    _state.update { it.copy(info = info, selected = selected, notice = "Selecionado automaticamente: ${selected.label}") }
                    importSelected()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.toUserMessage()) }
            } finally {
                _state.update { it.copy(analyzing = false) }
            }
        }
    }

    private var activeConnector: SourceConnector? = null

    /**
     * Enfileira um link vindo do navegador sem navegar para outra tela.
     * Cada análise tem seu próprio Job, portanto enviar um segundo link não cancela o primeiro.
     */
    fun enqueueUrlAutomatically(rawUrl: String, preferredFormat: String) {
        val url = when (val parsed = UrlInput.parse(rawUrl)) {
            is AppResult.Success -> parsed.value
            is AppResult.Failure -> {
                _state.update { it.copy(error = parsed.message) }
                return
            }
        }
        val platform = PlatformDetector.detect(url)
        val connector = ConnectorRegistry.default.forPlatform(platform)
        if (connector == null) {
            _state.update { it.copy(notice = "Conector para ${platform.label} ainda não disponível.") }
            return
        }
        if (!NetworkStatus.isOnline(app)) {
            _state.update { it.copy(error = NetworkStatus.OFFLINE_MESSAGE) }
            return
        }
        val id = UUID.randomUUID().toString()
        _state.update { state ->
            state.copy(
                error = null,
                notice = "Analisando novo link…",
                queue = state.queue + DownloadQueueEntry(id, "Analisando link…", preferredFormat.uppercase(), "Analisando")
            )
        }
        val job = app.downloadScope.launch {
            try {
                val info = connector.analyze(url)
                val best = info.options.maxWithOrNull(
                    compareBy<MediaOption> { it.bitrateKbps ?: 0 }.thenBy { it.sizeBytes }
                )
                val preferred = when (preferredFormat.lowercase()) {
                    "webm", "opus" -> info.options.firstOrNull {
                        it.format.equals("webm", true) || it.mimeType?.contains("opus", true) == true
                    }
                    "m4a", "aac" -> info.options.firstOrNull {
                        it.format.equals("m4a", true) || it.format.equals("aac", true) ||
                            it.mimeType?.contains("mp4", true) == true || it.mimeType?.contains("aac", true) == true
                    }
                    else -> best
                }
                val selected = preferred ?: best
                if (selected == null) {
                    updateQueue(id, status = "Falhou", title = info.fileName ?: "Áudio sem opções", detail = "Nenhuma opção de áudio compatível foi encontrada.")
                } else {
                    val item = PendingImport(id, connector, selected, info.fileName)
                    queueItems[id] = item
                    pending.addLast(item)
                    updateQueue(
                        id,
                        status = "Na fila",
                        title = info.fileName?.takeIf { it.isNotBlank() } ?: "Áudio ${id.take(8)}",
                        format = selected.format.uppercase(),
                        detail = null
                    )
                    _state.update { it.copy(notice = "Download adicionado à fila. Continue navegando.") }
                    startQueueWorker()
                }
            } catch (ce: CancellationException) {
                updateQueue(id, status = "Cancelado", detail = "Análise cancelada")
                throw ce
            } catch (t: Throwable) {
                updateQueue(id, status = "Falhou", detail = t.toUserMessage())
            } finally {
                autoAnalysisJobs.remove(id)
            }
        }
        autoAnalysisJobs[id] = job
    }

    /** Adiciona o formato escolhido à fila e libera imediatamente a busca para o próximo link. */
    fun importSelected() {
        val current = _state.value
        val option = current.selected ?: return
        val connector = activeConnector ?: return
        if (!NetworkStatus.isOnline(app)) {
            _state.update { it.copy(error = NetworkStatus.OFFLINE_MESSAGE) }
            return
        }
        val id = UUID.randomUUID().toString()
        val title = current.info?.fileName
        val item = PendingImport(id, connector, option, title)
        queueItems[id] = item
        pending.addLast(item)
        val displayTitle = title?.takeIf { it.isNotBlank() } ?: "Áudio ${id.take(8)}"
        _state.update {
            it.copy(
                input = "",
                error = null,
                validUrl = null,
                platform = null,
                analyzing = false,
                info = null,
                selected = null,
                notice = "Adicionado à fila de downloads",
                importing = true,
                progress = 0f,
                result = null,
                queue = it.queue + DownloadQueueEntry(id, displayTitle, option.format.uppercase())
            )
        }
        startQueueWorker()
    }

    /** Tenta novamente um item que falhou ou foi cancelado, mantendo a opção escolhida. */
    fun retryDownload(id: String) {
        val entry = _state.value.queue.firstOrNull { it.id == id } ?: return
        if (entry.status != "Falhou" && entry.status != "Cancelado") return
        val item = queueItems[id] ?: return
        if (pending.any { it.id == id } || activeItemId == id) return
        pending.addLast(item)
        updateQueue(id, status = "Na fila", progress = 0f, detail = null)
        _state.update { it.copy(importing = true, notice = "Download reenfileirado para nova tentativa") }
        startQueueWorker()
    }

    /** Remove apenas o registro da fila. Se estiver ativo, cancela o trabalho antes de removê-lo. */
    fun removeDownload(id: String) {
        autoAnalysisJobs.remove(id)?.cancel()
        pending.removeAll { it.id == id }
        if (activeItemId == id) activeItemJob?.cancel()
        queueItems.remove(id)
        _state.update { state ->
            state.copy(
                queue = state.queue.filterNot { it.id == id },
                importing = pending.isNotEmpty() || (activeItemId != null && activeItemId != id)
            )
        }
        if (pending.isEmpty() && activeItemJob == null) {
            _state.update { it.copy(importing = false) }
        }
    }

    /** Limpa o histórico visual, sem apagar arquivos já importados na Biblioteca. */
    fun clearQueue() {
        autoAnalysisJobs.values.toList().forEach { it.cancel() }
        autoAnalysisJobs.clear()
        pending.clear()
        queueItems.clear()
        activeItemJob?.cancel()
        _state.update { it.copy(queue = emptyList(), importing = activeItemJob?.isActive == true, progress = 0f, notice = "Lista limpa. As músicas da Biblioteca foram preservadas.") }
    }

    private fun startQueueWorker() {
        if (queueWorker?.isActive == true) return
        com.cdvirtual.app.media.service.DownloadService.start(app)
        queueWorker = app.downloadScope.launch {
            while (pending.isNotEmpty()) {
                val item = pending.removeFirst()
                activeItemId = item.id
                activeItemJob = launch { processItem(item) }
                activeItemJob?.join()
                activeItemJob = null
                activeItemId = null
                _state.update { it.copy(importing = pending.isNotEmpty(), progress = 0f) }
            }
            _state.update { it.copy(importing = false) }
            com.cdvirtual.app.media.service.DownloadService.finish(app)
        }
    }

    private suspend fun processItem(item: PendingImport) {
        updateQueue(item.id, status = "Baixando", progress = 0f, detail = null)
        _state.update { it.copy(importing = true, progress = 0f) }
        val safeName = (item.title ?: "audio-${item.id.take(8)}")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(90)
            .ifBlank { "audio-${item.id.take(8)}" }
        val dir = File(app.filesDir, "imports").apply { mkdirs() }
        val target = File(dir, "${safeName}-${item.id.take(8)}.${item.option.format.lowercase()}")
        try {
            item.connector.download(item.option, target) { done, total ->
                val fraction = if (total > 0L) (done.toFloat() / total).coerceIn(0f, 0.99f) else 0f
                _state.update { it.copy(progress = fraction) }
                updateQueue(item.id, progress = fraction)
            }
            _state.update { it.copy(progress = 1f) }
            updateQueue(item.id, status = "Importando", progress = 1f)
            when (val outcome = app.trackImporter.import(Uri.fromFile(target), item.title)) {
                is ImportOutcome.Added -> {
                    updateQueue(item.id, status = "Concluído", progress = 1f)
                    _state.update { it.copy(result = "${item.title ?: "Áudio"} importado para a biblioteca") }
                }
                ImportOutcome.Duplicate -> {
                    target.delete()
                    updateQueue(item.id, status = "Duplicado", progress = 1f, detail = "Já está na biblioteca")
                }
                is ImportOutcome.Failed -> {
                    target.delete()
                    updateQueue(item.id, status = "Falhou", detail = outcome.reason)
                }
            }
        } catch (ce: CancellationException) {
            target.delete()
            updateQueue(item.id, status = "Cancelado", detail = "Download cancelado")
        } catch (t: Throwable) {
            target.delete()
            updateQueue(item.id, status = "Falhou", detail = t.toUserMessage())
        } finally {
            _state.update { it.copy(progress = 0f) }
        }
    }

    private fun updateQueue(
        id: String,
        status: String? = null,
        progress: Float? = null,
        detail: String? = null,
        title: String? = null,
        format: String? = null
    ) {
        val current = _state.value.queue.firstOrNull { it.id == id }
        if (current != null) {
            com.cdvirtual.app.media.service.DownloadService.update(
                app,
                "${current.title}: ${status ?: current.status}",
                progress ?: current.progress
            )
        }
        _state.update { state ->
            state.copy(queue = state.queue.map { entry ->
                if (entry.id != id) entry else entry.copy(
                    status = status ?: entry.status,
                    progress = progress ?: entry.progress,
                    title = title ?: entry.title,
                    format = format ?: entry.format,
                    detail = detail
                )
            })
        }
    }
}
