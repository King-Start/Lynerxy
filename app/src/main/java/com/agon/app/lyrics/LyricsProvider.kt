package com.agon.app.lyrics

import android.util.Base64
import com.agon.innertube.YouTube
import com.agon.innertube.models.WatchEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Abstraksi provider lirik.
 * Provider dicoba berurutan oleh [LyricsRegistry] — lirik ber-timestamp (synced) diutamakan.
 */
interface LyricsProvider {
    val name: String
    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSec: Int = 0,
        videoId: String = "",
        album: String = ""
    ): String?
}

// ─────────────────────────────────────────────────────────────
// Util bersama (HTTP + pembersihan judul/artis + kemiripan teks)
// ─────────────────────────────────────────────────────────────
private object LyricsHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** GET → body string, atau null kalau gagal / bukan 2xx. */
    fun get(url: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", "LyronixAi/1.0").build()
        client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
    } catch (e: Exception) {
        Timber.w("LyricsHttp GET gagal: ${e.message}")
        null
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    /** Encode dengan %20 untuk spasi (dibutuhkan KuGou). */
    fun encSpace20(s: String): String = enc(s).replace("+", "%20")
}

private object LyricsText {
    private val titleCleanup = listOf(
        Regex("""\s*\(.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\[.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\]""", RegexOption.IGNORE_CASE),
        Regex("""\s*【.*?】"""),
        Regex("""\s*\|.*$"""),
        Regex("""\s*-\s*(official|video|audio|lyrics|lyric|visualizer).*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(feat\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(ft\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*feat\..*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*ft\..*$""", RegexOption.IGNORE_CASE),
    )
    private val artistSeparators =
        listOf(" & ", " and ", ", ", " x ", " X ", " feat. ", " feat ", " ft. ", " ft ", " featuring ", " with ")

    fun cleanTitle(title: String): String {
        var s = title.trim()
        for (p in titleCleanup) s = s.replace(p, "")
        return s.trim()
    }

    fun cleanArtist(artist: String): String {
        var s = artist.trim()
        for (sep in artistSeparators) {
            if (s.contains(sep, ignoreCase = true)) {
                s = s.split(sep, ignoreCase = true, limit = 2)[0]
                break
            }
        }
        return s.trim()
    }

    fun similarity(a: String, b: String): Double {
        val s1 = a.trim().lowercase()
        val s2 = b.trim().lowercase()
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val contains = if (s1.contains(s2) || s2.contains(s1)) 0.8 else 0.0
        val dist = levenshtein(s1, s2)
        return maxOf(contains, 1.0 - dist.toDouble() / maxOf(s1.length, s2.length))
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) m[i][0] = i
        for (j in 0..b.length) m[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            m[i][j] = minOf(m[i - 1][j] + 1, m[i][j - 1] + 1, m[i - 1][j - 1] + cost)
        }
        return m[a.length][b.length]
    }
}

// ─────────────────────────────────────────────────────────────
// 1. LrcLib.net — lirik synced gratis
// ─────────────────────────────────────────────────────────────
class LrcLibProvider : LyricsProvider {
    override val name = "LrcLib"

    private data class Candidate(
        val trackName: String,
        val artistName: String,
        val duration: Double,
        val plain: String?,
        val synced: String?
    )

    override suspend fun getLyrics(title: String, artist: String, durationSec: Int, videoId: String, album: String): String? =
        withContext(Dispatchers.IO) {
            val cTitle = LyricsText.cleanTitle(title)
            val cArtist = LyricsText.cleanArtist(artist)
            val base = "https://lrclib.net/api/search"
            val e = LyricsHttp::enc

            // Beberapa strategi pencarian — berhenti di yang pertama menghasilkan lirik
            val urls = buildList {
                add("$base?track_name=${e(cTitle)}&artist_name=${e(cArtist)}" +
                    if (album.isNotBlank()) "&album_name=${e(album)}" else "")
                add("$base?track_name=${e(cTitle)}")
                add("$base?q=${e("$cArtist $cTitle")}")
                add("$base?q=${e(cTitle)}")
                if (cTitle != title.trim()) add("$base?track_name=${e(title.trim())}&artist_name=${e(artist.trim())}")
            }

            for (url in urls) {
                val candidates = parse(LyricsHttp.get(url))
                if (candidates.isEmpty()) continue
                val best = pickBest(candidates, cTitle, cArtist, durationSec) ?: continue
                val text = best.synced ?: best.plain
                if (!text.isNullOrBlank()) return@withContext text
            }
            null
        }

    private fun parse(body: String?): List<Candidate> {
        if (body.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val synced = o.strOrNull("syncedLyrics")
                val plain = o.strOrNull("plainLyrics")
                if (synced == null && plain == null) null
                else Candidate(o.optString("trackName"), o.optString("artistName"), o.optDouble("duration", 0.0), plain, synced)
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun JSONObject.strOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun pickBest(list: List<Candidate>, title: String, artist: String, durationSec: Int): Candidate? {
        if (durationSec > 0) {
            // 1) synced dengan durasi paling dekat (±5 detik), 2) lirik apa pun dalam toleransi
            fun near(c: Candidate) = abs(c.duration.toInt() - durationSec)
            list.filter { it.synced != null }.minByOrNull(::near)?.takeIf { near(it) <= 5 }?.let { return it }
            list.minByOrNull(::near)?.takeIf { near(it) <= 5 }?.let { return it }
        }
        // Tanpa durasi (atau tak ada yang cocok): pilih berdasar kemiripan judul + artis
        return list.maxByOrNull { c ->
            (LyricsText.similarity(title, c.trackName) + LyricsText.similarity(artist, c.artistName)) / 2.0 +
                (if (c.synced != null) 0.1 else 0.0)
        }?.takeIf { c ->
            (LyricsText.similarity(title, c.trackName) + LyricsText.similarity(artist, c.artistName)) / 2.0 > 0.6
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 2. KuGou — lirik synced (bagus untuk lagu Asia / Mandarin / populer)
//    Diport dari RythimMusic (asal: ViMusic)
// ─────────────────────────────────────────────────────────────
class KuGouProvider : LyricsProvider {
    override val name = "KuGou"

    private companion object {
        const val DURATION_TOLERANCE = 8
        const val HEAD_CUT_LIMIT = 30
        val ACCEPTED = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\].*".toRegex()
        val BANNED = ".+].+[:：].+".toRegex()
    }

    override suspend fun getLyrics(title: String, artist: String, durationSec: Int, videoId: String, album: String): String? =
        withContext(Dispatchers.IO) {
            val keyword = buildKeyword(title, artist, album)
            val duration = if (durationSec > 0) durationSec else -1

            // a) cari lagu → ambil kandidat lirik via hash
            var candidate: Pair<Long, String>? = null
            val songs = searchSongs(keyword)
            for ((songDuration, hash) in songs.take(5)) {
                if (duration == -1 || abs(songDuration - duration) <= DURATION_TOLERANCE) {
                    candidate = searchLyricsByHash(hash)
                    if (candidate != null) break
                }
            }
            // b) fallback: cari lirik langsung via keyword
            if (candidate == null) candidate = searchLyricsByKeyword(keyword, duration)

            val (id, key) = candidate ?: return@withContext null
            download(id, key)?.let { normalize(it) }?.takeIf { it.isNotBlank() }
        }

    private fun buildKeyword(title: String, artist: String, album: String): String {
        val t = title.replace("\\(.*\\)".toRegex(), "").replace("（.*）".toRegex(), "")
            .replace("「.*」".toRegex(), "").replace("『.*』".toRegex(), "")
            .replace("<.*>".toRegex(), "").replace("《.*》".toRegex(), "")
            .replace("〈.*〉".toRegex(), "").replace("＜.*＞".toRegex(), "").trim()
        val a = artist.replace(", ", "、").replace(" & ", "、").replace(".", "").replace("和", "、")
            .replace("\\(.*\\)".toRegex(), "").replace("（.*）".toRegex(), "").trim()
        return buildString {
            append(t); append(" - "); append(a)
            if (album.isNotBlank()) { append(" "); append(album) }
        }
    }

    /** Daftar (durasi detik, hash) */
    private fun searchSongs(keyword: String): List<Pair<Int, String>> {
        val url = "https://mobileservice.kugou.com/api/v3/search/song?version=9108&plat=0&pagesize=8&showtype=0" +
            "&keyword=${LyricsHttp.encSpace20(keyword)}"
        val body = LyricsHttp.get(url) ?: return emptyList()
        return try {
            val info = JSONObject(body).optJSONObject("data")?.optJSONArray("info") ?: return emptyList()
            (0 until info.length()).mapNotNull { i ->
                val o = info.optJSONObject(i) ?: return@mapNotNull null
                val hash = o.optString("hash")
                if (hash.isBlank()) null else o.optInt("duration") to hash
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun firstCandidate(body: String?): Pair<Long, String>? {
        if (body.isNullOrBlank()) return null
        return try {
            val arr = JSONObject(body).optJSONArray("candidates") ?: return null
            val o = arr.optJSONObject(0) ?: return null
            val key = o.optString("accesskey")
            if (key.isBlank()) null else o.optLong("id") to key
        } catch (e: Exception) { null }
    }

    private fun searchLyricsByHash(hash: String) =
        firstCandidate(LyricsHttp.get("https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=$hash"))

    private fun searchLyricsByKeyword(keyword: String, duration: Int): Pair<Long, String>? {
        val dur = if (duration != -1) "&duration=${duration * 1000}" else ""
        return firstCandidate(
            LyricsHttp.get("https://lyrics.kugou.com/search?ver=1&man=yes&client=pc$dur&keyword=${LyricsHttp.encSpace20(keyword)}")
        )
    }

    private fun download(id: Long, accessKey: String): String? {
        val body = LyricsHttp.get(
            "https://lyrics.kugou.com/download?fmt=lrc&charset=utf8&client=pc&ver=1&id=$id&accesskey=$accessKey"
        ) ?: return null
        return try {
            val content = JSONObject(body).optString("content")
            if (content.isBlank()) null else String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) { null }
    }

    /** Buang baris kredit (penyanyi, penulis, dll) di awal/akhir lirik. */
    private fun normalize(raw: String): String {
        val lines = raw.lines().filter { it.matches(ACCEPTED) }
        if (lines.isEmpty()) return ""

        var headCut = 0
        for (i in minOf(HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
            if (lines[i].matches(BANNED)) { headCut = i + 1; break }
        }
        val afterHead = lines.drop(headCut)

        var tailCut = 0
        for (i in minOf(lines.size - HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
            if (lines[lines.lastIndex - i].matches(BANNED)) { tailCut = i + 1; break }
        }
        return afterHead.dropLast(tailCut).joinToString("\n")
    }
}

// ─────────────────────────────────────────────────────────────
// 3. YouTube Music — lirik teks biasa (butuh ytVideoId)
// ─────────────────────────────────────────────────────────────
class YouTubeLyricsProvider : LyricsProvider {
    override val name = "YouTube Music"

    override suspend fun getLyrics(title: String, artist: String, durationSec: Int, videoId: String, album: String): String? {
        if (videoId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val endpoint = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()?.lyricsEndpoint
                    ?: return@withContext null
                YouTube.lyrics(endpoint).getOrNull()?.takeIf { it.isNotBlank() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.w("YouTubeLyricsProvider gagal: ${e.message}")
                null
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Registry — dicoba berurutan; lirik synced diutamakan, plain jadi cadangan
// ─────────────────────────────────────────────────────────────
object LyricsRegistry {
    private const val PROVIDER_TIMEOUT_MS = 15_000L

    private val providers: List<LyricsProvider> = listOf(
        LrcLibProvider(),
        KuGouProvider(),
        YouTubeLyricsProvider()
    )

    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSec: Int = 0,
        videoId: String = "",
        album: String = ""
    ): LyricsResult {
        var plainFallback: Pair<String, String>? = null   // (teks, nama provider)

        for (provider in providers) {
            val raw = try {
                withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                    provider.getLyrics(title, artist, durationSec, videoId, album)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Timber.w("Provider ${provider.name} gagal: ${t.message}")
                null
            }
            if (raw.isNullOrBlank()) continue

            if (lyricsLooksSynced(raw)) {
                return LyricsResult(raw = raw, entries = parseLrc(raw), isSynced = true, source = provider.name)
            }
            if (plainFallback == null) plainFallback = raw to provider.name
        }

        plainFallback?.let { (text, source) ->
            return LyricsResult(raw = text, entries = emptyList(), isSynced = false, source = source)
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
