package com.agon.app.data

import com.agon.innertube.YouTube
import com.agon.innertube.models.SongItem
import com.agon.innertube.models.YouTubeClient
import com.agon.innertube.models.WatchEndpoint
import com.agon.innertube.models.BrowseEndpoint
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
    trackId = videoId.hashCode().toLong(),
    trackName = title,
    artistName = artist,
    collectionName = album,
    artworkUrl100 = thumbnailUrl,
    previewUrl = streamUrl,
    ytVideoId = videoId,
    durationSeconds = durationSeconds
)

class YouTubeMusicRepository {

    // ──── Search ────────────────────────────────────────────────────────────
    suspend fun search(query: String): List<YtTrack> = withContext(Dispatchers.IO) {
        try {
            val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
            result.getOrNull()?.items?.filterIsInstance<SongItem>()?.map { song ->
                YtTrack(
                    videoId = song.id,
                    title = song.title,
                    artist = song.artists.joinToString(", ") { it.name },
                    album = song.album?.name ?: "",
                    thumbnailUrl = song.thumbnail ?: "",
                    durationSeconds = song.duration ?: 0
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "YTMusic search failed")
            emptyList()
        }
    }

    // ──── Get Stream URL via Innertube player endpoint ──────────────────────
    suspend fun getStreamUrl(videoId: String): String = withContext(Dispatchers.IO) {
        try {
            val result = YouTube.player(videoId, client = YouTubeClient.ANDROID_MUSIC)
            result.getOrNull()?.let { playerResponse ->
                val formats = playerResponse.streamingData?.adaptiveFormats
                    ?: playerResponse.streamingData?.formats
                    ?: return@withContext ""

                formats
                    .filter { it.mimeType.startsWith("audio/") && it.url != null }
                    .maxByOrNull { it.bitrate }
                    ?.url ?: ""
            } ?: ""
        } catch (e: Exception) {
            Timber.e(e, "getStreamUrl failed for $videoId")
            ""
        }
    }

    // ──── Home / Top Charts ─────────────────────────────────────────────────
    suspend fun getTopCharts(): List<YtTrack> = withContext(Dispatchers.IO) {
        try {
            val result = YouTube.home()
            result.getOrNull()?.sections?.flatMap { section ->
                section.items.filterIsInstance<SongItem>().map { song ->
                    YtTrack(
                        videoId = song.id,
                        title = song.title,
                        artist = song.artists.joinToString(", ") { it.name },
                        album = song.album?.name ?: "",
                        thumbnailUrl = song.thumbnail ?: "",
                        durationSeconds = song.duration ?: 0
                    )
                }
            }?.take(30) ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "getTopCharts failed")
            emptyList()
        }
    }

    // ──── Related Tracks via next endpoint ──────────────────────────────────
    suspend fun getRelatedTracks(videoId: String): List<YtTrack> = withContext(Dispatchers.IO) {
        try {
            val nextResult = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()
            nextResult?.relatedEndpoint?.let { relatedEndpoint ->
                val related = YouTube.related(relatedEndpoint).getOrNull()
                related?.songs?.map { song ->
                    YtTrack(
                        videoId = song.id,
                        title = song.title,
                        artist = song.artists.joinToString(", ") { it.name },
                        album = song.album?.name ?: "",
                        thumbnailUrl = song.thumbnail ?: "",
                        durationSeconds = song.duration ?: 0
                    )
                }?.take(20)
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "getRelatedTracks failed for $videoId")
            emptyList()
        }
    }

    // ──── Search Suggestions ────────────────────────────────────────────────
    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val result = YouTube.searchSuggestions(query)
            result.getOrNull()?.queries ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "searchSuggestions failed")
            emptyList()
        }
    }
}
