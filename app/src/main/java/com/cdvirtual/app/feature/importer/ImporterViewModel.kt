package com.cdvirtual.app.feature.importer

import android.app.Application
import android.net.Uri
import com.cdvirtual.app.CDVirtualApp
import com.cdvirtual.app.feature.library.ImportOutcome
import com.cdvirtual.app.media.connectors.ConnectorRegistry
import com.cdvirtual.app.media.connectors.SourceConnector
import com.cdvirtual.app.media.connectors.toUserMessage
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cdvirtual.app.core.common.AppResult
import com.cdvirtual.app.core.common.NetworkStatus
import com.cdvirtual.app.media.connectors.LinkInfo
import com.cdvirtual.app.media.connectors.MediaOption
import com.cdvirtual.app.media.connectors.Platform
import com.cdvirtual.app.media.connectors.PlatformDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val result: String? = null
)

class ImporterViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(ImporterUiState())
    val state: StateFlow<ImporterUiState> = _state.asStateFlow()
    private var analysisJob: Job? = null
    private var importJob: Job? = null
    private var activeConnector: SourceConnector? = null
    private val app = app as CDVirtualApp

    fun onInput(value: String) {
        analysisJob?.cancel()
        _state.update { ImporterUiState(input = value) }
    }

    fun cancel() {
        analysisJob?.cancel()
        importJob?.cancel()
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
        activeConnector = connector
        if (!NetworkStatus.isOnline(app)) { // 10.7: avisa logo, em vez de esperar o tempo esgotar
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
    }

    fun importSelected() {
        val option = _state.value.selected ?: return
        val connector = activeConnector ?: return
        if (!NetworkStatus.isOnline(app)) {
            _state.update { it.copy(error = NetworkStatus.OFFLINE_MESSAGE) }
            return
        }
        importJob?.cancel()
        importJob = viewModelScope.launch {
            _state.update { it.copy(importing = true, progress = 0f, result = null, error = null, notice = null) }
            val dir = File(app.filesDir, "imports").apply { mkdirs() }
            val target = File(dir, "${System.currentTimeMillis()}.${option.format.lowercase()}")
            try {
                connector.download(option, target) { done, total ->
                    if (total > 0) _state.update { it.copy(progress = done.toFloat() / total) }
                }
                when (val outcome = app.trackImporter.import(Uri.fromFile(target))) {
                    is ImportOutcome.Added -> _state.update { it.copy(result = "Música importada para a biblioteca") }
                    ImportOutcome.Duplicate -> {
                        target.delete()
                        _state.update { it.copy(result = "Esta música já está na biblioteca") }
                    }
                    is ImportOutcome.Failed -> {
                        target.delete()
                        _state.update { it.copy(error = "Arquivo inválido: ${outcome.reason}") }
                    }
                }
            } catch (ce: CancellationException) {
                target.delete()
                _state.update { it.copy(notice = "Importação cancelada") }
                throw ce
            } catch (t: Throwable) {
                target.delete()
                _state.update { it.copy(error = t.toUserMessage()) }
            } finally {
                _state.update { it.copy(importing = false) }
            }
        }
    }
}
