package com.agon.app.viewmodel

import androidx.media3.common.Player as Media3Player
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import timber.log.Timber
import com.agon.app.alarm.MusicAlarmEntry
import com.agon.app.alarm.MusicAlarmScheduler
import com.agon.app.alarm.MusicAlarmStore
import com.agon.app.data.*
import com.agon.app.db.AppDatabase
import com.agon.app.db.entities.LyricsEntity
import com.agon.app.db.entities.SearchHistory
import com.agon.app.db.entities.SongEntity
import com.agon.app.download.DownloadManager
import com.agon.app.eq.EqualizerManager
import com.agon.app.eq.data.EQProfileRepository
import com.agon.app.eq.data.SavedEQProfile
import com.agon.app.lyrics.LyricsEntry
import com.agon.app.lyrics.LyricsRegistry
import com.agon.app.lyrics.findCurrentLyricsIndex
import com.agon.app.lyrics.lyricsLooksSynced
import com.agon.app.lyrics.parseLrc
import com.agon.app.utils.MusicPlayerManager
import com.agon.app.utils.SleepTimer
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    // ── Repositories ──────────────────────────────────────────
    private val musicRepo       = MusicRepository()
    private val saavnApi        = SaavnApiManager()
    private val ytMusicRepo     = YouTubeMusicRepository()
    private val chatRepo        = ChatRepository(application)
    private val cloudRepo       = CloudRepository()
    private val settingsRepo    = SettingsRepository(application)
    private val aiRepo          = AiRepository()
    private val imageRepo       = ImageRepository()
    private val db              = AppDatabase.getInstance(application)
    private val downloadMgr     = DownloadManager(application)
    val eqProfileRepo           = EQProfileRepository(application)
    private val json            = Json { ignoreUnknownKeys = true }

    // ── API Config ────────────────────────────────────────────
    private val _apiKey             = MutableStateFlow(settingsRepo.getApiKey())
    val apiKey: StateFlow<String>   = _apiKey
    private val _apiProvider        = MutableStateFlow(settingsRepo.getApiProvider())
    val apiProvider: StateFlow<String> = _apiProvider
    private val _apiValidationResult = MutableStateFlow<String?>(null)
    val apiValidationResult: StateFlow<String?> = _apiValidationResult

    // ── Theme ─────────────────────────────────────────────────
    private val _themeMode          = MutableStateFlow(settingsRepo.getThemeMode())
    val themeMode: StateFlow<String> = _themeMode

    // ── Chat ──────────────────────────────────────────────────
    private val _chatMessages       = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages
    private val _isAiTyping         = MutableStateFlow(false)
    val isAiTyping: StateFlow<Boolean> = _isAiTyping

    // ── Discover ──────────────────────────────────────────────
    private val _searchQuery        = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    private val _searchResults      = MutableStateFlow<List<Track>>(emptyList())
    val searchResults: StateFlow<List<Track>> = _searchResults
    private val _isLoading          = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _topCharts          = MutableStateFlow<List<Track>>(emptyList())
    val topCharts: StateFlow<List<Track>> = _topCharts
    private val _relatedTracks      = MutableStateFlow<List<Track>>(emptyList())
    val relatedTracks: StateFlow<List<Track>> = _relatedTracks
    private val _searchSuggestions  = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions
    private val _searchHistory      = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory

    private val _mediaType          = MutableStateFlow("Music")
    val mediaType: StateFlow<String> = _mediaType
    fun setMediaType(type: String) { _mediaType.value = type }

    // ── Player ────────────────────────────────────────────────
    private val _selectedTrack      = MutableStateFlow<Track?>(null)
    val selectedTrack: StateFlow<Track?> = _selectedTrack
    private val _isPlaying          = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private val _currentPosition    = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition
    private val _duration           = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration
    private val _isShuffled         = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled
    private val _repeatMode         = MutableStateFlow(0) // 0=off 1=all 2=one
    val repeatMode: StateFlow<Int> = _repeatMode
    private val _queue              = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue
    private val _isBuffering        = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering

    // ── Lyrics ────────────────────────────────────────────────
    private val _lyrics             = MutableStateFlow<String?>(null)
    val lyrics: StateFlow<String?> = _lyrics
    private val _lyricsEntries      = MutableStateFlow<List<LyricsEntry>>(emptyList())
    val lyricsEntries: StateFlow<List<LyricsEntry>> = _lyricsEntries
    private val _isLyricsLoading    = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading
    private val _currentLyricsIndex = MutableStateFlow(-1)
    val currentLyricsIndex: StateFlow<Int> = _currentLyricsIndex
    private val _lyricsIsSynced     = MutableStateFlow(false)
    val lyricsIsSynced: StateFlow<Boolean> = _lyricsIsSynced

    // ── Library ───────────────────────────────────────────────
    private val _favorites          = MutableStateFlow<List<FavoriteTrack>>(emptyList())
    val favorites: StateFlow<List<FavoriteTrack>> = _favorites
    private val _playlists          = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists
    val recentlyPlayed: Flow<List<SongEntity>> = db.musicDao().getRecentlyPlayed(50)
    val likedSongs: Flow<List<SongEntity>>     = db.musicDao().getLikedSongs()
    val downloadedSongs: Flow<List<SongEntity>> = db.musicDao().getDownloadedSongs()

    // ── Download ──────────────────────────────────────────────
    val downloadProgress: StateFlow<Map<String, Float>> = downloadMgr.downloadProgress

    // ── Sleep Timer ───────────────────────────────────────────
    private var sleepTimer: SleepTimer? = null
    private val _sleepTimerActive   = MutableStateFlow(false)
    val sleepTimerActive: StateFlow<Boolean> = _sleepTimerActive
    private val _sleepTimerRemaining = MutableStateFlow(-1L)
    val sleepTimerRemaining: StateFlow<Long> = _sleepTimerRemaining

    // ── Alarms ────────────────────────────────────────────────
    private val _alarms             = MutableStateFlow<List<MusicAlarmEntry>>(emptyList())
    val alarms: StateFlow<List<MusicAlarmEntry>> = _alarms

    // ── EQ ────────────────────────────────────────────────────
    val activeEqProfile: StateFlow<SavedEQProfile?> = eqProfileRepo.activeProfile

    // ── Image ─────────────────────────────────────────────────
    private val _generatedImageUrl  = MutableStateFlow<String?>(null)
    val generatedImageUrl: StateFlow<String?> = _generatedImageUrl

    // ── Analysis ──────────────────────────────────────────────
    private val _isAnalyzing        = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing
    private val _analysisResult     = MutableStateFlow<String?>(null)
    val analysisResult: StateFlow<String?> = _analysisResult

    // ── Init ──────────────────────────────────────────────────
    init {
        loadChatHistory()
        loadLibrary()
        loadTopCharts()
        loadSearchHistory()
        loadAlarms()
    }

    fun initPlayer(context: Context) {
        MusicPlayerManager.init(context)
        MusicPlayerManager.onTrackChanged = { nextTrack() }
        // Sleep timer init
        sleepTimer = MusicPlayerManager.player?.let { player ->
            SleepTimer(viewModelScope, player) { mult ->
                MusicPlayerManager.player?.volume = mult
            }
        }
        // Poll playback state
        viewModelScope.launch {
            while (true) {
                val player = MusicPlayerManager.player
                if (player != null) {
                    _isPlaying.value = player.isPlaying
                    _currentPosition.value = player.currentPosition.toInt().coerceAtLeast(0)
                    if (player.duration > 0) _duration.value = player.duration.toInt()
                    _isBuffering.value = player.playbackState == Media3Player.STATE_BUFFERING
                    // Sync lyrics index
                    if (_lyricsEntries.value.isNotEmpty()) {
                        _currentLyricsIndex.value = findCurrentLyricsIndex(
                            _lyricsEntries.value, _currentPosition.value.toLong()
                        )
                    }
                    // Sleep timer countdown
                    sleepTimer?.let { st ->
                        _sleepTimerActive.value = st.isActive
                        _sleepTimerRemaining.value = st.remainingMs()
                    }
                }
                delay(500)
            }
        }
    }

    // ── Settings ──────────────────────────────────────────────
    fun saveApiConfig(key: String, provider: String) {
        settingsRepo.saveApiKey(key); settingsRepo.saveApiProvider(provider)
        _apiKey.value = key; _apiProvider.value = provider
    }
    fun setThemeMode(mode: String) { settingsRepo.saveThemeMode(mode); _themeMode.value = mode }
    fun validateApiKey() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val key = _apiKey.value.trim()
            if (key.isBlank()) { _apiValidationResult.value = "❌ API Key kosong!"; return@launch }
            _apiValidationResult.value = "⏳ Memvalidasi ${_apiProvider.value}..."
            try {
                val result = aiRepo.generateResponse("Reply with exactly: VALID", key, _apiProvider.value)
                _apiValidationResult.value = when {
                    result.startsWith("ERROR_CONNECTION") -> "❌ Gagal konek. Cek internet."
                    result.startsWith("ERROR_401") -> "❌ API Key salah / expired."
                    result.startsWith("ERROR_403") -> "❌ Akses ditolak. Cek plan API."
                    result.startsWith("ERROR_429") -> "⚠️ Rate limit. Coba lagi sebentar."
                    result.startsWith("ERROR") -> "❌ Error: ${result.take(80)}"
                    else -> "✅ Valid! ${_apiProvider.value} siap digunakan."
                }
            } catch (e: Exception) {
                _apiValidationResult.value = "❌ Exception: ${e.message?.take(60)}"
            }
        }
    }

    // ── Chat ──────────────────────────────────────────────────
    private fun loadChatHistory() {
        viewModelScope.launch {
            val loggedIn = FirebaseAuth.getInstance().currentUser != null
            if (loggedIn) try {
                val cloud = cloudRepo.loadHistoryFromCloud()
                if (cloud.isNotEmpty()) { _chatMessages.value = cloud; chatRepo.saveHistory(cloud); return@launch }
            } catch (_: Exception) {}
            val local = chatRepo.loadHistory()
            _chatMessages.value = if (local.isEmpty()) {
                val welcome = listOf(ChatMessage(text = "Yo! Gue **LyronixAi** 🎵\n\nGue bisa bantu:\n• 🎵 Analisis musik & genre\n• 💻 Kode HTML/CSS/Web\n• 📝 Lirik & Suno AI prompt\n• 🖼️ Generate gambar GRATIS!\n• 🤖 Chat AI multi-provider\n\n⚙️ Set API Key di Settings!", isUser = false))
                chatRepo.saveHistory(welcome); welcome
            } else local
        }
    }

    fun sendMessage(text: String, category: String, hasImage: Boolean = false, hasAudio: Boolean = false) {
        if (text.isBlank() && !hasImage && !hasAudio) return
        val display = when { hasImage -> "📷 $text"; hasAudio -> "🎵 $text"; else -> text }
        addMessage(ChatMessage(text = display, isUser = true, hasImage = hasImage, hasAudio = hasAudio))
        viewModelScope.launch {
            _isAiTyping.value = true
            val key = _apiKey.value.trim()
            if (key.isBlank()) {
                addMessage(ChatMessage(text = "⚠️ **API Key belum diisi!**\n\nBuka Settings → masukkan key AI.\n\nGratis:\n• Gemini: aistudio.google.com\n• DeepSeek: platform.deepseek.com", isUser = false))
                _isAiTyping.value = false; return@launch
            }
            if (category == "Image Prompt") {
                val prompt = imageRepo.generateImagePromptFromText(text, "music")
                val url = imageRepo.generateImageUrl(prompt)
                addMessage(ChatMessage(text = "🎨 Gambar generated!\nPrompt: *$prompt*", isUser = false, hasImageResult = true, imageResultUrl = url))
                _generatedImageUrl.value = url; _isAiTyping.value = false; saveToCloud(); return@launch
            }
            try {
                val prompt = buildPrompt(text, category, hasImage, hasAudio)
                val response = aiRepo.generateResponse(prompt, key, _apiProvider.value)
                if (response.startsWith("ERROR_")) addMessage(ChatMessage(text = "❌ $response", isUser = false))
                else {
                    val isCode = (category == "Coding" || category == "Web Dev") && !hasImage && !hasAudio
                    val clean = response.replace("```html","").replace("```css","").replace("```javascript","").replace("```js","").replace("```","").trim()
                    addMessage(ChatMessage(text = if (isCode) "✅ Kode berhasil dibuat:" else response, isUser = false, hasCode = isCode, codeSnippet = if (isCode) clean else null))
                }
            } catch (e: Exception) { addMessage(ChatMessage(text = "❌ Error: ${e.message}", isUser = false)) }
            _isAiTyping.value = false; saveToCloud()
        }
    }

    private fun buildPrompt(text: String, category: String, hasImage: Boolean, hasAudio: Boolean) = when {
        hasImage -> "Kamu music & design expert. User upload gambar: $text. Analisa, jika UI berikan kode HTML+CSS."
        hasAudio -> "Kamu music producer expert. Audio: $text. Analisa: genre, BPM, key, mood, instrumen, mixing."
        category == "Analisis Musik" -> "Kamu music critic profesional. Analisis mendalam:\n\"$text\"\nCover: genre, sub-genre, BPM, key, mood, instrumen, struktur, reference artist, cara buat di Suno AI."
        category == "Lirik" -> "Kamu penulis lirik pro. Buat lirik Bahasa Indonesia tentang:\n\"$text\"\nFormat: [Intro][Verse 1][Pre-Chorus][Chorus][Verse 2][Bridge][Outro]."
        category == "Suno Prompt" -> "Kamu Suno AI expert. Buat prompt optimal:\n\"$text\"\nFormat:\n**Style Tags:** [genre, mood, tempo]\n**Prompt:** [deskripsi detail]\n**Negative:** [tidak diinginkan]\n**BPM & Key:** [estimasi]"
        category == "Coding" || category == "Web Dev" -> "Senior web dev. Buat kode lengkap:\n\"$text\"\nHanya kode HTML+CSS+JS. Tanpa penjelasan, tanpa backtick."
        else -> "AI assistant ahli musik & teknologi. Jawab:\n$text"
    }

    private fun addMessage(msg: ChatMessage) { _chatMessages.value = _chatMessages.value + msg; chatRepo.saveHistory(_chatMessages.value) }
    private suspend fun saveToCloud() { if (FirebaseAuth.getInstance().currentUser != null) try { cloudRepo.saveHistoryToCloud(_chatMessages.value) } catch (_: Exception) {} }
    fun clearChat() { _chatMessages.value = emptyList(); chatRepo.saveHistory(emptyList()) }

    // ── Discover / Search ─────────────────────────────────────
    fun updateSearchQuery(q: String) {
        _searchQuery.value = q
        if (q.length >= 2) viewModelScope.launch {
            _searchSuggestions.value = ytMusicRepo.getSearchSuggestions(q)
        }
    }

    fun search() {
        val q = _searchQuery.value.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            db.musicDao().addSearchHistory(SearchHistory(q))
            _searchHistory.value = (_searchHistory.value.toMutableList().also { it.remove(q) }).also { it.add(0, q) }.take(20)
            _searchResults.value = try {
                val yt = ytMusicRepo.search(q)
                if (yt.isNotEmpty()) yt.map { it.toTrack() }
                else {
                    val saavn = saavnApi.searchSongs(q)
                    if (saavn.isNotEmpty()) saavn.map { it.toSaavnTrack() } else musicRepo.searchSongs(q)
                }
            } catch (e: Exception) { try { musicRepo.searchSongs(q) } catch (_: Exception) { emptyList() } }
            _isLoading.value = false
        }
    }

    fun loadGenre(genre: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _searchResults.value = try {
                val yt = ytMusicRepo.search("$genre music hits")
                if (yt.isNotEmpty()) yt.map { it.toTrack() } else musicRepo.getTopCharts("ID", genre)
            } catch (e: Exception) { emptyList() }
            _isLoading.value = false
        }
    }

    private fun loadTopCharts() {
        viewModelScope.launch {
            try {
                val yt = ytMusicRepo.getTopCharts()
                _topCharts.value = if (yt.isNotEmpty()) yt.map { it.toTrack() }
                else {
                    val saavn = saavnApi.getTopCharts()
                    if (saavn.isNotEmpty()) saavn.map { it.toSaavnTrack() } else musicRepo.getTopCharts()
                }
            } catch (e: Exception) { try { _topCharts.value = musicRepo.getTopCharts() } catch (_: Exception) {} }
        }
    }

    private fun loadSearchHistory() {
        viewModelScope.launch {
            db.musicDao().getSearchHistory().collect { list ->
                _searchHistory.value = list.map { it.query }
            }
        }
    }

    fun clearSearchHistory() { viewModelScope.launch { db.musicDao().clearSearchHistory(); _searchHistory.value = emptyList() } }
    fun deleteSearchHistoryItem(q: String) { viewModelScope.launch { db.musicDao().deleteSearchHistory(q) } }

    // ── Track Selection & Playback ────────────────────────────
    fun selectTrack(track: Track, playlistTracks: List<Track> = emptyList()) {
        try {
            _selectedTrack.value = track
            _lyrics.value = null
            _lyricsEntries.value = emptyList()
            _currentLyricsIndex.value = -1
            _isBuffering.value = true
            stopAudio()
            when {
                playlistTracks.isNotEmpty() -> _queue.value = playlistTracks
                _searchResults.value.any { it.trackId == track.trackId } -> _queue.value = _searchResults.value
                _topCharts.value.any { it.trackId == track.trackId } -> _queue.value = _topCharts.value
            }
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    db.musicDao().upsertSong(track.toSongEntity())
                    db.musicDao().incrementPlayTime(track.toSongEntity().id, 0L)
                } catch (_: Exception) {}
            }
            fetchLyricsAndPlay(track)
            fetchRelated(track)
        } catch (e: Exception) {
            Timber.e(e, "selectTrack failed")
        }
    }

    private fun fetchLyricsAndPlay(track: Track) {
        MusicPlayerManager.MUSIC_ID = track.ytVideoId.ifBlank { track.saavnId.ifBlank { track.trackId.toString() } }
        MusicPlayerManager.MUSIC_TITLE = track.trackName
        MusicPlayerManager.MUSIC_DESCRIPTION = track.artistName
        MusicPlayerManager.IMAGE_URL = track.artworkUrl100.replace("100x100", "500x500")
        val queueIds = _queue.value.map { it.ytVideoId.ifBlank { it.saavnId.ifBlank { it.trackId.toString() } } }
        if (queueIds.isNotEmpty()) {
            MusicPlayerManager.trackQueue = queueIds.toMutableList()
            MusicPlayerManager.track_position = _queue.value.indexOfFirst { it.trackId == track.trackId }
        }

        // Lyrics — cek cache DB dulu
        viewModelScope.launch {
            _isLyricsLoading.value = true
            val songId = track.ytVideoId.ifBlank { track.saavnId.ifBlank { track.trackId.toString() } }
            val cached = db.musicDao().getLyrics(songId)
            if (cached != null && cached.lyrics.isNotBlank()) {
                applyLyrics(cached.lyrics, cached.isSynced)
            } else {
                val result = LyricsRegistry.getLyrics(track.trackName, track.artistName)
                if (result.raw != null) {
                    db.musicDao().upsertLyrics(LyricsEntity(songId, result.raw, result.isSynced, result.source ?: ""))
                    applyLyrics(result.raw, result.isSynced)
                } else {
                    // Fallback Saavn/iTunes
                    val lyricsText = try {
                        when {
                            track.saavnId.isNotBlank() -> saavnApi.getLyrics(track.saavnId)
                            else -> musicRepo.getLyrics(track.artistName, track.trackName)
                        }
                    } catch (_: Exception) { null }
                    if (lyricsText != null) {
                        db.musicDao().upsertLyrics(LyricsEntity(songId, lyricsText, lyricsLooksSynced(lyricsText), "saavn"))
                        applyLyrics(lyricsText, lyricsLooksSynced(lyricsText))
                    } else {
                        _lyrics.value = "Lyrics not found."
                    }
                }
            }
            _isLyricsLoading.value = false
        }

        // Audio - fetch di IO thread, play di Main thread
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val audioUrl = when {
                    track.ytVideoId.isNotBlank() -> {
                        val url = try { ytMusicRepo.getStreamUrl(track.ytVideoId) } catch (_: Exception) { "" }
                        url.ifBlank { track.previewUrl }
                    }
                    track.fullAudioUrl.isNotBlank() && !track.fullAudioUrl.startsWith("ytmusic://") ->
                        track.fullAudioUrl
                    track.saavnId.isNotBlank() -> try {
                        val song = saavnApi.getSongById(track.saavnId)
                        val url = song?.downloadUrl?.lastOrNull()?.url ?: ""
                        (if (url.startsWith("http:")) url.replace("http:", "https:") else url).ifBlank { track.previewUrl }
                    } catch (_: Exception) { track.previewUrl }
                    else -> track.previewUrl
                }
                if (audioUrl.isNotBlank()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        MusicPlayerManager.SONG_URL = audioUrl
                        MusicPlayerManager.prepareMediaPlayer()
                    }
                }
                Timber.d("Audio URL: $audioUrl")
            } catch (e: Exception) {
                Timber.e(e, "fetchAudio failed")
                if (track.previewUrl.isNotBlank()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        MusicPlayerManager.SONG_URL = track.previewUrl
                        MusicPlayerManager.prepareMediaPlayer()
                    }
                }
            }
        }
    }

    private fun applyLyrics(raw: String, synced: Boolean) {
        _lyrics.value = raw
        _lyricsIsSynced.value = synced
        _lyricsEntries.value = if (synced) parseLrc(raw) else emptyList()
    }

    private fun fetchRelated(track: Track) {
        viewModelScope.launch {
            try {
                _relatedTracks.value = when {
                    track.ytVideoId.isNotBlank() -> {
                        val yt = ytMusicRepo.getRelatedTracks(track.ytVideoId)
                        if (yt.isNotEmpty()) yt.map { it.toTrack() } else musicRepo.getRelatedTracks(track.artistName)
                    }
                    track.saavnId.isNotBlank() -> saavnApi.getSuggestions(track.saavnId).map { it.toSaavnTrack() }
                    else -> musicRepo.getRelatedTracks(track.artistName)
                }
            } catch (_: Exception) {}
        }
    }

    // ── Library ───────────────────────────────────────────────
    private fun loadLibrary() {
        try {
            _favorites.value = json.decodeFromString<List<FavoriteTrack>>(settingsRepo.getFavorites())
            _playlists.value = json.decodeFromString<List<Playlist>>(settingsRepo.getPlaylists())
        } catch (e: Exception) {
            _favorites.value = emptyList()
            _playlists.value = listOf(Playlist(name = "Liked Songs"), Playlist(name = "My Playlist #1"))
        }
    }

    fun toggleFavorite(track: Track) {
        val cur = _favorites.value.toMutableList()
        val exists = cur.find { it.trackId == track.trackId }
        if (exists != null) cur.remove(exists) else cur.add(0, FavoriteTrack(track.trackId, track.trackName, track.artistName, track.artworkUrl100, track.previewUrl, track.primaryGenreName))
        _favorites.value = cur
        try { settingsRepo.saveFavorites(json.encodeToString<List<FavoriteTrack>>(cur)) } catch (_: Exception) {}
        viewModelScope.launch {
            val songId = track.ytVideoId.ifBlank { track.saavnId.ifBlank { track.trackId.toString() } }
            db.musicDao().setLiked(songId, exists == null)
        }
    }
    fun isFavorite(trackId: Long) = _favorites.value.any { it.trackId == trackId }

    fun createPlaylist(name: String) {
        val cur = _playlists.value + Playlist(name = name)
        _playlists.value = cur
        try { settingsRepo.savePlaylists(json.encodeToString<List<Playlist>>(cur)) } catch (_: Exception) {}
    }
    fun addTrackToPlaylist(playlistId: String, track: Track) {
        val cur = _playlists.value.map { pl ->
            if (pl.id == playlistId && pl.tracks.none { it.trackId == track.trackId })
                pl.copy(tracks = pl.tracks + track, coverUrl = if (pl.coverUrl.isBlank()) track.artworkUrl100 else pl.coverUrl)
            else pl
        }
        _playlists.value = cur
        try { settingsRepo.savePlaylists(json.encodeToString<List<Playlist>>(cur)) } catch (_: Exception) {}
    }
    fun deletePlaylist(playlistId: String) {
        val cur = _playlists.value.filter { it.id != playlistId }
        _playlists.value = cur
        try { settingsRepo.savePlaylists(json.encodeToString<List<Playlist>>(cur)) } catch (_: Exception) {}
    }

    // ── Player Controls ───────────────────────────────────────
    fun togglePlayPause() { MusicPlayerManager.togglePlayPause() }
    fun stopAudio() {
        try { MusicPlayerManager.player?.stop() } catch (_: Exception) {}
        _isPlaying.value = false
        _currentPosition.value = 0
        _isBuffering.value = false
    }
    fun seekTo(ms: Int) { MusicPlayerManager.player?.seekTo(ms.toLong()); _currentPosition.value = ms }
    fun skipForward() = seekTo((_currentPosition.value + 10000).coerceAtMost(_duration.value))
    fun skipBackward() = seekTo((_currentPosition.value - 10000).coerceAtLeast(0))
    fun toggleShuffle() { _isShuffled.value = !_isShuffled.value }
    fun toggleRepeat() { _repeatMode.value = (_repeatMode.value + 1) % 3 }

    fun nextTrack() {
        val list = if (_isShuffled.value) _queue.value.shuffled() else _queue.value
        if (list.isEmpty()) return
        val cur = _selectedTrack.value ?: return
        val idx = list.indexOfFirst { it.trackId == cur.trackId }
        when {
            idx < list.size - 1 -> selectTrack(list[idx + 1], list)
            _repeatMode.value == 1 -> selectTrack(list[0], list)
            _repeatMode.value == 2 -> fetchLyricsAndPlay(cur)
        }
        sleepTimer?.notifySongTransition()
    }

    fun previousTrack() {
        if (_currentPosition.value > 3000) { seekTo(0); return }
        val list = _queue.value
        val cur = _selectedTrack.value ?: return
        val idx = list.indexOfFirst { it.trackId == cur.trackId }
        if (idx > 0) selectTrack(list[idx - 1], list)
    }

    // ── Sleep Timer ───────────────────────────────────────────
    fun setSleepTimer(minutes: Int, stopAfterCurrentSong: Boolean = false, fadeOut: Boolean = false) {
        val player = MusicPlayerManager.player ?: return
        if (sleepTimer == null) sleepTimer = SleepTimer(viewModelScope, player) { MusicPlayerManager.player?.volume = it }
        sleepTimer!!.start(minutes, stopAfterCurrentSong, fadeOut)
        _sleepTimerActive.value = true
    }
    fun cancelSleepTimer() { sleepTimer?.clear(); _sleepTimerActive.value = false; _sleepTimerRemaining.value = -1L }

    // ── Download ──────────────────────────────────────────────
    fun downloadTrack(track: Track) {
        viewModelScope.launch {
            val audioUrl = when {
                track.ytVideoId.isNotBlank() -> try { ytMusicRepo.getStreamUrl(track.ytVideoId) } catch (_: Exception) { "" }
                track.saavnId.isNotBlank() -> try { saavnApi.getSongById(track.saavnId)?.downloadUrl?.lastOrNull()?.url ?: "" } catch (_: Exception) { "" }
                else -> track.previewUrl
            }
            downloadMgr.downloadSong(track.toSongEntity().copy(audioUrl = audioUrl))
        }
    }
    fun cancelDownload(songId: String) = downloadMgr.cancelDownload(songId)
    fun deleteDownload(songId: String) = downloadMgr.deleteDownload(songId)
    fun getDownloadedPath(songId: String) = downloadMgr.getDownloadedFilePath(songId)

    // ── EQ ────────────────────────────────────────────────────
    fun applyEqProfile(profile: SavedEQProfile) {
        eqProfileRepo.setActiveProfile(profile)
        EqualizerManager.applyProfile(profile)
    }
    fun disableEq() { eqProfileRepo.setActiveProfile(null); EqualizerManager.disable() }
    fun saveCustomEqProfile(profile: SavedEQProfile) = eqProfileRepo.saveProfile(profile)
    fun deleteEqProfile(id: String) = eqProfileRepo.deleteProfile(id)

    // ── Alarms ────────────────────────────────────────────────
    private fun loadAlarms() { _alarms.value = MusicAlarmStore.load(getApplication()) }

    fun addAlarm(entry: MusicAlarmEntry) {
        val updated = _alarms.value + entry
        _alarms.value = updated
        MusicAlarmScheduler.scheduleAll(getApplication(), updated)
    }
    fun updateAlarm(entry: MusicAlarmEntry) {
        val updated = _alarms.value.map { if (it.id == entry.id) entry else it }
        _alarms.value = updated
        MusicAlarmScheduler.scheduleAll(getApplication(), updated)
    }
    fun deleteAlarm(id: String) {
        MusicAlarmScheduler.cancel(getApplication(), id)
        val updated = _alarms.value.filter { it.id != id }
        _alarms.value = updated
        MusicAlarmStore.save(getApplication(), updated)
    }
    fun toggleAlarm(id: String) {
        val entry = _alarms.value.find { it.id == id } ?: return
        updateAlarm(entry.copy(enabled = !entry.enabled))
    }

    // ── Analysis ──────────────────────────────────────────────
    fun simulateAnalysis() {
        viewModelScope.launch {
            _isAnalyzing.value = true; _analysisResult.value = null
            delay(2000); _analysisResult.value = "Upload file audio untuk analisa real."
            _isAnalyzing.value = false
        }
    }

    override fun onCleared() { super.onCleared(); MusicPlayerManager.player?.stop(); EqualizerManager.release() }
}

// ── Helper extension ──────────────────────────────────────
private fun Track.toSongEntity() = SongEntity(
    id = ytVideoId.ifBlank { saavnId.ifBlank { trackId.toString() } },
    title = trackName, artist = artistName,
    album = collectionName, duration = (trackTimeMillis / 1000).toInt(),
    thumbnailUrl = artworkUrl100, audioUrl = previewUrl,
    lastPlayedTime = System.currentTimeMillis()
)
