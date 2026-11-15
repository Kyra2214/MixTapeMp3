package com.cdvirtual.app.media.connectors

import com.cdvirtual.app.core.common.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Único ponto que fala com o YouTube. Trocar o transporte (Cobalt, NewPipe Extractor) significa
 * reescrever só este arquivo: [YouTubeConnector] depende apenas de [analyze].
 *
 * Usa o endpoint interno do player (Innertube) com contexto de cliente Android. Sem biblioteca nova,
 * mas herda o risco de o Google mudar o cliente: nesse caso a falha chega como
 * [YouTubeFailure.PlayerChanged].
 */
object YouTubeApi {
    private const val ENDPOINT = "https://www.youtube.com/youtubei/v1/player"
    private const val CLIENT_VERSION = "20.10.38"
    private const val UA = "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip"

    suspend fun analyze(videoId: String): LinkInfo = withContext(Dispatchers.IO) {
        val root = post(videoId)
        val status = root.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
        if (status != "OK") throw YouTubeUnavailableException(failureOf(root, status))
        val formats = root.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
            ?: throw YouTubeUnavailableException(YouTubeFailure.NoAudio)
        val options = buildList {
            for (i in 0 until formats.length()) {
                val format = formats.optJSONObject(i) ?: continue
                val mime = format.optString("mimeType")
                if (!mime.startsWith("audio/")) continue
                val url = format.optString("url").takeIf { it.isNotEmpty() } ?: continue
                val ext = extensionOf(mime) ?: continue
                val kbps = format.optInt("bitrate", 0) / 1000
                val size = format.optLong("contentLength", 0)
                add(
                    MediaOption(
                        id = format.optString("itag", ext),
                        label = "${ext.uppercase()} · $kbps kbps" + if (size > 0) " · ${formatSize(size)}" else "",
                        format = ext,
                        mimeType = mime.substringBefore(';'),
                        sizeBytes = size,
                        bitrateKbps = kbps.takeIf { it > 0 },
                        url = url
                    )
                )
            }
        }
        if (options.isEmpty()) throw YouTubeUnavailableException(YouTubeFailure.NoAudio)
        LinkInfo(
            finalUrl = videoId, contentType = null, sizeBytes = 0, fileName = null,
            kind = LinkKind.Audio, options = options.sortedByDescending { it.bitrateKbps ?: 0 }
        )
    }

    private fun post(videoId: String): JSONObject {
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9")
            conn.setRequestProperty("X-YouTube-Client-Name", "3")
            conn.setRequestProperty("X-YouTube-Client-Version", CLIENT_VERSION)
            val body = JSONObject().apply {
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
                put("context", JSONObject().put("client", JSONObject().apply {
                    put("clientName", "ANDROID")
                    put("clientVersion", CLIENT_VERSION)
                    put("androidSdkVersion", 30)
                    put("hl", "pt")
                    put("gl", "BR")
                }))
            }.toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    /** O mimeType do YouTube separa o container do codec: "audio/mp4; codecs=..." vira "m4a". */
    private fun extensionOf(mime: String): String? = when (mime.substringBefore(';').trim()) {
        "audio/mp4" -> "m4a"
        "audio/webm" -> "webm"
        "audio/ogg" -> "ogg"
        "audio/mpeg" -> "mp3"
        else -> null
    }

    private fun failureOf(root: JSONObject, status: String): YouTubeFailure {
        val reason = root.optJSONObject("playabilityStatus")?.optString("reason").orEmpty().lowercase()
        return when {
            reason.contains("idade") || reason.contains("age") -> YouTubeFailure.AgeRestricted
            reason.contains("ao vivo") || reason.contains("live") -> YouTubeFailure.LiveNotSupported
            reason.contains("país") || reason.contains("country") || reason.contains("região") -> YouTubeFailure.RegionBlocked
            status == "LOGIN_REQUIRED" -> YouTubeFailure.Private
            status == "ERROR" || status == "UNPLAYABLE" -> YouTubeFailure.PlayerChanged
            else -> YouTubeFailure.PlayerChanged
        }
    }
}
