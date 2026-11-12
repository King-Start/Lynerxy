package com.agon.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.alarm.MusicAlarmEntry
import com.agon.app.alarm.MusicAlarmScheduler
import com.agon.app.alarm.MusicAlarmStore
import com.agon.app.data.*
import com.agon.app.db.AppDatabase
import com.agon.app.db.entities.LyricsEntity
import com.agon.app.db.entities.SearchHistory
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val ytRepo         = YouTubeMusicRepository()
    private val saavnApi       = SaavnApiManager()
    private val musicRepo      = MusicRepository()
    private val chatRepo       = ChatRepository(application)
    private val cloudRepo      = CloudRepository()
    private val settingsRepo   = SettingsRepository(application)
    private val aiRepo         = AiRepository()
    private val imageRepo      = ImageRepository()
    private val db             = AppDatabase.getInstance(application)
    private val downloadMgr    = DownloadManager(application)
    val eqProfileRepo          = EQProfileRepository(application)
    private val json           = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── Settings ──────────────────────────────────────────────
    private val _apiKey      = MutableStateFlow(settingsRepo.getApiKey())
    val apiKey: StateFlow<String> = _apiKey
    private val _apiProvider = MutableStateFlow(settingsRepo.getApiProvider())
    val apiProvider: StateFlow<String> = _apiProvider
    private val _apiValidation = MutableStateFlow<String?>(null)
    val apiValidationResult: StateFlow<String?> = _apiValidation
    private val _themeMode   = MutableStateFlow(settingsRepo.getThemeMode())
    val themeMode: StateFlow<String> = _themeMode
    private val _mediaType   = MutableStateFlow("Music")
    val mediaType: StateFlow<String> = _mediaType

    // ── Chat ──────────────────────────────────────────────────
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages
    private val _isAiTyping  = MutableStateFlow(false)
    val isAiTyping: StateFlow<Boolean> = _isAiTyping

    // ── Discover ──────────────────────────────────────────────
    private val _searchQuery   = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults: StateFlow<List<Track>> = _searchResults
    private val _isLoading     = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _topCharts     = MutableStateFlow<List<Track>>(emptyList())
    val topCharts: StateFlow<List<Track>> = _topCharts
    private val _relatedTracks = MutableStateFlow<List<Track>>(emptyList())
    val relatedTracks: StateFlow<List<Track>> = _relatedTracks
    private val _suggestions   = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _suggestions
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory

    // ── Player ────────────────────────────────────────────────
    private val _selectedTrack = MutableStateFlow<Track?>(null)
    val selectedTrack: StateFlow<Track?> = _selectedTrack
    private val _isPlaying     = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private val _currentPos    = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPos
    private val _duration      = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration
    private val _isBuffering   = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering
    private val _isShuffled    = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled
    private val _repeatMode    = MutableStateFlow(0)
    val repeatMode: StateFlow<Int> = _repeatMode
    private val _queue         = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue

    // ── Lyrics ────────────────────────────────────────────────
    private val _lyrics        = MutableStateFlow<String?>(null)
    val lyrics: StateFlow<String?> = _lyrics
    private val _lyricsEntries = MutableStateFlow<List<LyricsEntry>>(emptyList())
    val lyricsEntries: StateFlow<List<LyricsEntry>> = _lyricsEntries
    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading
    private val _currentLyricsIndex = MutableStateFlow(-1)
    val currentLyricsIndex: StateFlow<Int> = _currentLyricsIndex
    private val _lyricsIsSynced = MutableStateFlow(false)
    val lyricsIsSynced: StateFlow<Boolean> = _lyricsIsSynced

    // ── Library ───────────────────────────────────────────────
    private val _favorites  = MutableStateFlow<List<FavoriteTrack>>(emptyList())
    val favorites: StateFlow<List<FavoriteTrack>> = _favorites
    private val _playlists  = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists
    val recentlyPlayed      = db.musicDao().getRecentlyPlayed(50)
    val likedSongs          = db.musicDao().getLikedSongs()
    val downloadedSongs     = db.musicDao().getDownloadedSongs()

    // ── Download ──────────────────────────────────────────────
    val downloadProgress: StateFlow<Map<String, Float>> = downloadMgr.downloadProgress

    // ── Sleep Timer ───────────────────────────────────────────
    private var sleepTimer: SleepTimer? = null
    private val _sleepTimerActive = MutableStateFlow(false)
    val sleepTimerActive: StateFlow<Boolean> = _sleepTimerActive
    private val _sleepTimerRemaining = MutableStateFlow(-1L)
    val sleepTimerRemaining: StateFlow<Long> = _sleepTimerRemaining

    // ── Alarms ────────────────────────────────────────────────
    private val _alarms = MutableStateFlow<List<MusicAlarmEntry>>(emptyList())
    val alarms: StateFlow<List<MusicAlarmEntry>> = _alarms

    // ── EQ ────────────────────────────────────────────────────
    val activeEqProfile: StateFlow<SavedEQProfile?> = eqProfileRepo.activeProfile

    // ── Analysis ──────────────────────────────────────────────
    private val _isAnalyzing    = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing
    private val _analysisResult = MutableStateFlow<String?>(null)
    val analysisResult: StateFlow<String?> = _analysisResult
    private val _generatedImageUrl = MutableStateFlow<String?>(null)
    val generatedImageUrl: StateFlow<String?> = _generatedImageUrl

    init {
        loadLibrary()
        loadChatHistory()
        loadTopCharts()
        loadSearchHistory()
        loadAlarms()
    }

    // ─────────────────────────────────────────────────────────
    // INIT PLAYER
    // ─────────────────────────────────────────────────────────
    fun initPlayer(context: Context) {
        MusicPlayerManager.init(context)
        MusicPlayerManager.onTrackChanged = { nextTrack() }
        MusicPlayerManager.player?.let { p ->
            sleepTimer = SleepTimer(viewModelScope, p) { vol -> MusicPlayerManager.player?.volume = vol }
        }
        // Poll player state setiap 500ms
        viewModelScope.launch {
            while (true) {
                delay(500)
                val p = MusicPlayerManager.player ?: continue
                _isPlaying.value = p.isPlaying
                _currentPos.value = p.currentPosition.toInt().coerceAtLeast(0)
                if (p.duration > 0) _duration.value = p.duration.toInt()
                _isBuffering.value = p.playbackState == androidx.media3.common.Player.STATE_BUFFERING
                if (_lyricsEntries.value.isNotEmpty()) {
                    _currentLyricsIndex.value = findCurrentLyricsIndex(_lyricsEntries.value, _currentPos.value.toLong())
                }
                sleepTimer?.let { st ->
                    _sleepTimerActive.value = st.isActive
                    _sleepTimerRemaining.value = st.remainingMs()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // SELECT TRACK — entry point utama saat user tap lagu
    // ─────────────────────────────────────────────────────────
    fun selectTrack(track: Track, playlistTracks: List<Track> = emptyList()) {
        Timber.d("selectTrack: ${track.trackName} ytId=${track.ytVideoId} saavnId=${track.saavnId}")
        _selectedTrack.value = track
        _lyrics.value = null
        _lyricsEntries.value = emptyList()
        _currentLyricsIndex.value = -1
        _isBuffering.value = true

        // Update queue
        when {
            playlistTracks.isNotEmpty() -> _queue.value = playlistTracks
            _searchResults.value.any { it.trackId == track.trackId } -> _queue.value = _searchResults.value
            _topCharts.value.any { it.trackId == track.trackId } -> _queue.value = _topCharts.value
        }

        // Save ke DB (background)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.musicDao().upsertSong(track.toSongEntity())
                db.musicDao().incrementPlayTime(track.toSongEntity().id, 0L)
            } catch (_: Exception) {}
        }

        // Fetch audio URL lalu play
        viewModelScope.launch {
            fetchAndPlay(track)
        }

        // Fetch lyrics (background, tidak blocking audio)
        viewModelScope.launch {
            fetchLyrics(track)
        }

        // Fetch related
        viewModelScope.launch {
            fetchRelated(track)
        }
    }

    private suspend fun fetchAndPlay(track: Track) {
        _isBuffering.value = true
        try {
            val audioUrl = withContext(Dispatchers.IO) {
                when {
                    // 1. Punya ytVideoId → pakai YouTube Music
                    track.ytVideoId.isNotBlank() -> {
                        val url = ytRepo.getStreamUrl(track.ytVideoId)
                        Timber.d("YT stream url: '$url'")
                        url.ifBlank { track.previewUrl }
                    }
                    // 2. fullAudioUrl sudah ada (dari Saavn)
                    track.fullAudioUrl.isNotBlank() && !track.fullAudioUrl.startsWith("ytmusic://") ->
                        track.fullAudioUrl
                    // 3. Coba Saavn
                    track.saavnId.isNotBlank() -> {
                        try {
                            val song = saavnApi.getSongById(track.saavnId)
                            val url = song?.downloadUrl?.lastOrNull()?.url ?: ""
                            (if (url.startsWith("http:")) url.replace("http:", "https:") else url)
                                .ifBlank { track.previewUrl }
                        } catch (_: Exception) { track.previewUrl }
                    }
                    // 4. Preview URL fallback
                    else -> track.previewUrl
                }
            }
            Timber.d("fetchAndPlay final url: '$audioUrl'")
            if (audioUrl.isNotBlank()) {
                withContext(Dispatchers.Main) {
                    MusicPlayerManager.play(audioUrl)
                }
            } else {
                Timber.w("fetchAndPlay: no audio URL found for ${track.trackName}")
                _isBuffering.value = false
            }
        } catch (e: Exception) {
            Timber.e(e, "fetchAndPlay crashed")
            _isBuffering.value = false
            // Fallback: coba preview URL
            if (track.previewUrl.isNotBlank()) {
                withContext(Dispatchers.Main) { MusicPlayerManager.play(track.previewUrl) }
            }
        }
    }

    private suspend fun fetchLyrics(track: Track) {
        _isLyricsLoading.value = true
        try {
            val songId = track.ytVideoId.ifBlank { track.saavnId.ifBlank { track.trackId.toString() } }
            // Cek cache DB
            val cached = withContext(Dispatchers.IO) { db.musicDao().getLyrics(songId) }
            if (cached != null && cached.lyrics.isNotBlank()) {
                applyLyrics(cached.lyrics, cached.isSynced)
                _isLyricsLoading.value = false
                return
            }
            // Fetch dari provider
            val result = withContext(Dispatchers.IO) {
                LyricsRegistry.getLyrics(track.trackName, track.artistName)
            }
            if (!result.raw.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    db.musicDao().upsertLyrics(LyricsEntity(songId, result.raw, result.isSynced, result.source ?: ""))
                }
                applyLyrics(result.raw, result.isSynced)
            } else if (track.saavnId.isNotBlank()) {
                val saavnLyrics = withContext(Dispatchers.IO) {
                    try { saavnApi.getLyrics(track.saavnId) } catch (_: Exception) { null }
                }
                if (!saavnLyrics.isNullOrBlank()) applyLyrics(saavnLyrics, lyricsLooksSynced(saavnLyrics))
                else _lyrics.value = null
            } else {
                _lyrics.value = null
            }
        } catch (e: Exception) {
            Timber.e(e, "fetchLyrics failed")
            _lyrics.value = null
        }
        _isLyricsLoading.value = false
    }

    private fun applyLyrics(raw: String, synced: Boolean) {
        _lyrics.value = raw
        _lyricsIsSynced.value = synced
        _lyricsEntries.value = if (synced) parseLrc(raw) else emptyList()
    }

    private fun fetchRelated(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val related = when {
                    track.ytVideoId.isNotBlank() -> ytRepo.getRelatedTracks(track.ytVideoId).map { it.toTrack() }
                    track.saavnId.isNotBlank() -> saavnApi.getSuggestions(track.saavnId).map { it.toSaavnTrack() }
                    else -> emptyList()
                }
                _relatedTracks.value = related
            } catch (_: Exception) {}
        }
    }

    // ─────────────────────────────────────────────────────────
    // PLAYER CONTROLS
    // ─────────────────────────────────────────────────────────
    fun togglePlayPause() {
        Timber.d("togglePlayPause isPlaying=${MusicPlayerManager.isPlaying()}")
        MusicPlayerManager.togglePlayPause()
    }

    fun seekTo(ms: Int) {
        MusicPlayerManager.seekTo(ms.toLong())
        _currentPos.value = ms
    }

    fun skipForward()  = seekTo((_currentPos.value + 10_000).coerceAtMost(_duration.value))
    fun skipBackward() = seekTo((_currentPos.value - 10_000).coerceAtLeast(0))
    fun toggleShuffle() { _isShuffled.value = !_isShuffled.value }
    fun toggleRepeat()  { _repeatMode.value = (_repeatMode.value + 1) % 3 }

    fun nextTrack() {
        val list = if (_isShuffled.value) _queue.value.shuffled() else _queue.value
        if (list.isEmpty()) return
        val cur = _selectedTrack.value ?: return
        val idx = list.indexOfFirst { it.trackId == cur.trackId }
        when {
            idx < list.size - 1 -> selectTrack(list[idx + 1], list)
            _repeatMode.value == 1 -> selectTrack(list[0], list)
            _repeatMode.value == 2 -> viewModelScope.launch { fetchAndPlay(cur) }
        }
        sleepTimer?.notifySongTransition()
    }

    fun previousTrack() {
        if (_currentPos.value > 3000) { seekTo(0); return }
        val list = _queue.value
        val cur = _selectedTrack.value ?: return
        val idx = list.indexOfFirst { it.trackId == cur.trackId }
        if (idx > 0) selectTrack(list[idx - 1], list)
    }

    fun stopAudio() {
        try { MusicPlayerManager.player?.stop() } catch (_: Exception) {}
        _isPlaying.value = false
        _currentPos.value = 0
        _isBuffering.value = false
    }

    // ─────────────────────────────────────────────────────────
    // SEARCH
    // ─────────────────────────────────────────────────────────
    fun updateSearchQuery(q: String) {
        _searchQuery.value = q
        if (q.length >= 2) viewModelScope.launch {
            _suggestions.value = ytRepo.getSearchSuggestions(q)
        }
    }

    fun search() {
        val q = _searchQuery.value.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _searchResults.value = emptyList()
            try {
                // Simpan ke history
                withContext(Dispatchers.IO) {
                    db.musicDao().addSearchHistory(SearchHistory(q))
                }
                // Search YTMusic
                val ytResults = ytRepo.search(q)
                if (ytResults.isNotEmpty()) {
                    _searchResults.value = ytResults.map { it.toTrack() }
                } else {
                    // Fallback Saavn
                    val saavnResults = withContext(Dispatchers.IO) {
                        try { saavnApi.searchSongs(q) } catch (_: Exception) { emptyList() }
                    }
                    if (saavnResults.isNotEmpty()) {
                        _searchResults.value = saavnResults.map { it.toSaavnTrack() }
                    } else {
                        // Fallback iTunes
                        _searchResults.value = withContext(Dispatchers.IO) {
                            try { musicRepo.searchSongs(q) } catch (_: Exception) { emptyList() }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "search failed")
            }
            _isLoading.value = false
        }
    }

    fun loadGenre(genre: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _searchQuery.value = genre
            try {
                val results = ytRepo.search("$genre music best hits")
                _searchResults.value = if (results.isNotEmpty()) results.map { it.toTrack() }
                else withContext(Dispatchers.IO) { musicRepo.getTopCharts("ID", genre) }
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }

    private fun loadTopCharts() {
        viewModelScope.launch {
            try {
                val yt = ytRepo.getTopCharts()
                _topCharts.value = if (yt.isNotEmpty()) yt.map { it.toTrack() }
                else {
                    val saavn = withContext(Dispatchers.IO) { saavnApi.getTopCharts() }
                    if (saavn.isNotEmpty()) saavn.map { it.toSaavnTrack() }
                    else withContext(Dispatchers.IO) { musicRepo.getTopCharts() }
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadSearchHistory() {
        viewModelScope.launch {
            db.musicDao().getSearchHistory().collect { list ->
                _searchHistory.value = list.map { it.query }
            }
        }
    }

    fun clearSearchHistory() = viewModelScope.launch(Dispatchers.IO) {
        db.musicDao().clearSearchHistory()
        _searchHistory.value = emptyList()
    }

    // ─────────────────────────────────────────────────────────
    // LIBRARY
    // ─────────────────────────────────────────────────────────
    private fun loadLibrary() {
        try {
            _favorites.value = json.decodeFromString<List<FavoriteTrack>>(settingsRepo.getFavorites())
            _playlists.value = json.decodeFromString<List<Playlist>>(settingsRepo.getPlaylists())
        } catch (_: Exception) {
            _favorites.value = emptyList()
            _playlists.value = listOf(Playlist(name = "Liked Songs"), Playlist(name = "My Playlist #1"))
        }
    }

    fun toggleFavorite(track: Track) {
        val list = _favorites.value.toMutableList()
        val existing = list.find { it.trackId == track.trackId }
        if (existing != null) list.remove(existing)
        else list.add(0, FavoriteTrack(track.trackId, track.trackName, track.artistName, track.artworkUrl100, track.previewUrl, track.primaryGenreName))
        _favorites.value = list
        try { settingsRepo.saveFavorites(json.encodeToString<List<FavoriteTrack>>(list)) } catch (_: Exception) {}
        viewModelScope.launch(Dispatchers.IO) {
            try { db.musicDao().setLiked(track.toSongEntity().id, existing == null) } catch (_: Exception) {}
        }
    }

    fun isFavorite(trackId: Long) = _favorites.value.any { it.trackId == trackId }

    fun createPlaylist(name: String) {
        val list = _playlists.value + Playlist(name = name)
        _playlists.value = list
        try { settingsRepo.savePlaylists(json.encodeToString<List<Playlist>>(list)) } catch (_: Exception) {}
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        val list = _playlists.value.map { pl ->
            if (pl.id == playlistId && pl.tracks.none { it.trackId == track.trackId })
                pl.copy(tracks = pl.tracks + track, coverUrl = if (pl.coverUrl.isBlank()) track.artworkUrl100 else pl.coverUrl)
            else pl
        }
        _playlists.value = list
        try { settingsRepo.savePlaylists(json.encodeToString<List<Playlist>>(list)) } catch (_: Exception) {}
    }

    fun deletePlaylist(id: String) {
        val list = _playlists.value.filter { it.id != id }
        _playlists.value = list
        try { settingsRepo.savePlaylists(json.encodeToString<List<Playlist>>(list)) } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────
    // SETTINGS / AI
    // ─────────────────────────────────────────────────────────
    fun saveApiConfig(key: String, provider: String) {
        settingsRepo.saveApiKey(key); settingsRepo.saveApiProvider(provider)
        _apiKey.value = key; _apiProvider.value = provider
    }

    fun setThemeMode(mode: String) { settingsRepo.saveThemeMode(mode); _themeMode.value = mode }

    fun validateApiKey() {
        viewModelScope.launch(Dispatchers.IO) {
            val key = _apiKey.value.trim()
            if (key.isBlank()) { _apiValidation.value = "❌ API Key kosong!"; return@launch }
            _apiValidation.value = "⏳ Memvalidasi ${_apiProvider.value}..."
            try {
                val result = aiRepo.generateResponse("Reply with: VALID", key, _apiProvider.value)
                _apiValidation.value = when {
                    result.startsWith("ERROR_401") -> "❌ API Key salah / expired"
                    result.startsWith("ERROR_403") -> "❌ Akses ditolak. Cek plan API"
                    result.startsWith("ERROR_429") -> "⚠️ Rate limit. Coba lagi nanti"
                    result.startsWith("ERROR_CONNECTION") -> "❌ Gagal konek. Cek internet"
                    result.startsWith("ERROR") -> "❌ Error: ${result.take(80)}"
                    else -> "✅ ${_apiProvider.value} siap digunakan!"
                }
            } catch (e: Exception) {
                _apiValidation.value = "❌ ${e.message?.take(60)}"
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // CHAT / AI
    // ─────────────────────────────────────────────────────────
    private fun loadChatHistory() {
        viewModelScope.launch {
            val loggedIn = FirebaseAuth.getInstance().currentUser != null
            if (loggedIn) {
                try {
                    val cloud = cloudRepo.loadHistoryFromCloud()
                    if (cloud.isNotEmpty()) { _chatMessages.value = cloud; return@launch }
                } catch (_: Exception) {}
            }
            val local = chatRepo.loadHistory()
            _chatMessages.value = if (local.isEmpty()) {
                val welcome = listOf(ChatMessage(
                    text = "Yo! Gue **LyronixAi** 🎵\n\nBisa bantu:\n• 🎵 Analisis musik\n• 📝 Lirik & Suno prompt\n• 💻 Kode HTML/CSS/JS\n• 🖼️ Generate gambar GRATIS\n\n⚙️ Set API Key di Settings dulu!",
                    isUser = false
                ))
                chatRepo.saveHistory(welcome); welcome
            } else local
        }
    }

    fun sendMessage(text: String, category: String, hasImage: Boolean = false, hasAudio: Boolean = false) {
        if (text.isBlank() && !hasImage && !hasAudio) return
        val display = when { hasImage -> "📷 $text"; hasAudio -> "🎵 $text"; else -> text }
        addMsg(ChatMessage(text = display, isUser = true, hasImage = hasImage, hasAudio = hasAudio))
        viewModelScope.launch {
            _isAiTyping.value = true
            val key = _apiKey.value.trim()
            if (key.isBlank()) {
                addMsg(ChatMessage(text = "⚠️ **API Key belum diisi!**\n\nBuka Settings dan masukkan key.\n\nGratis:\n• Gemini → aistudio.google.com\n• DeepSeek → platform.deepseek.com", isUser = false))
                _isAiTyping.value = false; return@launch
            }
            try {
                if (category == "Image Prompt") {
                    val prompt = withContext(Dispatchers.IO) { imageRepo.generateImagePromptFromText(text, "music") }
                    val url = withContext(Dispatchers.IO) { imageRepo.generateImageUrl(prompt) }
                    addMsg(ChatMessage(text = "🎨 Gambar berhasil!\nPrompt: *$prompt*", isUser = false, hasImageResult = true, imageResultUrl = url))
                    _generatedImageUrl.value = url
                } else {
                    val prompt = buildPrompt(text, category, hasImage, hasAudio)
                    val response = withContext(Dispatchers.IO) { aiRepo.generateResponse(prompt, key, _apiProvider.value) }
                    if (response.startsWith("ERROR")) {
                        addMsg(ChatMessage(text = "❌ $response", isUser = false))
                    } else {
                        val isCode = (category == "Coding" || category == "Web Dev") && !hasImage && !hasAudio
                        val clean = response.replace("```html","").replace("```css","").replace("```javascript","").replace("```js","").replace("```","").trim()
                        addMsg(ChatMessage(
                            text = if (isCode) "✅ Kode selesai:" else response,
                            isUser = false,
                            hasCode = isCode,
                            codeSnippet = if (isCode) clean else null
                        ))
                    }
                }
            } catch (e: Exception) {
                addMsg(ChatMessage(text = "❌ ${e.message?.take(80)}", isUser = false))
            }
            _isAiTyping.value = false
            try {
                chatRepo.saveHistory(_chatMessages.value)
                if (FirebaseAuth.getInstance().currentUser != null)
                    withContext(Dispatchers.IO) { cloudRepo.saveHistoryToCloud(_chatMessages.value) }
            } catch (_: Exception) {}
        }
    }

    private fun buildPrompt(text: String, cat: String, hasImage: Boolean, hasAudio: Boolean) = when {
        hasImage -> "Kamu music & design expert. Analisis gambar: $text"
        hasAudio -> "Kamu music producer. Analisis audio: $text. Cover: genre, BPM, key, mood, instrumen."
        cat == "Analisis Musik" -> "Analisis lagu: \"$text\"\nCover: genre, BPM, key, mood, instrumen, struktur, referensi artis, cara buat di Suno."
        cat == "Lirik" -> "Tulis lirik Bahasa Indonesia tentang: \"$text\"\nFormat: [Verse][Pre-Chorus][Chorus][Bridge][Outro]"
        cat == "Suno Prompt" -> "Buat Suno AI prompt untuk: \"$text\"\nFormat:\n**Style:** [genre, mood, tempo]\n**Prompt:** [deskripsi detail]\n**Negative:** [hindari apa]\n**BPM:** [estimasi]"
        cat == "Coding" || cat == "Web Dev" -> "Buat kode HTML+CSS+JS lengkap: \"$text\"\nHanya kode. Tanpa penjelasan. Tanpa backtick."
        else -> "Jawab sebagai AI assistant musik & teknologi:\n$text"
    }

    private fun addMsg(msg: ChatMessage) { _chatMessages.value = _chatMessages.value + msg }
    fun clearChat() { _chatMessages.value = emptyList(); chatRepo.saveHistory(emptyList()) }

    // ─────────────────────────────────────────────────────────
    // SLEEP TIMER
    // ─────────────────────────────────────────────────────────
    fun setSleepTimer(minutes: Int, stopAfterSong: Boolean = false, fadeOut: Boolean = false) {
        val p = MusicPlayerManager.player ?: return
        if (sleepTimer == null) sleepTimer = SleepTimer(viewModelScope, p) { vol -> MusicPlayerManager.player?.volume = vol }
        sleepTimer!!.start(minutes, stopAfterSong, fadeOut)
        _sleepTimerActive.value = true
    }

    fun cancelSleepTimer() { sleepTimer?.clear(); _sleepTimerActive.value = false; _sleepTimerRemaining.value = -1L }

    // ─────────────────────────────────────────────────────────
    // DOWNLOAD
    // ─────────────────────────────────────────────────────────
    fun downloadTrack(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val audioUrl = when {
                    track.ytVideoId.isNotBlank() -> ytRepo.getStreamUrl(track.ytVideoId)
                    track.saavnId.isNotBlank() -> saavnApi.getSongById(track.saavnId)?.downloadUrl?.lastOrNull()?.url ?: ""
                    else -> track.previewUrl
                }
                downloadMgr.downloadSong(track.toSongEntity().copy(audioUrl = audioUrl))
            } catch (_: Exception) {}
        }
    }

    fun cancelDownload(id: String) = downloadMgr.cancelDownload(id)
    fun deleteDownload(id: String) = downloadMgr.deleteDownload(id)
    fun getDownloadedPath(id: String) = downloadMgr.getDownloadedFilePath(id)

    // ─────────────────────────────────────────────────────────
    // EQ
    // ─────────────────────────────────────────────────────────
    fun applyEqProfile(profile: SavedEQProfile) { eqProfileRepo.setActiveProfile(profile); EqualizerManager.applyProfile(profile) }
    fun disableEq() { eqProfileRepo.setActiveProfile(null); EqualizerManager.disable() }
    fun saveCustomEqProfile(p: SavedEQProfile) = eqProfileRepo.saveProfile(p)
    fun deleteEqProfile(id: String) = eqProfileRepo.deleteProfile(id)

    // ─────────────────────────────────────────────────────────
    // ALARMS
    // ─────────────────────────────────────────────────────────
    private fun loadAlarms() { _alarms.value = MusicAlarmStore.load(getApplication()) }
    fun addAlarm(e: MusicAlarmEntry) { val u = _alarms.value + e; _alarms.value = u; MusicAlarmScheduler.scheduleAll(getApplication(), u) }
    fun updateAlarm(e: MusicAlarmEntry) { val u = _alarms.value.map { if (it.id == e.id) e else it }; _alarms.value = u; MusicAlarmScheduler.scheduleAll(getApplication(), u) }
    fun deleteAlarm(id: String) { MusicAlarmScheduler.cancel(getApplication(), id); val u = _alarms.value.filter { it.id != id }; _alarms.value = u; MusicAlarmStore.save(getApplication(), u) }
    fun toggleAlarm(id: String) { _alarms.value.find { it.id == id }?.let { updateAlarm(it.copy(enabled = !it.enabled)) } }

    // ─────────────────────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        try { EqualizerManager.release() } catch (_: Exception) {}
    }
}

// Extension helpers
private fun Track.toSongEntity() = com.agon.app.db.entities.SongEntity(
    id = ytVideoId.ifBlank { saavnId.ifBlank { trackId.toString() } },
    title = trackName, artist = artistName, album = collectionName,
    duration = (trackTimeMillis / 1000).toInt(),
    thumbnailUrl = artworkUrl100, audioUrl = previewUrl,
    lastPlayedTime = System.currentTimeMillis()
)
