package com.cdvirtual.app.feature.youtube

import com.cdvirtual.app.core.database.VideoCacheEntity
import com.cdvirtual.app.core.database.VideoCacheStatus
import com.cdvirtual.app.core.storage.VideoCacheRecoveryFileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VideoCacheRecoveryTest {
    @Test
    fun queuedAndDownloadingEntriesBecomeInterruptedAndKeepPartialFiles() = runBlocking {
        val queued = item("queued", VideoCacheStatus.QUEUED)
        val downloading = item("downloading", VideoCacheStatus.DOWNLOADING)
        val partial = File("/cache/downloading.part")
        val repository = FakeRepository(listOf(queued, downloading))
        val files = FakeFiles(partials = listOf(partial))

        val report = VideoCacheRecovery(repository, files).recover()

        assertEquals(VideoCacheStatus.INTERRUPTED, repository.items.getValue("queued").status)
        assertEquals(VideoCacheStatus.INTERRUPTED, repository.items.getValue("downloading").status)
        assertEquals(2, report.interruptedDownloads)
        assertEquals(listOf(partial.absolutePath), report.partialFiles)
        assertTrue(files.deletedPartials.isEmpty())
    }

    @Test
    fun playingEntryIsPausedWhenItsPublishedFileIsValid() = runBlocking {
        val valid = Files.createTempFile("playing-video-cache", ".mp4").toFile()
        try {
            valid.writeBytes(ByteArray(12) { it.toByte() })
            val entry = item("playing", VideoCacheStatus.PLAYING, valid.path, 12)
            val repository = FakeRepository(listOf(entry))
            val files = FakeFiles(validFiles = mapOf(valid.path to valid))

            val report = VideoCacheRecovery(repository, files).recover()

            assertEquals(VideoCacheStatus.PAUSED, repository.items.getValue("playing").status)
            assertEquals(1, report.pausedPlayback)
            assertTrue(report.orphanedFiles.isEmpty())
        } finally {
            valid.delete()
        }
    }

    @Test
    fun missingAndCorruptFilesAreReportedWithoutDeletingThem() = runBlocking {
        val missing = item("missing", VideoCacheStatus.READY, "/cache/missing.mp4", 20)
        val corruptFile = Files.createTempFile("corrupt-video-cache", ".mp4").toFile()
        try {
            corruptFile.writeBytes(byteArrayOf(1, 2, 3))
            val corrupt = item("corrupt", VideoCacheStatus.READY, corruptFile.path, 30)
            val repository = FakeRepository(listOf(missing, corrupt))
            val files = FakeFiles(
                published = listOf(corruptFile),
                validFiles = emptyMap()
            )

            val report = VideoCacheRecovery(repository, files).recover()

            assertEquals(VideoCacheStatus.INTERRUPTED, repository.items.getValue("missing").status)
            assertNull(repository.items.getValue("missing").filePath)
            assertEquals(VideoCacheStatus.INTERRUPTED, repository.items.getValue("corrupt").status)
            assertEquals(corruptFile.path, repository.items.getValue("corrupt").filePath)
            assertEquals(listOf(corruptFile.path), report.orphanedFiles)
            assertEquals(2, report.missingFiles)
            assertTrue(files.deletedPublished.isEmpty())
            assertTrue("O arquivo corrompido deve ser preservado para remoção manual.", corruptFile.isFile)
        } finally {
            corruptFile.delete()
        }
    }

    @Test
    fun completedEntryDeletesFilesBeforeRemovingItsRecord() = runBlocking {
        val entry = item("completed", VideoCacheStatus.COMPLETED, "/cache/completed.mp4", 40)
        val repository = FakeRepository(listOf(entry))
        val files = FakeFiles()

        val report = VideoCacheRecovery(repository, files).recover()

        assertFalse(repository.items.containsKey("completed"))
        assertEquals(listOf("/cache/completed.mp4"), files.deletedPublished)
        assertEquals(listOf("completed"), files.deletedPartials)
        assertEquals(1, report.completedItemsRemoved)
    }

    @Test
    fun failedFileDeletionKeepsRemovalRecordForRetry() = runBlocking {
        val entry = item("removed", VideoCacheStatus.REMOVED, "/cache/removed.mp4", 50)
        val repository = FakeRepository(listOf(entry))
        val files = FakeFiles(deletePublishedResult = false)

        VideoCacheRecovery(repository, files).recover()

        assertTrue(repository.items.containsKey("removed"))
        assertEquals(listOf("/cache/removed.mp4"), files.deletedPublished)
    }

    private fun item(
        id: String,
        status: String,
        path: String? = null,
        size: Long = 0L
    ) = VideoCacheEntity(
        cacheId = id,
        sourceId = "test",
        sourceVideoId = id,
        title = "Vídeo $id",
        status = status,
        filePath = path,
        sizeBytes = size
    )

    private class FakeRepository(entries: List<VideoCacheEntity>) : VideoCacheRecoveryRepository {
        val items = entries.associateBy { it.cacheId }.toMutableMap()

        override suspend fun all() = items.values.toList()

        override suspend fun updateStatus(cacheId: String, status: String) {
            items[cacheId] = items.getValue(cacheId).copy(status = status)
        }

        override suspend fun markReadyAfterPromotion(cacheId: String, publishedFile: File) {
            items[cacheId] = items.getValue(cacheId).copy(
                status = VideoCacheStatus.READY,
                filePath = publishedFile.path,
                sizeBytes = publishedFile.length()
            )
        }

        override suspend fun save(item: VideoCacheEntity) {
            items[item.cacheId] = item
        }

        override suspend fun deleteRecordAfterFileRemoval(cacheId: String) {
            items.remove(cacheId)
        }
    }

    private class FakeFiles(
        private val published: List<File> = emptyList(),
        private val partials: List<File> = emptyList(),
        private val validFiles: Map<String, File> = emptyMap(),
        private val deletePublishedResult: Boolean = true
    ) : VideoCacheRecoveryFileStore {
        val deletedPublished = mutableListOf<String>()
        val deletedPartials = mutableListOf<String>()

        override fun resolveValidFile(path: String?, expectedBytes: Long): File? =
            path?.let(validFiles::get)?.takeIf { expectedBytes <= 0L || it.length() == expectedBytes }

        override fun deleteManagedFile(path: String?): Boolean {
            if (path == null) return false
            deletedPublished += path
            return deletePublishedResult
        }

        override fun deletePartialFile(cacheId: String): Boolean {
            deletedPartials += cacheId
            return true
        }

        override fun listPublishedFiles() = published
        override fun listPartialFiles() = partials
    }
}
