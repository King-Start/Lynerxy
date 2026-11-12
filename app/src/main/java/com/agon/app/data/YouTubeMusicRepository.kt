package com.agon.app.data

import com.agon.innertube.NewPipeExtractor
import com.agon.innertube.YouTube
import com.agon.innertube.models.SongItem
import com.agon.innertube.models.WatchEndpoint
import com.agon.innertube.models.YouTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

data class YtTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val thumbnailUrl: String,
    val durationSeconds: Int = 0
)

fun YtTrack.toTrack() = Track(
    trackId        = videoId.hashCode().toLong(),
    trackName      = title,
    artistName     = artist,
    collectionName = album,
    artworkUrl100  = thumbnailUrl,
    ytVideoId      = videoId,
    durationSeconds= durationSeconds
)

class YouTubeMusicRepository {

    suspend fun search(query: String): List<YtTrack> = withContext(Dispatchers.IO) {
        try {
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                .getOrNull()?.items
                ?.filterIsInstance<SongItem>()
                ?.map { it.toYtTrack() }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "search failed")
            emptyList()
        }
    }

    /**
     * Ambil URL audio stream.
     * Strategy: ANDROID_NO_SDK -> TVHTML5_SIMPLY_EMBEDDED -> NewPipe
     */
    suspend fun getStreamUrl(videoId: String): String = withContext(Dispatchers.IO) {
        Timber.d("getStreamUrl start: $videoId")

        // Strategy 1: ANDROID_NO_SDK — url biasanya langsung tanpa cipher
        val url1 = tryClient(videoId, YouTubeClient.ANDROID_NO_SDK)
        if (url1.isNotBlank()) return@withContext url1

        // Strategy 2: TVHTML5 embedded — url langsung juga
        val url2 = tryClient(videoId, YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER)
        if (url2.isNotBlank()) return@withContext url2

        // Strategy 3: NewPipeExtractor — JS deobfuscation
        try {
            val streams = NewPipeExtractor.newPipePlayer(videoId)
            // Prefer audio-only: 251=opus/160k, 140=m4a/128k, 250=opus/70k, 249=opus/50k
            val preferred = listOf(251, 140, 250, 249, 139)
            for (itag in preferred) {
                val found = streams.firstOrNull { pair -> pair.first == itag }
                if (found != null && found.second.isNotBlank()) {
                    Timber.d("getStreamUrl NewPipe itag=$itag ok")
                    return@withContext found.second
                }
            }
            // Fallback: apapun yang ada
            streams.firstOrNull()?.second?.also {
                Timber.d("getStreamUrl NewPipe fallback ok")
            } ?: ""
        } catch (e: Exception) {
            Timber.e(e, "getStreamUrl NewPipe failed")
            ""
        }
    }

    private suspend fun tryClient(videoId: String, client: YouTubeClient): String {
        return try {
            val response = YouTube.player(videoId, client = client).getOrNull() ?: return ""
            val allFormats = buildList {
                addAll(response.streamingData?.adaptiveFormats ?: emptyList())
                addAll(response.streamingData?.formats ?: emptyList())
            }
            // Filter audio only
            val audioFormats = allFormats.filter { fmt ->
                fmt.mimeType.startsWith("audio/") && fmt.url != null
            }.ifEmpty {
                // Kalau tidak ada audio-only, ambil semua yang punya url
                allFormats.filter { it.url != null }
            }
            val best = audioFormats.maxByOrNull { it.bitrate }
            val url = best?.url ?: ""
            if (url.isNotBlank()) Timber.d("tryClient ${client.clientName} ok: bitrate=${best?.bitrate}")
            else Timber.d("tryClient ${client.clientName} url empty")
            url
        } catch (e: Exception) {
            Timber.w("tryClient ${client.clientName} failed: ${e.message}")
            ""
        }
    }

    suspend fun getTopCharts(): List<YtTrack> = withContext(Dispatchers.IO) {
        try {
            YouTube.home().getOrNull()?.sections
                ?.flatMap { it.items.filterIsInstance<SongItem>() }
                ?.map { it.toYtTrack() }
                ?.take(30) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getRelatedTracks(videoId: String): List<YtTrack> = withContext(Dispatchers.IO) {
        try {
            val next = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull() ?: return@withContext emptyList()
            val related = next.relatedEndpoint?.let { YouTube.related(it).getOrNull() } ?: return@withContext emptyList()
            related.songs.map { it.toYtTrack() }.take(20)
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        try { YouTube.searchSuggestions(query).getOrNull()?.queries ?: emptyList() }
        catch (_: Exception) { emptyList() }
    }

    private fun SongItem.toYtTrack() = YtTrack(
        videoId        = id,
        title          = title,
        artist         = artists.joinToString(", ") { it.name },
        album          = album?.name ?: "",
        thumbnailUrl   = thumbnail ?: "",
        durationSeconds= duration ?: 0
    )
}
