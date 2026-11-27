package com.cdvirtual.app.core.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VideoCacheRollbackTest {
    @Test
    fun rollbackMovesPublishedFileBackToPartial() {
        val directory = Files.createTempDirectory("video-cache").toFile()
        try {
            val published = File(directory, "cache-1.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }

            assertTrue(rollbackVideoCachePromotion(published, directory, "cache-1"))
            assertFalse(published.exists())
            assertTrue(File(directory, "cache-1.part").isFile)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rollbackPreservesBothFilesWhenPartialAlreadyExists() {
        val directory = Files.createTempDirectory("video-cache").toFile()
        try {
            val published = File(directory, "cache-2.webm").apply { writeBytes(byteArrayOf(1)) }
            val partial = File(directory, "cache-2.part").apply { writeBytes(byteArrayOf(2)) }

            assertFalse(rollbackVideoCachePromotion(published, directory, "cache-2"))
            assertTrue(published.isFile)
            assertTrue(partial.isFile)
            assertTrue(published.readBytes().contentEquals(byteArrayOf(1)))
            assertTrue(partial.readBytes().contentEquals(byteArrayOf(2)))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rollbackRejectsFileOutsideManagedDirectory() {
        val directory = Files.createTempDirectory("video-cache").toFile()
        val outside = Files.createTempFile("outside-cache", ".mp4").toFile()
        try {
            rollbackVideoCachePromotion(outside, directory, "cache-3")
        } finally {
            directory.deleteRecursively()
            outside.delete()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rollbackRejectsFileBelongingToAnotherCacheId() {
        val directory = Files.createTempDirectory("video-cache").toFile()
        try {
            val published = File(directory, "other.mp4").apply { writeBytes(byteArrayOf(1)) }
            rollbackVideoCachePromotion(published, directory, "cache-4")
        } finally {
            directory.deleteRecursively()
        }
    }
}
