package com.cdvirtual.app.feature.youtube

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VideoCachePublicationIntegrationTest {
    @Test
    fun failedReadyPersistenceRollsPublishedFileBackToPartial() = runBlocking {
        val directory = Files.createTempDirectory("video-publication").toFile()
        try {
            val partial = File(directory, "cache-1.part").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val published = File(directory, "cache-1.mp4")
            val failure = runCatching {
                publishVideoCacheFileWithRollback(
                    promote = {
                        check(partial.renameTo(published))
                        published
                    },
                    markReady = { throw IllegalStateException("Room write failed") },
                    readyWasPersisted = { false },
                    rollback = { file -> check(file.renameTo(partial)) }
                )
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertTrue(partial.isFile)
            assertFalse(published.exists())
            assertTrue(partial.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun successfulPublicationKeepsPublishedFile() = runBlocking {
        val directory = Files.createTempDirectory("video-publication").toFile()
        try {
            val partial = File(directory, "cache-2.part").apply { writeBytes(byteArrayOf(4, 5, 6)) }
            val published = File(directory, "cache-2.mp4")
            val result = publishVideoCacheFileWithRollback(
                promote = {
                    check(partial.renameTo(published))
                    published
                },
                markReady = { file -> assertTrue(file.isFile) },
                readyWasPersisted = { true },
                rollback = { error("Rollback must not run after READY") }
            )

            assertTrue(result.isFile)
            assertFalse(partial.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cancellationAfterReadyCommitDoesNotRollbackPublishedFile() = runBlocking {
        val directory = Files.createTempDirectory("video-publication").toFile()
        try {
            val partial = File(directory, "cache-3.part").apply { writeBytes(byteArrayOf(7, 8, 9)) }
            val published = File(directory, "cache-3.webm")
            val failure = runCatching {
                publishVideoCacheFileWithRollback(
                    promote = {
                        check(partial.renameTo(published))
                        published
                    },
                    markReady = { throw kotlinx.coroutines.CancellationException("cancel after commit") },
                    readyWasPersisted = { true },
                    rollback = { error("Committed READY file must be preserved") }
                )
            }.exceptionOrNull()

            assertTrue(failure is kotlinx.coroutines.CancellationException)
            assertTrue(published.isFile)
            assertFalse(partial.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
