package com.cdvirtual.app.media.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data class Running(val cdId: Long, val cdName: String, val phase: String, val progress: Float) : ExportStatus
    data class Done(val cdId: Long, val cdName: String, val path: String, val sizeBytes: Long, val durationMs: Long) : ExportStatus
    data class Cancelled(val cdId: Long, val cdName: String) : ExportStatus
    data class Failed(val cdId: Long, val cdName: String, val message: String) : ExportStatus
}

/** Estado observável da geração do MP3, compartilhado entre o serviço em segundo plano e as telas. */
object ExportState {
    private val _status = MutableStateFlow<ExportStatus>(ExportStatus.Idle)
    val status: StateFlow<ExportStatus> = _status.asStateFlow()
    fun set(s: ExportStatus) { _status.value = s }
    val isRunning get() = _status.value is ExportStatus.Running
}
