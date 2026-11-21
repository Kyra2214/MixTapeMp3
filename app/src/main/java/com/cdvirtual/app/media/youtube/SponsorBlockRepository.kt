package com.cdvirtual.app.media.youtube

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class SponsorSegment(
    val startSeconds: Double,
    val endSeconds: Double,
    val category: String
)

/**
 * Integração opcional com a API pública do SponsorBlock.
 * Envia somente o ID público do vídeo para consultar segmentos enviados pela comunidade.
 * O cliente HTTP é compartilhado para reutilizar conexões entre consultas.
 */
object SponsorBlockRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getSkippableSegments(videoId: String): List<SponsorSegment> =
        withContext(Dispatchers.IO) {
            if (videoId.isBlank()) return@withContext emptyList()

            val categories = JSONArray(listOf("sponsor", "selfpromo"))
            val url = "https://sponsor.ajay.app/api/skipSegments" +
                "?videoID=" + Uri.encode(videoId) +
                "&categories=" + Uri.encode(categories.toString())
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MixTapeMp3/1.0")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@withContext emptyList()
                    val json = JSONArray(body)
                    buildList {
                        for (index in 0 until json.length()) {
                            val item = json.optJSONObject(index) ?: continue
                            if (item.optString("actionType") != "skip") continue
                            val range = item.optJSONArray("segment") ?: continue
                            if (range.length() < 2) continue
                            val start = range.optDouble(0, -1.0)
                            val end = range.optDouble(1, -1.0)
                            if (start.isFinite() && end.isFinite() && start >= 0.0 && end > start) {
                                add(
                                    SponsorSegment(
                                        startSeconds = start,
                                        endSeconds = end,
                                        category = item.optString("category")
                                    )
                                )
                            }
                        }
                    }.sortedBy { it.startSeconds }
                }
            } catch (_: Exception) {
                // Falha do SponsorBlock nunca deve impedir a reprodução do vídeo.
                emptyList()
            }
        }
}
