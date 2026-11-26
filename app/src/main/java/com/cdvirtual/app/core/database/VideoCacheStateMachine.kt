package com.cdvirtual.app.core.database

/**
 * Máquina de estados do cache. A saída de um vídeo para outro ou a pausa não conclui
 * a visualização e, portanto, nunca autoriza a remoção automática do arquivo.
 */
object VideoCacheStateMachine {
    private val transitions = mapOf(
        VideoCacheStatus.QUEUED to setOf(VideoCacheStatus.DOWNLOADING, VideoCacheStatus.REMOVED),
        VideoCacheStatus.DOWNLOADING to setOf(VideoCacheStatus.READY, VideoCacheStatus.INTERRUPTED, VideoCacheStatus.REMOVED),
        VideoCacheStatus.INTERRUPTED to setOf(VideoCacheStatus.DOWNLOADING, VideoCacheStatus.READY, VideoCacheStatus.REMOVED),
        VideoCacheStatus.READY to setOf(VideoCacheStatus.PLAYING, VideoCacheStatus.INTERRUPTED, VideoCacheStatus.REMOVED),
        VideoCacheStatus.PLAYING to setOf(VideoCacheStatus.PAUSED, VideoCacheStatus.READY, VideoCacheStatus.INTERRUPTED, VideoCacheStatus.COMPLETED, VideoCacheStatus.REMOVED),
        VideoCacheStatus.PAUSED to setOf(VideoCacheStatus.PLAYING, VideoCacheStatus.INTERRUPTED, VideoCacheStatus.REMOVED),
        VideoCacheStatus.COMPLETED to setOf(VideoCacheStatus.REMOVED),
        VideoCacheStatus.REMOVED to emptySet()
    )

    fun canTransition(from: String, to: String): Boolean {
        if (!VideoCacheStatus.isKnown(from) || !VideoCacheStatus.isKnown(to)) return false
        if (from == to) return true
        return transitions[from]?.contains(to) == true
    }
}
