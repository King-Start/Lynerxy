package com.agon.app.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val songId: String,
    val lyrics: String,
    val isSynced: Boolean = false,
    val source: String = "",
    val cachedAt: Long = System.currentTimeMillis()
)
