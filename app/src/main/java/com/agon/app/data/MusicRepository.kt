package com.agon.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MusicRepository {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchSongs(query: String): List<Track> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://itunes.apple.com/search?term=$encoded&entity=song&limit=50&country=ID")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().readText()
                json.decodeFromString<ItunesSearchResponse>(resp).results
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getTopCharts(country: String = "ID", genre: String = ""): List<Track> = withContext(Dispatchers.IO) {
        try {
            val genreParam = if (genre.isNotBlank()) "&genreId=${getGenreId(genre)}" else ""
            val url = URL("https://itunes.apple.com/search?term=top+hits+2024&entity=song&limit=50&country=$country$genreParam")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().readText()
                json.decodeFromString<ItunesSearchResponse>(resp).results
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getRelatedTracks(artistName: String): List<Track> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(artistName, "UTF-8")
            val url = URL("https://itunes.apple.com/search?term=$encoded&entity=song&limit=25")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().readText()
                json.decodeFromString<ItunesSearchResponse>(resp).results
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        try {
            val a = URLEncoder.encode(artist, "UTF-8")
            val t = URLEncoder.encode(title, "UTF-8")
            val conn = URL("https://api.lyrics.ovh/v1/$a/$t").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().readText()
                json.decodeFromString<LyricsResponse>(resp).lyrics.takeIf { it.isNotBlank() }
            } else null
        } catch (e: Exception) { null }
    }

    private fun getGenreId(genre: String): String = when (genre.lowercase()) {
        "pop" -> "14"
        "rock" -> "21"
        "hip-hop", "hiphop" -> "18"
        "electronic" -> "7"
        "r&b", "rnb" -> "15"
        "jazz" -> "11"
        "country" -> "6"
        else -> ""
    }
}