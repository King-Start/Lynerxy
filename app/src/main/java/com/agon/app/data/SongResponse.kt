package com.agon.app.data

data class SongResponse(
    val success: Boolean = false,
    val data: MutableList<Song?>? = null
) {
    data class Song(
        val id: String? = null,
        val name: String? = null,
        val duration: Double? = null,
        val year: String? = null,
        val hasLyrics: Boolean = false,
        val url: String? = null,
        val album: Album? = null,
        val artists: Artists? = null,
        val image: MutableList<Image?>? = null,
        val downloadUrl: MutableList<DownloadUrl?>? = null,
        val lyrics: Lyrics? = null,
        val playCount: Int? = null,
        val copyright: String? = null
    ) {
        fun name(): String = name?.replace("&amp;", "&")?.replace("&#039;", "'") ?: ""
    }

    data class Lyrics(val lyrics: String? = null, val snippet: String? = null)
    data class Album(val id: String? = null, val name: String? = null, val url: String? = null)
    data class Artists(
        val primary: MutableList<Artist?>? = null,
        val featured: MutableList<Artist?>? = null,
        val all: MutableList<Artist?>? = null
    )
    data class Artist(val id: String? = null, val name: String? = null, val role: String? = null) {
        fun name(): String = name?.replace("&amp;", "&") ?: ""
    }
    data class Image(val quality: String? = null, val url: String? = null)
    data class DownloadUrl(val quality: String? = null, val url: String? = null)
}
