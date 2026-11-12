package com.agon.app.db

import androidx.room.*
import com.agon.app.db.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // ── Songs ──────────────────────────────────────────────────────
    @Upsert
    suspend fun upsertSong(song: SongEntity)

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSong(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE liked = 1 ORDER BY lastPlayedTime DESC")
    fun getLikedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE inLibrary = 1 ORDER BY lastPlayedTime DESC")
    fun getLibrarySongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY lastPlayedTime DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int = 50): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY totalPlayTime DESC LIMIT :limit")
    fun getMostPlayed(limit: Int = 20): Flow<List<SongEntity>>

    @Query("UPDATE songs SET liked = :liked WHERE id = :id")
    suspend fun setLiked(id: String, liked: Boolean)

    @Query("UPDATE songs SET inLibrary = :inLibrary WHERE id = :id")
    suspend fun setInLibrary(id: String, inLibrary: Boolean)

    @Query("UPDATE songs SET totalPlayTime = totalPlayTime + :ms, lastPlayedTime = :now WHERE id = :id")
    suspend fun incrementPlayTime(id: String, ms: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE songs SET downloadState = :state WHERE id = :id")
    suspend fun setDownloadState(id: String, state: Int)

    @Query("SELECT * FROM songs WHERE downloadState = 2")
    fun getDownloadedSongs(): Flow<List<SongEntity>>

    // ── Playlists ──────────────────────────────────────────────────
    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getPlaylists(): Flow<List<PlaylistEntity>>

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Upsert
    suspend fun addSongToPlaylist(map: PlaylistSongMap)

    @Query("DELETE FROM playlist_song_map WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String)

    @Query("""
        SELECT s.* FROM songs s 
        INNER JOIN playlist_song_map m ON s.id = m.songId 
        WHERE m.playlistId = :playlistId 
        ORDER BY m.position ASC
    """)
    fun getPlaylistSongs(playlistId: String): Flow<List<SongEntity>>

    // ── Lyrics Cache ───────────────────────────────────────────────
    @Upsert
    suspend fun upsertLyrics(lyrics: LyricsEntity)

    @Query("SELECT * FROM lyrics WHERE songId = :songId")
    suspend fun getLyrics(songId: String): LyricsEntity?

    // ── Search History ─────────────────────────────────────────────
    @Upsert
    suspend fun addSearchHistory(history: SearchHistory)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 20")
    fun getSearchHistory(): Flow<List<SearchHistory>>

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchHistory(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}
