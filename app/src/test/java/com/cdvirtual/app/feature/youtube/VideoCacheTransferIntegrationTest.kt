package com.cdvirtual.app.feature.youtube

import com.cdvirtual.app.core.storage.VideoCacheTransferFileStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class VideoCacheTransferIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var directory: File
    private lateinit var client: OkHttpClient

    private class TemporaryFileStore(private val directory: File) : VideoCacheTransferFileStore {
        override fun getOrCreatePartialFile(cacheId: String): File =
            File(directory, "$cacheId.part")
    }

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("video-transfer-it").toFile()
        val heldCertificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(heldCertificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(heldCertificate.certificate)
            .build()
        server = MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory(), false)
            start()
        }
        client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .hostnameVerifier { _, _ -> true }
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
        directory.deleteRecursively()
    }

    private fun request(expectedBytes: Long = 8L) = VideoCacheTransferRequest(
        mediaUrl = server.url("/authorized-video").toString(),
        mimeType = "video/mp4",
        extension = "mp4",
        expectedBytes = expectedBytes
    )

    @Test
    fun realTransferAppendsOnlyTheRequestedContentRange() = runBlocking {
        val partial = File(directory, "resume.part").apply { writeText("abcd") }
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 4-7/8")
                .setHeader("Content-Type", "video/mp4")
                .setBody("efgh")
        )

        val result = VideoCacheTransfer(TemporaryFileStore(directory), client)
            .download("resume", request())

        assertEquals("abcdefgh", result.readText())
        assertEquals("bytes=4-", server.takeRequest().getHeader("Range"))
    }

    @Test
    fun realTransferRejectsMismatchedContentRangeAndPreservesPartial() = runBlocking {
        val partial = File(directory, "mismatch.part").apply { writeText("keep-me") }
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-3/8")
                .setHeader("Content-Type", "video/mp4")
                .setBody("xxxx")
        )

        val failure = runCatching {
            VideoCacheTransfer(TemporaryFileStore(directory), client)
                .download("mismatch", request())
        }.exceptionOrNull()

        assertTrue(failure is java.io.IOException)
        assertEquals("keep-me", partial.readText())
        assertEquals("bytes=7-", server.takeRequest().getHeader("Range"))
    }
}
