package com.agon.app.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,      // ytVideoId atau saavnId
    val title: String,
    val artist: String,
    val album: String = "",
    val duration: Int = 0,           // detik
    val thumbnailUrl: String = "",
    val audioUrl: String = "",
    val liked: Boolean = false,
    val inLibrary: Boolean = false,
    val totalPlayTime: Long = 0L,
    val lastPlayedTime: Long = 0L,
    val downloadState: Int = 0       // 0=none, 1=downloading, 2=downloaded
)
