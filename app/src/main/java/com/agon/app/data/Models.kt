package com.agon.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ItunesSearchResponse(val resultCount: Int, val results: List<Track>)

@Serializable
data class Track(
    val trackId: Long = 0,
    val trackName: String = "",
    val artistName: String = "",
    val collectionName: String = "",
    val artworkUrl100: String = "",
    val previewUrl: String = "",
    val primaryGenreName: String = "",
    val trackTimeMillis: Long = 0,
    val collectionId: Long = 0,
    // Saavn fields for full audio
    val saavnId: String = "",
    val fullAudioUrl: String = "",
    val durationSeconds: Int = 0,
    val ytVideoId: String = ""
)

@Serializable
data class LyricsResponse(val lyrics: String = "")

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val hasImage: Boolean = false,
    val hasAudio: Boolean = false,
    val hasCode: Boolean = false,
    val codeSnippet: String? = null,
    val isSunoAudio: Boolean = false,
    val sunoAudioUrl: String? = null,
    val hasImageResult: Boolean = false,
    val imageResultUrl: String? = null
)

@Serializable
data class FavoriteTrack(
    val trackId: Long,
    val trackName: String,
    val artistName: String,
    val artworkUrl100: String,
    val previewUrl: String,
    val primaryGenreName: String
)

@Serializable
data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val tracks: List<Track> = emptyList(),
    val coverUrl: String = ""
)
