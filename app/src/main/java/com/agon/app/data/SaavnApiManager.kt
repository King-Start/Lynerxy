package com.agon.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SaavnApiManager {
    private val BASE = "https://meloapi.vercel.app/api"

    private fun get(url: String): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "okhttp/4.9.0")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText() else null
    } catch (e: Exception) { null }

    suspend fun searchSongs(query: String, limit: Int = 20): List<SongResponse.Song> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val raw = get("$BASE/search/songs?query=$encoded&limit=$limit") ?: return@withContext emptyList()
            parseSongs(raw)
        }

    suspend fun getSongById(id: String): SongResponse.Song? =
        withContext(Dispatchers.IO) {
            val raw = get("$BASE/songs/$id") ?: return@withContext null
            parseSingleFromResponse(raw)
        }

    suspend fun getSuggestions(id: String, limit: Int = 10): List<SongResponse.Song> =
        withContext(Dispatchers.IO) {
            val raw = get("$BASE/songs/$id/suggestions?limit=$limit") ?: return@withContext emptyList()
            parseSongs(raw)
        }

    suspend fun getLyrics(id: String): String? =
        withContext(Dispatchers.IO) {
            val raw = get("$BASE/songs/$id/lyrics") ?: return@withContext null
            try { JSONObject(raw).optJSONObject("data")?.optString("lyrics")?.takeIf { it.isNotBlank() } }
            catch (e: Exception) { null }
        }

    suspend fun getTopCharts(): List<SongResponse.Song> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode("top hindi songs 2024", "UTF-8")
            val raw = get("$BASE/search/songs?query=$encoded&limit=30") ?: return@withContext emptyList()
            parseSongs(raw)
        }

    private fun parseSongs(raw: String): List<SongResponse.Song> {
        return try {
            val json = JSONObject(raw)
            val dataObj = json.optJSONObject("data")
            val arr = dataObj?.optJSONArray("results") ?: json.optJSONArray("data") ?: return emptyList()
            (0 until arr.length()).mapNotNull { parseSong(arr.optJSONObject(it) ?: return@mapNotNull null) }
        } catch (e: Exception) { emptyList() }
    }

    private fun parseSingleFromResponse(raw: String): SongResponse.Song? {
        return try {
            val json = JSONObject(raw)
            val dataArr = json.optJSONArray("data")
            val dataObj = json.optJSONObject("data")
            when {
                dataArr != null && dataArr.length() > 0 -> parseSong(dataArr.getJSONObject(0))
                dataObj != null -> parseSong(dataObj)
                else -> null
            }
        } catch (e: Exception) { null }
    }

    private fun parseSong(song: JSONObject): SongResponse.Song? {
        val id = song.optString("id").takeIf { it.isNotBlank() } ?: return null

        val artistsObj = song.optJSONObject("artists")
        val primaryArr = artistsObj?.optJSONArray("primary")
        val artistName = if (primaryArr != null) {
            (0 until primaryArr.length()).mapNotNull { primaryArr.optJSONObject(it)?.optString("name") }.joinToString(", ")
        } else song.optString("primaryArtists", "Unknown")

        val imageArr = song.optJSONArray("image")
        val imageUrl = if (imageArr != null && imageArr.length() > 0) {
            imageArr.optJSONObject(imageArr.length() - 1)?.optString("url") ?: ""
        } else ""

        val dlArr = song.optJSONArray("downloadUrl")
        val audioUrl = if (dlArr != null && dlArr.length() > 0) {
            (dlArr.length() - 1 downTo 0).firstNotNullOfOrNull { i ->
                dlArr.optJSONObject(i)?.optString("url")?.takeIf { it.isNotBlank() }
            } ?: ""
        } else ""

        return SongResponse.Song(
            id = id,
            name = song.optString("name", "Unknown").replace("&amp;", "&").replace("&#039;", "'"),
            duration = song.optDouble("duration", 0.0),
            year = song.optString("year", ""),
            url = audioUrl,
            album = SongResponse.Album(name = song.optJSONObject("album")?.optString("name")),
            artists = SongResponse.Artists(
                primary = mutableListOf(SongResponse.Artist(name = artistName))
            ),
            image = mutableListOf(SongResponse.Image(url = imageUrl)),
            downloadUrl = if (audioUrl.isNotBlank()) mutableListOf(SongResponse.DownloadUrl(quality = "320kbps", url = audioUrl)) else mutableListOf()
        )
    }
}

// Extension: SongResponse.Song -> Track
fun SongResponse.Song.toSaavnTrack(): Track {
    val audioUrl = downloadUrl?.lastOrNull()?.url?.let {
        if (it.startsWith("http:")) it.replace("http:", "https:") else it
    } ?: url ?: ""
    val imageUrl = image?.lastOrNull()?.url ?: ""
    val artistName = artists?.primary?.firstOrNull()?.name() ?: "Unknown"
    return Track(
        trackId = (id ?: "0").hashCode().toLong(),
        trackName = name() ?: "Unknown",
        artistName = artistName,
        collectionName = album?.name ?: "",
        artworkUrl100 = imageUrl,
        previewUrl = audioUrl,
        primaryGenreName = "",
        trackTimeMillis = ((duration ?: 0.0) * 1000).toLong(),
        saavnId = id ?: "",
        fullAudioUrl = audioUrl,
        durationSeconds = (duration ?: 0.0).toInt()
    )
}
