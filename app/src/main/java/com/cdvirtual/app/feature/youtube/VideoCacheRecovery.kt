package com.cdvirtual.app.feature.youtube

import com.cdvirtual.app.core.database.VideoCacheStatus
import com.cdvirtual.app.core.storage.VideoCacheFileStore

data class VideoCacheRecoveryReport(
    val interruptedDownloads: Int,
    val pausedPlayback: Int,
    val missingFiles: Int,
    val completedItemsRemoved: Int,
    val orphanedFiles: List<String>,
    val partialFiles: List<String>
)

/**
 * Reconcilia Room e arquivos no início do app.
 *
 * Nunca apaga arquivos .part nem arquivos publicados sem registro: ambos são reportados
 * para decisão posterior do gerenciador, pois podem representar downloads retomáveis ou
 * um arquivo completo cujo registro ainda não foi persistido.
 */
class VideoCacheRecovery(
    private val repository: VideoCacheRepository,
    private val fileStore: VideoCacheFileStore
) {
    suspend fun recover(): VideoCacheRecoveryReport {
        var interruptedDownloads = 0
        var pausedPlayback = 0
        var missingFiles = 0
        var completedItemsRemoved = 0

        val entries = repository.all()
        val knownPaths = entries.mapNotNull { it.filePath }
            .mapNotNull { path -> runCatching { java.io.File(path).canonicalPath }.getOrNull() }
            .toSet()

        for (entry in entries) {
            val validFile = fileStore.resolveValidFile(entry.filePath, entry.sizeBytes)
            when (entry.status) {
                VideoCacheStatus.DOWNLOADING -> {
                    repository.updateStatus(entry.cacheId, VideoCacheStatus.INTERRUPTED)
                    interruptedDownloads++
                }
                VideoCacheStatus.PLAYING -> {
                    if (validFile != null) {
                        repository.updateStatus(entry.cacheId, VideoCacheStatus.PAUSED)
                        pausedPlayback++
                    } else {
                        repository.updateStatus(entry.cacheId, VideoCacheStatus.INTERRUPTED)
                        repository.save(entry.copy(
                            filePath = null,
                            sizeBytes = 0L,
                            status = VideoCacheStatus.INTERRUPTED,
                            updatedAt = System.currentTimeMillis()
                        ))
                        missingFiles++
                    }
                }
                VideoCacheStatus.READY, VideoCacheStatus.PAUSED -> {
                    if (validFile == null) {
                        repository.updateStatus(entry.cacheId, VideoCacheStatus.INTERRUPTED)
                        repository.save(entry.copy(
                            filePath = null,
                            sizeBytes = 0L,
                            status = VideoCacheStatus.INTERRUPTED,
                            updatedAt = System.currentTimeMillis()
                        ))
                        missingFiles++
                    }
                }
                VideoCacheStatus.INTERRUPTED -> {
                    if (validFile != null) {
                        repository.markReadyAfterPromotion(entry.cacheId, validFile)
                    }
                }
                VideoCacheStatus.COMPLETED, VideoCacheStatus.REMOVED -> {
                    val fileRemoved = entry.filePath.isNullOrBlank() ||
                        fileStore.deleteManagedFile(entry.filePath)
                    if (fileRemoved) {
                        repository.deleteRecordAfterFileRemoval(entry.cacheId)
                        if (entry.status == VideoCacheStatus.COMPLETED) completedItemsRemoved++
                    }
                }
            }
        }

        val orphanedFiles = fileStore.listPublishedFiles()
            .filter { file ->
                runCatching { file.canonicalPath }.getOrNull() !in knownPaths
            }
            .map { it.absolutePath }

        return VideoCacheRecoveryReport(
            interruptedDownloads = interruptedDownloads,
            pausedPlayback = pausedPlayback,
            missingFiles = missingFiles,
            completedItemsRemoved = completedItemsRemoved,
            orphanedFiles = orphanedFiles,
            partialFiles = fileStore.listPartialFiles().map { it.absolutePath }
        )
    }
}
