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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ==================== ENUMS & CONSTANTS ====================

enum class RepeatMode(val value: Int) {
    OFF(0), ALL(1), ONE(2);

    companion object {
        fun fromValue(value: Int): RepeatMode = entries.find { it.value == value } ?: OFF
    }
}

enum class MediaType(val label: String) {
    MUSIC("Music"), PODCAST("Podcast"), VIDEO("Video")
}

// ==================== VIEWMODEL ====================

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    // ── Repositories ──────────────────────────────────────────
    private val musicRepo    = MusicRepository()
    private val saavnApi     = SaavnApiManager()
    private val ytMusicRepo  = YouTubeMusicRepository()
    private val chatRepo     = ChatRepository(application)
    private val cloudRepo    = CloudRepository()
    private val settingsRepo = SettingsRepository(application)
    private val aiRepo       = AiRepository()
    private val imageRepo    = ImageRepository()
    private val db           = AppDatabase.getInstance(application)
    private val downloadMgr  = DownloadManager(application)
    val eqProfileRepo        = EQProfileRepository(application)
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = false
    }

    // ── API Config ────────────────────────────────────────────
    private val _apiKey = MutableStateFlow(settingsRepo.getApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _apiProvider = MutableStateFlow(settingsRepo.getApiProvider())
    val apiProvider: StateFlow<String> = _apiProvider.asStateFlow()

    private val _apiValidationResult = MutableStateFlow<String?>(null)
    val apiValidationResult: StateFlow<String?> = _apiValidationResult.asStateFlow()

    // ── Theme ─────────────────────────────────────────────────
    private val _themeMode = MutableStateFlow(settingsRepo.getThemeMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // ── Chat ──────────────────────────────────────────────────
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiTyping = MutableStateFlow(false)
    val isAiTyping: StateFlow<Boolean> = _isAiTyping.asStateFlow()

    // ── Discover ──────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults: StateFlow<List<Track>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _topCharts = MutableStateFlow<List<Track>>(emptyList())
    val topCharts: StateFlow<List<Track>> = _topCharts.asStateFlow()

    private val _relatedTracks = MutableStateFlow<List<Track>>(emptyList())
    val relatedTracks: StateFlow<List<Track>> = _relatedTracks.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _mediaType = MutableStateFlow(MediaType.MUSIC.label)
    val mediaType: StateFlow<String> = _mediaType.asStateFlow()

    fun setMediaType(type: String) { 
        _mediaType.value = type 
    }

    // ── Player ────────────────────────────────────────────────
    private val _selectedTrack = MutableStateFlow<Track?>(null)
    val selectedTrack: StateFlow<Track?> = _selectedTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private val _isShuffled = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF.value)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    // ── Lyrics ────────────────────────────────────────────────
    private val _lyrics = MutableStateFlow<String?>(null)
    val lyrics: StateFlow<String?> = _lyrics.asStateFlow()

    private val _lyricsEntries = MutableStateFlow<List<LyricsEntry>>(emptyList())
    val lyricsEntries: StateFlow<List<LyricsEntry>> = _lyricsEntries.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()

    private val _currentLyricsIndex = MutableStateFlow(-1)
    val currentLyricsIndex: StateFlow<Int> = _currentLyricsIndex.asStateFlow()

    private val _lyricsIsSynced = MutableStateFlow(false)
    val lyricsIsSynced: StateFlow<Boolean> = _lyricsIsSynced.asStateFlow()

    // ── Library ───────────────────────────────────────────────
    private val _favorites = MutableStateFlow<List<FavoriteTrack>>(emptyList())
    val favorites: StateFlow<List<FavoriteTrack>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    val recentlyPlayed: Flow<List<SongEntity>> = db.musicDao().getRecentlyPlayed(50)
    val likedSongs: Flow<List<SongEntity>> = db.musicDao().getLikedSongs()
    val downloadedSongs: Flow<List<SongEntity>> = db.musicDao().getDownloadedSongs()

    // ── Download ──────────────────────────────────────────────
    val downloadProgress: StateFlow<Map<String, Float>> = downloadMgr.downloadProgress

    // ── Sleep Timer ───────────────────────────────────────────
    private var sleepTimer: SleepTimer? = null

    private val _sleepTimerActive = MutableStateFlow(false)
    val sleepTimerActive: StateFlow<Boolean> = _sleepTimerActive.asStateFlow()

    private val _sleepTimerRemaining = MutableStateFlow(-1L)
    val sleepTimerRemaining: StateFlow<Long> = _sleepTimerRemaining.asStateFlow()

    // ── Alarms ────────────────────────────────────────────────
    private val _alarms = MutableStateFlow<List<MusicAlarmEntry>>(emptyList())
    val alarms: StateFlow<List<MusicAlarmEntry>> = _alarms.asStateFlow()

    // ── EQ ────────────────────────────────────────────────────
    val activeEqProfile: StateFlow<SavedEQProfile?> = eqProfileRepo.activeProfile

    // ── Image ─────────────────────────────────────────────────
    private val _generatedImageUrl = MutableStateFlow<String?>(null)
    val generatedImageUrl: StateFlow<String?> = _generatedImageUrl.asStateFlow()

    // ── Analysis ──────────────────────────────────────────────
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisResult = MutableStateFlow<String?>(null)
    val analysisResult: StateFlow<String?> = _analysisResult.asStateFlow()

    // ── Init ──────────────────────────────────────────────────
    init {
        loadChatHistory()
        loadLibrary()
        loadTopCharts()
        loadSearchHistory()
        loadAlarms()
    }

    // ==================== PLAYER INITIALIZATION ====================

    fun initPlayer(context: Context) {
        MusicPlayerManager.init(context)
        MusicPlayerManager.onTrackChanged = { nextTrack() }

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
                    updatePlaybackState(player)
                }
                delay(500)
            }
        }
    }

    private fun updatePlaybackState(player: Media3Player) {
        _isPlaying.value = player.isPlaying
        _currentPosition.value = player.currentPosition.toInt().coerceAtLeast(0)
        if (player.duration > 0) {
            _duration.value = player.duration.toInt()
        }
        _isBuffering.value = player.playbackState == Media3Player.STATE_BUFFERING
        
        // Sync lyrics index
        if (_lyricsEntries.value.isNotEmpty()) {
            _currentLyricsIndex.value = findCurrentLyricsIndex(
                _lyricsEntries.value, 
                _currentPosition.value.toLong()
            )
        }
        
        // Sleep timer countdown
        sleepTimer?.let { st ->
            _sleepTimerActive.value = st.isActive
            _sleepTimerRemaining.value = st.remainingMs()
        }
    }

    // ==================== SETTINGS ====================

    fun saveApiConfig(key: String, provider: String) {
        settingsRepo.saveApiKey(key)
        settingsRepo.saveApiProvider(provider)
        _apiKey.value = key
        _apiProvider.value = provider
    }

    fun setThemeMode(mode: String) { 
        settingsRepo.saveThemeMode(mode)
        _themeMode.value = mode 
    }

    fun validateApiKey() {
        viewModelScope.launch(Dispatchers.IO) {
            val key = _apiKey.value.trim()
            if (key.isBlank()) { 
                _apiValidationResult.value = "❌ API Key kosong!"
                return@launch 
            }
            
            _apiValidationResult.value = "⏳ Memvalidasi ${_apiProvider.value}..."
            
            try {
                val result = aiRepo.generateResponse(
                    "Reply with exactly: VALID", 
                    key, 
                    _apiProvider.value
                )
                _apiValidationResult.value = parseApiValidationResult(result)
            } catch (e: Exception) {
                Timber.e(e, "API validation failed")
                _apiValidationResult.value = "❌ Exception: ${e.message?.take(60)}"
            }
        }
    }

    private fun parseApiValidationResult(result: String): String = when {
        result.startsWith("ERROR_CONNECTION") -> "❌ Gagal konek. Cek internet."
        result.startsWith("ERROR_401") -> "❌ API Key salah / expired."
        result.startsWith("ERROR_403") -> "❌ Akses ditolak. Cek plan API."
        result.startsWith("ERROR_429") -> "⚠️ Rate limit. Coba lagi sebentar."
        result.startsWith("ERROR") -> "❌ Error: ${result.take(80)}"
        else -> "✅ Valid! ${_apiProvider.value} siap digunakan."
    }

    // ==================== CHAT ====================

    private fun loadChatHistory() {
        viewModelScope.launch {
            val loggedIn = FirebaseAuth.getInstance().currentUser != null
            
            if (loggedIn) {
                tryLoadCloudHistory()
            } else {
                loadLocalHistory()
            }
        }
    }

    private suspend fun tryLoadCloudHistory() {
        try {
            val cloud = cloudRepo.loadHistoryFromCloud()
            if (cloud.isNotEmpty()) {
                _chatMessages.value = cloud
                chatRepo.saveHistory(cloud)
                return
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to load cloud history")
        }
        loadLocalHistory()
    }

    private suspend fun loadLocalHistory() {
        val local = chatRepo.loadHistory()
        _chatMessages.value = if (local.isEmpty()) {
            createWelcomeMessage()
        } else {
            local
        }
    }

    private fun createWelcomeMessage(): List<ChatMessage> {
        val welcome = listOf(
            ChatMessage(
                text = "Yo! Gue **LyronixAi** 🎵\n\nGue bisa bantu:\n• 🎵 Analisis musik & genre\n• 💻 Kode HTML/CSS/Web\n• 📝 Lirik & Suno AI prompt\n• 🖼️ Generate gambar GRATIS!\n• 🤖 Chat AI multi-provider\n\n⚙️ Set API Key di Settings!",
                isUser = false
            )
        )
        chatRepo.saveHistory(welcome)
        return welcome
    }

    fun sendMessage(
        text: String, 
        category: String, 
        hasImage: Boolean = false, 
        hasAudio: Boolean = false
    ) {
        if (text.isBlank() && !hasImage && !hasAudio) return
        
        val display = when {
            hasImage -> "📷 $text"
            hasAudio -> "🎵 $text"
            else -> text
        }
        
        addMessage(ChatMessage(text = display, isUser = true, hasImage = hasImage, hasAudio = hasAudio))
        
        viewModelScope.launch {
            _isAiTyping.value = true
            
            val key = _apiKey.value.trim()
            if (key.isBlank()) {
                showApiKeyMissingError()
                return@launch
            }

            if (category == "Image Prompt") {
                generateImage(text)
                return@launch
            }

            try {
                val prompt = buildPrompt(text, category, hasImage, hasAudio)
                val response = aiRepo.generateResponse(prompt, key, _apiProvider.value)
                handleAiResponse(response, category, hasImage, hasAudio)
            } catch (e: Exception) {
                Timber.e(e, "AI response failed")
                addMessage(ChatMessage(text = "❌ Error: ${e.message}", isUser = false))
            }
            
            _isAiTyping.value = false
            saveToCloud()
        }
    }

    private fun showApiKeyMissingError() {
        addMessage(
            ChatMessage(
                text = "⚠️ **API Key belum diisi!**\n\nBuka Settings → masukkan key AI.\n\nGratis:\n• Gemini: aistudio.google.com\n• DeepSeek: platform.deepseek.com",
                isUser = false
            )
        )
        _isAiTyping.value = false
    }

    private suspend fun generateImage(text: String) {
        val prompt = imageRepo.generateImagePromptFromText(text, "music")
        val url = imageRepo.generateImageUrl(prompt)
        
        addMessage(
            ChatMessage(
                text = "🎨 Gambar generated!\nPrompt: *$prompt*",
                isUser = false,
                hasImageResult = true,
                imageResultUrl = url
            )
        )
        
        _generatedImageUrl.value = url
        _isAiTyping.value = false
        saveToCloud()
    }

    private fun handleAiResponse(response: String, category: String, hasImage: Boolean, hasAudio: Boolean) {
        if (response.startsWith("ERROR_")) {
            addMessage(ChatMessage(text = "❌ $response", isUser = false))
            return
        }

        val isCode = (category == "Coding" || category == "Web Dev") && !hasImage && !hasAudio
        val clean = response
            .replace("```html", "")
            .replace("```css", "")
            .replace("```javascript", "")
            .replace("```js", "")
            .replace("```", "")
            .trim()

        addMessage(
            ChatMessage(
                text = if (isCode) "✅ Kode berhasil dibuat:" else response,
                isUser = false,
                hasCode = isCode,
                codeSnippet = if (isCode) clean else null
            )
        )
    }

    private fun buildPrompt(
        text: String, 
        category: String, 
        hasImage: Boolean, 
        hasAudio: Boolean
    ): String = when {
        hasImage -> "Kamu music & design expert. User upload gambar: $text. Analisa, jika UI berikan kode HTML+CSS."
        hasAudio -> "Kamu music producer expert. Audio: $text. Analisa: genre, BPM, key, mood, instrumen, mixing."
        category == "Analisis Musik" -> "Kamu music critic profesional. Analisis mendalam:\n\"$text\"\nCover: genre, sub-genre, BPM, key, mood, instrumen, struktur, reference artist, cara buat di Suno AI."
        category == "Lirik" -> "Kamu penulis lirik pro. Buat lirik Bahasa Indonesia tentang:\n\"$text\"\nFormat: [Intro][Verse 1][Pre-Chorus][Chorus][Verse 2][Bridge][Outro]."
        category == "Suno Prompt" -> "Kamu Suno AI expert. Buat prompt optimal:\n\"$text\"\nFormat:\n**Style Tags:** [genre, mood, tempo]\n**Prompt:** [deskripsi detail]\n**Negative:** [tidak diinginkan]\n**BPM & Key:** [estimasi]"
        category == "Coding" || category == "Web Dev" -> "Senior web dev. Buat kode lengkap:\n\"$text\"\nHanya kode HTML+CSS+JS. Tanpa penjelasan, tanpa backtick."
        else -> "AI assistant ahli musik & teknologi. Jawab:\n$text"
    }

    private fun addMessage(msg: ChatMessage) { 
        _chatMessages.value = _chatMessages.value + msg
        chatRepo.saveHistory(_chatMessages.value) 
    }

    private suspend fun saveToCloud() { 
        if (FirebaseAuth.getInstance().currentUser != null) {
            try { 
                cloudRepo.saveHistoryToCloud(_chatMessages.value) 
            } catch (e: Exception) {
                Timber.w(e, "Failed to save to cloud")
            }
        } 
    }

    fun clearChat() { 
        _chatMessages.value = emptyList()
        chatRepo.saveHistory(emptyList()) 
    }

    // ==================== DISCOVER / SEARCH ====================

    fun updateSearchQuery(q: String) {
        _searchQuery.value = q
        if (q.length >= 2) {
            viewModelScope.launch {
                try {
                    _searchSuggestions.value = ytMusicRepo.getSearchSuggestions(q)
                } catch (e: Exception) {
                    Timber.w(e, "Search suggestions failed")
                }
            }
        }
    }

    fun search() {
        val q = _searchQuery.value.trim()
        if (q.isBlank()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                db.musicDao().addSearchHistory(SearchHistory(q))
                updateSearchHistoryList(q)
                _searchResults.value = performSearch(q)
            } catch (e: Exception) {
                Timber.e(e, "Search failed")
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun updateSearchHistoryList(q: String) {
        _searchHistory.value = _searchHistory.value
            .toMutableList()
            .apply { remove(q); add(0, q) }
            .take(20)
    }

    private suspend fun performSearch(q: String): List<Track> {
        return try {
            val yt = ytMusicRepo.search(q)
            if (yt.isNotEmpty()) {
                yt.map { it.toTrack() }
            } else {
                searchFallback(q)
            }
        } catch (e: Exception) {
            Timber.w(e, "YouTube search failed, trying fallback")
            searchFallback(q)
        }
    }

    private suspend fun searchFallback(q: String): List<Track> {
        return try {
            val saavn = saavnApi.searchSongs(q)
            if (saavn.isNotEmpty()) {
                saavn.map { it.toSaavnTrack() }
            } else {
                musicRepo.searchSongs(q)
            }
        } catch (e: Exception) {
            try { 
                musicRepo.searchSongs(q) 
            } catch (_: Exception) { 
                emptyList() 
            }
        }
    }

    fun loadGenre(genre: String) {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                val yt = ytMusicRepo.search("$genre music hits")
                _searchResults.value = if (yt.isNotEmpty()) {
                    yt.map { it.toTrack() }
                } else {
                    musicRepo.getTopCharts("ID", genre)
                }
            } catch (e: Exception) {
                Timber.e(e, "Genre load failed")
                _searchResults.value = emptyList()
            } finally {
                _isBuffering.value = false
            }
        }
    }

    private fun loadTopCharts() {
        viewModelScope.launch {
            try {
                val yt = ytMusicRepo.getTopCharts()
                _topCharts.value = if (yt.isNotEmpty()) {
                    yt.map { it.toTrack() }
                } else {
                    loadTopChartsFallback()
                }
            } catch (e: Exception) {
                Timber.e(e, "Top charts load failed")
                _topCharts.value = try { 
                    musicRepo.getTopCharts() 
                } catch (_: Exception) { 
                    emptyList() 
                }
            }
        }
    }

    private suspend fun loadTopChartsFallback(): List<Track> {
        val saavn = saavnApi.getTopCharts()
        return if (saavn.isNotEmpty()) {
            saavn.map { it.toSaavnTrack() }
        } else {
            musicRepo.getTopCharts()
        }
    }

    private fun loadSearchHistory() {
        viewModelScope.launch {
            db.musicDao().getSearchHistory().collect { list ->
                _searchHistory.value = list.map { it.query }
            }
        }
    }

    fun clearSearchHistory() { 
        viewModelScope.launch { 
            db.musicDao().clearSearchHistory()
            _searchHistory.value = emptyList() 
        } 
    }

    fun deleteSearchHistoryItem(q: String) { 
        viewModelScope.launch { 
            db.musicDao().deleteSearchHistory(q) 
        } 
    }

    // ==================== TRACK SELECTION & PLAYBACK ====================

    fun selectTrack(track: Track, playlistTracks: List<Track> = emptyList()) {
        try {
            resetPlayerState(track)
            updateQueue(track, playlistTracks)
            saveToRecentlyPlayed(track)
            fetchLyricsAndPlay(track)
            fetchRelated(track)
        } catch (e: Exception) {
            Timber.e(e, "selectTrack failed")
            _isBuffering.value = false
        }
    }

    private fun resetPlayerState(track: Track) {
        _selectedTrack.value = track
        _lyrics.value = null
        _lyricsEntries.value = emptyList()
        _currentLyricsIndex.value = -1
        _isBuffering.value = true
        stopAudio()
    }

    private fun updateQueue(track: Track, playlistTracks: List<Track>) {
        when {
            playlistTracks.isNotEmpty() -> _queue.value = playlistTracks
            _searchResults.value.any { it.trackId == track.trackId } -> _queue.value = _searchResults.value
            _topCharts.value.any { it.trackId == track.trackId } -> _queue.value = _topCharts.value
        }
    }

    private fun saveToRecentlyPlayed(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = track.toSongEntity()
                db.musicDao().upsertSong(entity)
                db.musicDao().incrementPlayTime(entity.id, 0L)
            } catch (e: Exception) {
                Timber.w(e, "Failed to save recently played")
            }
        }
    }

    // ==================== FETCH LYRICS & PLAY (REFACTORED) ====================

    private fun fetchLyricsAndPlay(track: Track) {
        // Set metadata
        MusicPlayerManager.MUSIC_ID = track.getTrackId()
        MusicPlayerManager.MUSIC_TITLE = track.trackName
        MusicPlayerManager.MUSIC_DESCRIPTION = track.artistName
        MusicPlayerManager.IMAGE_URL = track.artworkUrl100.replace("100x100", "500x500")

        // Set queue
        val queueIds = _queue.value.map { it.getTrackId() }
        if (queueIds.isNotEmpty()) {
            MusicPlayerManager.trackQueue = queueIds.toMutableList()
            MusicPlayerManager.track_position = _queue.value.indexOfFirst { it.trackId == track.trackId }
        }

        // ================== PRIORITY 1: AUDIO (Non-blocking) ==================
        viewModelScope.launch(Dispatchers.IO) {
            var audioUrl = ""
            
            try {
                audioUrl = resolveAudioUrl(track)
            } catch (e: Exception) {
                Timber.e(e, "Fetch full audio failed")
                audioUrl = track.previewUrl
            }

            withContext(Dispatchers.Main) {
                MusicPlayerManager.SONG_URL = audioUrl
                MusicPlayerManager.prepareMediaPlayer()
                Timber.d("▶️ Full Audio Playing: $audioUrl")
                _isBuffering.value = false
            }
        }

        // ================== PRIORITY 2: LYRICS (Async) ==================
        viewModelScope.launch {
            fetchLyrics(track)
        }

        // ================== PRIORITY 3: RELATED (Async) ==================
        fetchRelated(track)
    }

    private suspend fun resolveAudioUrl(track: Track): String {
        // 1. YouTube Music Full Stream
        if (track.ytVideoId.isNotBlank()) {
            try {
                val url = ytMusicRepo.getStreamUrl(track.ytVideoId)
                if (url.isNotBlank()) return url
            } catch (e: Exception) {
                Timber.w(e, "YouTube stream failed")
            }
        }

        // 2. Saavn Full Audio
        if (track.saavnId.isNotBlank()) {
            try {
                val song = saavnApi.getSongById(track.saavnId)
                val url = song?.downloadUrl?.lastOrNull()?.url ?: ""
                val secureUrl = if (url.startsWith("http:")) url.replace("http:", "https:") else url
                if (secureUrl.isNotBlank()) return secureUrl
            } catch (e: Exception) {
                Timber.w(e, "Saavn stream failed")
            }
        }

        // 3. Fallback
        return track.fullAudioUrl.ifBlank { track.previewUrl }
    }

    private suspend fun fetchLyrics(track: Track) {
        _isLyricsLoading.value = true
        
        try {
            val songId = track.getTrackId()
            val cached = db.musicDao().getLyrics(songId)
            
            if (cached != null && cached.lyrics.isNotBlank()) {
                applyLyrics(cached.lyrics, cached.isSynced)
                return
            }

            val result = LyricsRegistry.getLyrics(track.trackName, track.artistName)
            if (result.raw != null) {
                db.musicDao().upsertLyrics(
                    LyricsEntity(songId, result.raw, result.isSynced, result.source ?: "")
                )
                applyLyrics(result.raw, result.isSynced)
                return
            }

            // Fallback: Saavn/iTunes
            val lyricsText = fetchLyricsFromFallback(track)
            if (lyricsText != null) {
                val isSynced = lyricsLooksSynced(lyricsText)
                db.musicDao().upsertLyrics(
                    LyricsEntity(songId, lyricsText, isSynced, "saavn")
                )
                applyLyrics(lyricsText, isSynced)
            } else {
                _lyrics.value = "Lyrics not found."
            }
        } catch (e: Exception) {
            Timber.e(e, "Lyrics fetch failed")
            _lyrics.value = "Lyrics not found."
        } finally {
            _isLyricsLoading.value = false
        }
    }

    private suspend fun fetchLyricsFromFallback(track: Track): String? {
        return try {
            when {
                track.saavnId.isNotBlank() -> saavnApi.getLyrics(track.saavnId)
                else -> musicRepo.getLyrics(track.artistName, track.trackName)
            }
        } catch (e: Exception) {
            Timber.w(e, "Lyrics fallback failed")
            null
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
                    track.ytVideoId.isNotBlank() -> fetchYouTubeRelated(track)
                    track.saavnId.isNotBlank() -> saavnApi.getSuggestions(track.saavnId).map { it.toSaavnTrack() }
                    else -> musicRepo.getRelatedTracks(track.artistName)
                }
            } catch (e: Exception) {
                Timber.w(e, "Related tracks fetch failed")
            }
        }
    }

    private suspend fun fetchYouTubeRelated(track: Track): List<Track> {
        val yt = ytMusicRepo.getRelatedTracks(track.ytVideoId)
        return if (yt.isNotEmpty()) {
            yt.map { it.toTrack() }
        } else {
            musicRepo.getRelatedTracks(track.artistName)
        }
    }

    // ==================== LIBRARY ====================

    private fun loadLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _favorites.value = json.decodeFromString<List<FavoriteTrack>>(settingsRepo.getFavorites())
                _playlists.value = json.decodeFromString<List<Playlist>>(settingsRepo.getPlaylists())
            } catch (e: Exception) {
                Timber.w(e, "Library load failed, using defaults")
                _favorites.value = emptyList()
                _playlists.value = listOf(
                    Playlist(name = "Liked Songs"), 
                    Playlist(name = "My Playlist #1")
                )
            }
        }
    }

    fun toggleFavorite(track: Track) {
        val cur = _favorites.value.toMutableList()
        val exists = cur.find { it.trackId == track.trackId }
        
        if (exists != null) {
            cur.remove(exists)
        } else {
            cur.add(0, track.toFavoriteTrack())
        }
        
        _favorites.value = cur
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                settingsRepo.saveFavorites(json.encodeToString(cur))
            } catch (e: Exception) {
                Timber.e(e, "Failed to save favorites")
            }
            
            val songId = track.getTrackId()
            db.musicDao().setLiked(songId, exists == null)
        }
    }

    fun isFavorite(trackId: Long) = _favorites.value.any { it.trackId == trackId }

    fun createPlaylist(name: String) {
        val cur = _playlists.value + Playlist(name = name)
        _playlists.value = cur
        savePlaylists(cur)
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        val cur = _playlists.value.map { pl ->
            if (pl.id == playlistId && pl.tracks.none { it.trackId == track.trackId }) {
                pl.copy(
                    tracks = pl.tracks + track,
                    coverUrl = pl.coverUrl.ifBlank { track.artworkUrl100 }
                )
            } else {
                pl
            }
        }
        _playlists.value = cur
        savePlaylists(cur)
    }

    fun deletePlaylist(playlistId: String) {
        val cur = _playlists.value.filter { it.id != playlistId }
        _playlists.value = cur
        savePlaylists(cur)
    }

    private fun savePlaylists(playlists: List<Playlist>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                settingsRepo.savePlaylists(json.encodeToString(playlists))
            } catch (e: Exception) {
                Timber.e(e, "Failed to save playlists")
            }
        }
    }

    // ==================== PLAYER CONTROLS ====================

    fun togglePlayPause() { 
        MusicPlayerManager.togglePlayPause() 
    }

    fun stopAudio() {
        try { 
            MusicPlayerManager.player?.stop() 
        } catch (e: Exception) {
            Timber.w(e, "Stop audio failed")
        }
        _isPlaying.value = false
        _currentPosition.value = 0
        _isBuffering.value = false
    }

    fun seekTo(ms: Int) { 
        MusicPlayerManager.player?.seekTo(ms.toLong())
        _currentPosition.value = ms 
    }

    fun skipForward() = seekTo((_currentPosition.value + SKIP_INTERVAL_MS).coerceAtMost(_duration.value))
    
    fun skipBackward() = seekTo((_currentPosition.value - SKIP_INTERVAL_MS).coerceAtLeast(0))

    fun toggleShuffle() { 
        _isShuffled.value = !_isShuffled.value 
    }

    fun toggleRepeat() { 
        _repeatMode.value = (_repeatMode.value + 1) % 3 
    }

    fun nextTrack() {
        val list = if (_isShuffled.value) _queue.value.shuffled() else _queue.value
        if (list.isEmpty()) return
        
        val cur = _selectedTrack.value ?: return
        val idx = list.indexOfFirst { it.trackId == cur.trackId }
        
        when {
            idx < list.size - 1 -> selectTrack(list[idx + 1], list)
            _repeatMode.value == RepeatMode.ALL.value -> selectTrack(list[0], list)
            _repeatMode.value == RepeatMode.ONE.value -> fetchLyricsAndPlay(cur)
        }
        
        sleepTimer?.notifySongTransition()
    }

    fun previousTrack() {
        if (_currentPosition.value > PREVIOUS_THRESHOLD_MS) { 
            seekTo(0)
            return 
        }
        
        val list = _queue.value
        val cur = _selectedTrack.value ?: return
        val idx = list.indexOfFirst { it.trackId == cur.trackId }
        
        if (idx > 0) {
            selectTrack(list[idx - 1], list)
        }
    }

    // ==================== SLEEP TIMER ====================

    fun setSleepTimer(
        minutes: Int, 
        stopAfterCurrentSong: Boolean = false, 
        fadeOut: Boolean = false
    ) {
        val player = MusicPlayerManager.player ?: return
        
        if (sleepTimer == null) {
            sleepTimer = SleepTimer(viewModelScope, player) { 
                MusicPlayerManager.player?.volume = it 
            }
        }
        
        sleepTimer!!.start(minutes, stopAfterCurrentSong, fadeOut)
        _sleepTimerActive.value = true
    }

    fun cancelSleepTimer() { 
        sleepTimer?.clear()
        _sleepTimerActive.value = false
        _sleepTimerRemaining.value = -1L 
    }

    // ==================== DOWNLOAD ====================

    fun downloadTrack(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            val audioUrl = resolveDownloadUrl(track)
            downloadMgr.downloadSong(track.toSongEntity().copy(audioUrl = audioUrl))
        }
    }

    private suspend fun resolveDownloadUrl(track: Track): String {
        return when {
            track.ytVideoId.isNotBlank() -> try { 
                ytMusicRepo.getStreamUrl(track.ytVideoId) 
            } catch (_: Exception) { "" }
            
            track.saavnId.isNotBlank() -> try { 
                saavnApi.getSongById(track.saavnId)?.downloadUrl?.lastOrNull()?.url ?: "" 
            } catch (_: Exception) { "" }
            
            else -> track.previewUrl
        }
    }

    fun cancelDownload(songId: String) = downloadMgr.cancelDownload(songId)
    
    fun deleteDownload(songId: String) = downloadMgr.deleteDownload(songId)
    
    fun getDownloadedPath(songId: String) = downloadMgr.getDownloadedFilePath(songId)

    // ==================== EQUALIZER ====================

    fun applyEqProfile(profile: SavedEQProfile) {
        eqProfileRepo.setActiveProfile(profile)
        EqualizerManager.applyProfile(profile)
    }

    fun disableEq() { 
        eqProfileRepo.setActiveProfile(null)
        EqualizerManager.disable() 
    }

    fun saveCustomEqProfile(profile: SavedEQProfile) = eqProfileRepo.saveProfile(profile)
    
    fun deleteEqProfile(id: String) = eqProfileRepo.deleteProfile(id)

    // ==================== ALARMS ====================

    private fun loadAlarms() { 
        _alarms.value = MusicAlarmStore.load(getApplication()) 
    }

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

    // ==================== ANALYSIS ====================

    fun simulateAnalysis() {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisResult.value = null
            delay(2000)
            _analysisResult.value = "Upload file audio untuk analisa real."
            _isAnalyzing.value = false
        }
    }

    // ==================== LIFECYCLE ====================

    override fun onCleared() { 
        super.onCleared()
        MusicPlayerManager.player?.stop()
        EqualizerManager.release()
        sleepTimer?.clear()
    }

    // ==================== COMPANION ====================

    companion object {
        private const val SKIP_INTERVAL_MS = 10_000
        private const val PREVIOUS_THRESHOLD_MS = 3_000
    }
}

// ==================== EXTENSION FUNCTIONS ====================

private fun Track.getTrackId(): String = 
    ytVideoId.ifBlank { saavnId.ifBlank { trackId.toString() } }

private fun Track.toSongEntity(): SongEntity = SongEntity(
    id = getTrackId(),
    title = trackName,
    artist = artistName,
    album = collectionName,
    duration = (trackTimeMillis / 1000).toInt(),
    thumbnailUrl = artworkUrl100,
    audioUrl = previewUrl,
    lastPlayedTime = System.currentTimeMillis()
)

private fun Track.toFavoriteTrack(): FavoriteTrack = FavoriteTrack(
    trackId = trackId,
    trackName = trackName,
    artistName = artistName,
    artworkUrl100 = artworkUrl100,
    previewUrl = previewUrl,
    primaryGenreName = primaryGenreName
)
