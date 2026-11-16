package com.cdvirtual.app.media.connectors

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HttpStatusException(val code: Int) : IOException("HTTP $code")

object Http {
    private const val USER_AGENT = "CDVirtual/1.0"
    private val REDIRECTS = setOf(301, 302, 303, 307, 308)

    /** Abre a conexão seguindo redirecionamentos manualmente (até 6). */
    fun open(
        url: URL,
        method: String,
        headers: Map<String, String> = emptyMap(),
        connectTimeoutMs: Int = 15_000,
        readTimeoutMs: Int = 20_000
    ): HttpURLConnection {
        var current = url
        repeat(6) {
            val c = current.openConnection() as HttpURLConnection
            c.requestMethod = method
            c.instanceFollowRedirects = false
            c.connectTimeout = connectTimeoutMs
            c.readTimeout = readTimeoutMs
            c.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            if (code in REDIRECTS) {
                val location = c.getHeaderField("Location") ?: throw HttpStatusException(code)
                c.disconnect()
                current = URL(current, location)
                return@repeat
            }
            return c
        }
        throw IOException("Redirecionamentos demais")
    }
}
