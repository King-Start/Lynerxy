package com.agon.app.db.entities

import androidx.room.Entity

@Entity(tableName = "playlist_song_map", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongMap(
    val playlistId: String,
    val songId: String,
    val position: Int = 0
)
