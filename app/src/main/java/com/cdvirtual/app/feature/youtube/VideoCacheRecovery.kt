package com.cdvirtual.app.feature.youtube

import com.cdvirtual.app.core.database.VideoCacheStatus
import java.io.File
import com.cdvirtual.app.core.storage.VideoCacheRecoveryFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Narrow persistence contract keeps startup recovery testable without Room or Android. */
interface VideoCacheRecoveryRepository {
    suspend fun all(): List<com.cdvirtual.app.core.database.VideoCacheEntity>
    suspend fun updateStatus(cacheId: String, status: String)
    suspend fun markReadyAfterPromotion(cacheId: String, publishedFile: File)
    suspend fun save(item: com.cdvirtual.app.core.database.VideoCacheEntity)
    suspend fun deleteRecordAfterFileRemoval(cacheId: String)
}

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
    private val repository: VideoCacheRecoveryRepository,
    private val fileStore: VideoCacheRecoveryFileStore
) {
    suspend fun recover(): VideoCacheRecoveryReport = withContext(Dispatchers.IO) {
        var interruptedDownloads = 0
        var pausedPlayback = 0
        var missingFiles = 0
        var completedItemsRemoved = 0

        val entries = repository.all()
        val validFiles = entries.associate { entry ->
            entry.cacheId to fileStore.resolveValidFile(entry.filePath, entry.sizeBytes)
        }
        val knownPaths = validFiles.values.filterNotNull().map { it.canonicalPath }.toSet()
        for (entry in entries) {
            val validFile = validFiles[entry.cacheId]

            when (entry.status) {
                VideoCacheStatus.QUEUED -> {
                    // Uma reserva que sobreviveu ao processo não tem mais um job dono.
                    // Mantém qualquer .part e libera retry/remoção manual.
                    repository.updateStatus(entry.cacheId, VideoCacheStatus.INTERRUPTED)
                    interruptedDownloads++
                }
                VideoCacheStatus.DOWNLOADING -> {
                    if (validFile != null) {
                        // O processo pode ter caído após o rename e antes do commit READY no Room.
                        repository.markReadyAfterPromotion(entry.cacheId, validFile)
                    } else {
                        repository.updateStatus(entry.cacheId, VideoCacheStatus.INTERRUPTED)
                        interruptedDownloads++
                    }
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
        val orphanStillExists = entry.filePath?.let { runCatching { File(it).isFile }.getOrDefault(false) } == true
        repository.save(entry.copy(
            // Preserva o caminho de um arquivo corrompido para que a remoção manual
            // consiga alcançá-lo; caminhos realmente ausentes podem ser limpos do registro.
            filePath = entry.filePath.takeIf { orphanStillExists },
            sizeBytes = if (orphanStillExists) entry.sizeBytes else 0L,
            status = VideoCacheStatus.INTERRUPTED,
            updatedAt = System.currentTimeMillis()
        ))
    }
}
