package com.agon.app.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val coverUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
