package com.agon.app.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Abstraksi provider lirik
 */
interface LyricsProvider {
    val name: String
    suspend fun getLyrics(title: String, artist: String, durationSec: Int = 0): String?
}

/**
 * LrcLib.net — free synced lyrics API
 */
class LrcLibProvider : LyricsProvider {
    override val name = "LrcLib"

    override suspend fun getLyrics(title: String, artist: String, durationSec: Int): String? =
        withContext(Dispatchers.IO) {
            try {
                val q = URLEncoder.encode("$title $artist", "UTF-8")
                val url = URL("https://lrclib.net/api/search?q=$q")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "AgonApp/1.0")
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                if (conn.responseCode != 200) return@withContext null
                val body = conn.inputStream.bufferedReader().readText()
                // Cari entry pertama yang punya syncedLyrics
                val syncedIdx = body.indexOf("\"syncedLyrics\":")
                if (syncedIdx != -1) {
                    val start = body.indexOf('"', syncedIdx + 15) + 1
                    val end = body.indexOf('"', start)
                    val raw = body.substring(start, end)
                        .replace("\\n", "\n")
                        .replace("\\r", "")
                        .replace("\\\"", "\"")
                    if (raw.isNotBlank()) return@withContext raw
                }
                // Fallback ke plainLyrics
                val plainIdx = body.indexOf("\"plainLyrics\":")
                if (plainIdx != -1) {
                    val start = body.indexOf('"', plainIdx + 14) + 1
                    val end = body.indexOf('"', start)
                    val raw = body.substring(start, end)
                        .replace("\\n", "\n")
                        .replace("\\r", "")
                    if (raw.isNotBlank()) return@withContext raw
                }
                null
            } catch (e: Exception) { null }
        }
}

/**
 * Registry semua providers — dicoba berurutan sampai ada yang berhasil
 */
object LyricsRegistry {
    private val providers: List<LyricsProvider> = listOf(
        LrcLibProvider()
    )

    suspend fun getLyrics(title: String, artist: String, durationSec: Int = 0): LyricsResult {
        for (provider in providers) {
            val raw = provider.getLyrics(title, artist, durationSec)
            if (!raw.isNullOrBlank()) {
                val synced = lyricsLooksSynced(raw)
                return LyricsResult(
                    raw = raw,
                    entries = if (synced) parseLrc(raw) else emptyList(),
                    isSynced = synced,
                    source = provider.name
                )
            }
        }
        return LyricsResult(raw = null, entries = emptyList(), isSynced = false, source = null)
    }
}

data class LyricsResult(
    val raw: String?,
    val entries: List<LyricsEntry>,
    val isSynced: Boolean,
    val source: String?
)
