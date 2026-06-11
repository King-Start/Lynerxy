package com.agon.app.data

import com.agon.innertube.YouTube
import com.agon.innertube.models.SongItem
import com.agon.innertube.models.YouTubeClient
import com.agon.innertube.models.WatchEndpoint
import com.agon.innertube.models.response.PlayerResponse
import com.agon.innertube.NewPipeExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

data class YtTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val thumbnailUrl: String,
    val durationSeconds: Int = 0,
    val streamUrl: String = ""
)

fun YtTrack.toTrack(): Track = Track(
    trackId   = videoId.hashCode().toLong(),
    trackName = title,
    artistName= artist,
    collectionName = album,
    artworkUrl100  = thumbnailUrl,
    previewUrl     = streamUrl,
    ytVideoId      = videoId,
    durationSeconds= durationSeconds
)

class YouTubeMusicRepository {

    // ── Search ────────────────────────────────────────────────
    suspend fun search(query: String): List<YtTrack> = withContext(Dispatchers.IO) {
        try {
            val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
            result.getOrNull()?.items?.filterIsInstance<SongItem>()?.map { song ->
                YtTrack(
                    videoId       = song.id,
                    title         = song.title,
                    artist        = song.artists.joinToString(", ") { it.name },
                    album         = song.album?.name ?: "",
                    thumbnailUrl  = song.thumbnail ?: "",
                    durationSeconds = song.duration ?: 0
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "YTMusic search failed")
            emptyList()
        }
    }

    // ── Get Stream URL — multi-strategy ───────────────────────
    suspend fun getStreamUrl(videoId: String): String = withContext(Dispatchers.IO) {
        Timber.d("getStreamUrl: $videoId")

        // Strategy 1: ANDROID_NO_SDK — url biasanya langsung ada tanpa cipher
        val s1 = tryGetUrlFromPlayer(videoId, YouTubeClient.ANDROID_NO_SDK)
        if (s1.isNotBlank()) { Timber.d("S1 ok: $videoId"); return@withContext s1 }

        // Strategy 2: TVHTML5_SIMPLY_EMBEDDED — embedded player, url juga sering langsung
        val s2 = tryGetUrlFromPlayer(videoId, YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER)
        if (s2.isNotBlank()) { Timber.d("S2 ok: $videoId"); return@withContext s2 }

        // Strategy 3: NewPipeExtractor — full JS deobfuscation
        try {
            val streams = NewPipeExtractor.newPipePlayer(videoId)
            val preferred = listOf(251, 140, 250, 139, 249)
            val url = preferred.firstNotNullOfOrNull { itag ->
                streams.firstOrNull { pair -> pair.first == itag }?.second
            } ?: streams.firstOrNull()?.second ?: ""
            if (url.isNotBlank()) { Timber.d("S3 NewPipe ok: $videoId"); return@withContext url }
        } catch (e: Exception) {
            Timber.e(e, "S3 NewPipe failed: $videoId")
        }

        Timber.w("All strategies failed for $videoId")
        ""
    }

    private suspend fun tryGetUrlFromPlayer(videoId: String, client: YouTubeClient): String {
        return try {
            val response = YouTube.player(videoId, client = client).getOrNull() ?: return ""
            val formats = response.streamingData?.adaptiveFormats
                ?: response.streamingData?.formats
                ?: return ""

            // Filter audio only, pilih bitrate tertinggi
            val audioFormats = formats.filter { it.isAudio && it.isOriginal }
                .ifEmpty { formats.filter { it.isAudio } }
                .ifEmpty { formats }

            val best = audioFormats.maxByOrNull { it.bitrate } ?: return ""

            // URL langsung
            if (!best.url.isNullOrBlank()) return best.url!!

            // Decode signatureCipher
            val decoded = NewPipeExtractor.getStreamUrl(best, videoId) ?: ""
            decoded
        } catch (e: Exception) {
            Timber.w("tryGetUrlFromPlayer $client failed: ${e.message}")
            ""
        }
    }

    // ── Top Charts ────────────────────────────────────────────
    suspend fun getTopCharts(): List<YtTrack> = withContext(Dispatchers.IO) {
        try {
            YouTube.home().getOrNull()?.sections?.flatMap { section ->
                section.items.filterIsInstance<SongItem>().map { song ->
                    YtTrack(
                        videoId       = song.id,
                        title         = song.title,
                        artist        = song.artists.joinToString(", ") { it.name },
                        album         = song.album?.name ?: "",
                        thumbnailUrl  = song.thumbnail ?: "",
                        durationSeconds = song.duration ?: 0
                    )
                }
            }?.take(30) ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "getTopCharts failed")
            emptyList()
        }
    }

    // ── Related Tracks ────────────────────────────────────────
    suspend fun getRelatedTracks(videoId: String): List<YtTrack> = withContext(Dispatchers.IO) {
        try {
            val next = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull() ?: return@withContext emptyList()
            val related = next.relatedEndpoint?.let { YouTube.related(it).getOrNull() } ?: return@withContext emptyList()
            related.songs.map { song ->
                YtTrack(
                    videoId      = song.id,
                    title        = song.title,
                    artist       = song.artists.joinToString(", ") { it.name },
                    album        = song.album?.name ?: "",
                    thumbnailUrl = song.thumbnail ?: "",
                    durationSeconds = song.duration ?: 0
                )
            }.take(20)
        } catch (e: Exception) {
            Timber.e(e, "getRelatedTracks failed")
            emptyList()
        }
    }

    // ── Search Suggestions ────────────────────────────────────
    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        try {
            YouTube.searchSuggestions(query).getOrNull()?.queries ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
