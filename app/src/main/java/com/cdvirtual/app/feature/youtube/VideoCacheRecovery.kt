package com.cdvirtual.app.feature.youtube

import com.cdvirtual.app.core.database.VideoCacheStatus
import com.cdvirtual.app.core.storage.VideoCacheFileStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * Arquivos parciais e arquivos publicados sem registro íntegro nunca são apagados
 * automaticamente. Arquivos ausentes/corrompidos são desassociados do item e
 * reportados para que a interface possa orientar uma nova preparação.
 */
class VideoCacheRecovery(
    private val repository: VideoCacheRepository,
    private val fileStore: VideoCacheFileStore
) {
    suspend fun recover(): VideoCacheRecoveryReport = withContext(Dispatchers.IO) {
        var interruptedDownloads = 0
        var pausedPlayback = 0
        var missingFiles = 0
        var completedItemsRemoved = 0

        val entries = repository.all()
        val knownPaths = entries.mapNotNull { entry ->
            fileStore.resolveValidFile(entry.filePath, entry.sizeBytes)?.canonicalPath
        }.toSet()
        for (entry in entries) {
            val validFile = fileStore.resolveValidFile(entry.filePath, entry.sizeBytes)

            when (entry.status) {
                VideoCacheStatus.QUEUED -> {
                    // Uma reserva que sobreviveu ao processo não tem mais um job dono.
                    // Mantém qualquer .part e libera retry/remoção manual.
                    repository.updateStatus(entry.cacheId, VideoCacheStatus.INTERRUPTED)
                    interruptedDownloads++
                }
                VideoCacheStatus.DOWNLOADING -> {
                    repository.updateStatus(entry.cacheId, VideoCacheStatus.INTERRUPTED)
                    interruptedDownloads++
                }
                VideoCacheStatus.PLAYING -> {
                    if (validFile != null) {
                        repository.updateStatus(entry.cacheId, VideoCacheStatus.PAUSED)
                        pausedPlayback++
                    } else {
                        markMissing(entry)
                        missingFiles++
                    }
                }
                VideoCacheStatus.READY, VideoCacheStatus.PAUSED -> {
                    if (validFile == null) {
                        markMissing(entry)
                        missingFiles++
                    }
                }
                VideoCacheStatus.INTERRUPTED -> {
                    if (validFile != null) {
                        repository.markReadyAfterPromotion(entry.cacheId, validFile)
                    }
                }
                VideoCacheStatus.COMPLETED, VideoCacheStatus.REMOVED -> {
                    // A conclusão/remoção já foi solicitada. Final e parcial são
                    // limpos antes do registro; falhas mantêm o registro para retry.
                    val finalRemoved = entry.filePath.isNullOrBlank() ||
                        fileStore.deleteManagedFile(entry.filePath)
                    val partialRemoved = fileStore.deletePartialFile(entry.cacheId)
                    if (finalRemoved && partialRemoved) {
                        repository.deleteRecordAfterFileRemoval(entry.cacheId)
                        if (entry.status == VideoCacheStatus.COMPLETED) completedItemsRemoved++
                    }
                }
            }
        }

        // Somente arquivos íntegros referenciados por registros são considerados conhecidos.
        // Um arquivo corrompido permanece no disco, mas aparece como órfão para inspeção.
        val orphanedFiles = fileStore.listPublishedFiles()
            .filter { file -> runCatching { file.canonicalPath }.getOrNull() !in knownPaths }
            .map { it.absolutePath }

        VideoCacheRecoveryReport(
            interruptedDownloads = interruptedDownloads,
            pausedPlayback = pausedPlayback,
            missingFiles = missingFiles,
            completedItemsRemoved = completedItemsRemoved,
            orphanedFiles = orphanedFiles,
            partialFiles = fileStore.listPartialFiles().map { it.absolutePath }
        )
    }

    private suspend fun markMissing(entry: com.cdvirtual.app.core.database.VideoCacheEntity) {
        repository.save(entry.copy(
            filePath = null,
            sizeBytes = 0L,
            status = VideoCacheStatus.INTERRUPTED,
            updatedAt = System.currentTimeMillis()
        ))
    }
}
