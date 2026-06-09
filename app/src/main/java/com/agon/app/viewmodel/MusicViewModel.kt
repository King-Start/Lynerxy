package com.agon.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.data.AiRepository
import com.agon.app.data.ChatMessage
import com.agon.app.data.ChatRepository
import com.agon.app.data.CloudRepository
import com.agon.app.data.FavoriteTrack
import com.agon.app.data.ImageRepository
import com.agon.app.data.MusicRepository
import com.agon.app.data.Playlist
import com.agon.app.data.SaavnApiManager
import com.agon.app.data.SettingsRepository
import com.agon.app.data.Track
import com.agon.app.data.YouTubeMusicRepository
import com.agon.app.data.toTrack
import com.agon.app.data.toSaavnTrack
import com.agon.app.utils.MusicPlayerManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val musicRepo = MusicRepository()
    private val saavnApi = SaavnApiManager()
    private val ytMusicRepo = YouTubeMusicRepository()
    private val chatRepo = ChatRepository(application)
    private val cloudRepo = CloudRepository()
    private val settingsRepo = SettingsRepository(application)
    private val aiRepo = AiRepository()
    private val imageRepo = ImageRepository()
    private val json = Json { ignoreUnknownKeys = true }

    // ── API Config ────────────────────────────────────────────
    private val _apiKey = MutableStateFlow(settingsRepo.getApiKey())
    val apiKey: StateFlow<String> = _apiKey
    private val _apiProvider = MutableStateFlow(settingsRepo.getApiProvider())
    val apiProvider: StateFlow<String> = _apiProvider
    private val _apiValidationResult = MutableStateFlow<String?>(null)
    val apiValidationResult: StateFlow<String?> = _apiValidationResult

    // ── Theme ─────────────────────────────────────────────────
    private val _themeMode = MutableStateFlow(settingsRepo.getThemeMode())
    val themeMode: StateFlow<String> = _themeMode

    // ── Chat ──────────────────────────────────────────────────
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages
    private val _isAiTyping = MutableStateFlow(false)
    val isAiTyping: StateFlow<Boolean> = _isAiTyping

    // ── Analysis ──────────────────────────────────────────────
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing
    private val _analysisResult = MutableStateFlow<String?>(null)
    val analysisResult: StateFlow<String?> = _analysisResult

    // ── Discover ──────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    private val _mediaType = MutableStateFlow("Music")
    val mediaType: StateFlow<String> = _mediaType
    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults: StateFlow<List<Track>> = _searchResults
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _selectedTrack = MutableStateFlow<Track?>(null)
    val selectedTrack: StateFlow<Track?> = _selectedTrack
    private val _lyrics = MutableStateFlow<String?>(null)
    val lyrics: StateFlow<String?> = _lyrics
    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading
    private val _topCharts = MutableStateFlow<List<Track>>(emptyList())
    val topCharts: StateFlow<List<Track>> = _topCharts
    private val _relatedTracks = MutableStateFlow<List<Track>>(emptyList())
    val relatedTracks: StateFlow<List<Track>> = _relatedTracks

    // ── Player ────────────────────────────────────────────────
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition
    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration
    private val _isShuffled = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled
    private val _repeatMode = MutableStateFlow(0)
    val repeatMode: StateFlow<Int> = _repeatMode
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue

    // ── Library ───────────────────────────────────────────────
    private val _favorites = MutableStateFlow<List<FavoriteTrack>>(emptyList())
    val favorites: StateFlow<List<FavoriteTrack>> = _favorites
    private val _recentlyPlayed = MutableStateFlow<List<Track>>(emptyList())
    val recentlyPlayed: StateFlow<List<Track>> = _recentlyPlayed
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists

    // ── Image ─────────────────────────────────────────────────
    private val _generatedImageUrl = MutableStateFlow<String?>(null)
    val generatedImageUrl: StateFlow<String?> = _generatedImageUrl

    // ── Init ──────────────────────────────────────────────────
    init {
        loadChatHistory()
        loadLibrary()
        loadTopCharts()
    }

    fun initPlayer(context: Context) {
        MusicPlayerManager.init(context)
        MusicPlayerManager.onTrackChanged = { nextTrack() }
        viewModelScope.launch {
            while (true) {
                val player = MusicPlayerManager.player
                if (player != null) {
                    _isPlaying.value = player.isPlaying
                    _currentPosition.value = try { player.currentPosition.toInt() } catch (e: Exception) { 0 }
                    if (player.duration > 0) _duration.value = player.duration.toInt()
                }
                delay(500)
            }
        }
    }

    // ── Chat History ──────────────────────────────────────────
    private fun loadChatHistory() {
        viewModelScope.launch {
            val isLoggedIn = FirebaseAuth.getInstance().currentUser != null
            if (isLoggedIn) {
                try {
                    val cloud = cloudRepo.loadHistoryFromCloud()
                    if (cloud.isNotEmpty()) {
                        _chatMessages.value = cloud
                        chatRepo.saveHistory(cloud)
                        return@launch
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            val local = chatRepo.loadHistory()
            _chatMessages.value = if (local.isEmpty()) {
                val welcome = listOf(ChatMessage(
                    text = "Yo! Gue **LyronixAi** 🎵\n\nGue bisa bantu:\n• 🎵 Analisis musik & genre\n• 💻 Kode HTML/CSS/Web\n• 📝 Lirik & Suno AI prompt\n• 🖼️ Generate gambar GRATIS!\n• 🤖 Chat AI (OpenAI/Claude/Gemini/DeepSeek)\n\n⚙️ Set API Key di menu ☰ Settings!",
                    isUser = false
                ))
                chatRepo.saveHistory(welcome)
                welcome
            } else local
        }
    }

    // ── Library ───────────────────────────────────────────────
    private fun loadLibrary() {
        try {
            _favorites.value = json.decodeFromString<List<FavoriteTrack>>(settingsRepo.getFavorites())
            _recentlyPlayed.value = json.decodeFromString<List<Track>>(settingsRepo.getRecentlyPlayed())
            _playlists.value = json.decodeFromString<List<Playlist>>(settingsRepo.getPlaylists())
        } catch (e: Exception) {
            _favorites.value = emptyList()
            _recentlyPlayed.value = emptyList()
            _playlists.value = listOf(Playlist(name = "Liked Songs"), Playlist(name = "My Playlist #1"))
        }
    }

    private fun loadTopCharts() {
        viewModelScope.launch {
            try {
                val ytCharts = ytMusicRepo.getTopCharts()
                _topCharts.value = if (ytCharts.isNotEmpty()) {
                    ytCharts.map { it.toTrack() }
                } else {
                    val saavn = saavnApi.getTopCharts()
                    if (saavn.isNotEmpty()) saavn.map { it.toSaavnTrack() }
                    else musicRepo.getTopCharts()
                }
            } catch (e: Exception) {
                try { _topCharts.value = musicRepo.getTopCharts() } catch (_: Exception) {}
            }
        }
    }

    // ── Settings ──────────────────────────────────────────────
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
        viewModelScope.launch {
            val key = _apiKey.value.trim()
            if (key.isBlank()) { _apiValidationResult.value = "❌ API Key kosong!"; return@launch }
            _apiValidationResult.value = "⏳ Memvalidasi..."
            val result = aiRepo.generateResponse("Say: OK", key, _apiProvider.value)
            _apiValidationResult.value = if (result.startsWith("ERROR"))
                "❌ Key tidak valid: $result"
            else "✅ API Key valid! Provider: ${_apiProvider.value}"
        }
    }

    // ── Chat ──────────────────────────────────────────────────
    fun sendMessage(text: String, category: String, hasImage: Boolean = false, hasAudio: Boolean = false) {
        if (text.isBlank() && !hasImage && !hasAudio) return
        val display = when {
            hasImage -> "📷 Upload gambar: $text"
            hasAudio -> "🎵 Upload audio: $text"
            else -> text
        }
        addMessage(ChatMessage(text = display, isUser = true, hasImage = hasImage, hasAudio = hasAudio))

        viewModelScope.launch {
            _isAiTyping.value = true
            delay(300)
            val key = _apiKey.value.trim()
            if (key.isBlank()) {
                addMessage(ChatMessage(
                    text = "⚠️ **API Key belum diisi!**\n\nBuka ☰ → Settings → masukkan key.\n\nGratis di:\n• Gemini: aistudio.google.com\n• DeepSeek: platform.deepseek.com\n• GLM: open.bigmodel.cn",
                    isUser = false
                ))
                _isAiTyping.value = false
                return@launch
            }
            if (category == "Image Prompt") {
                val prompt = imageRepo.generateImagePromptFromText(text, "music")
                val url = imageRepo.generateImageUrl(prompt)
                addMessage(ChatMessage(text = "🎨 Gambar generated!\nPrompt: *$prompt*", isUser = false, hasImageResult = true, imageResultUrl = url))
                _generatedImageUrl.value = url
                _isAiTyping.value = false
                saveToCloud()
                return@launch
            }
            try {
                val prompt = buildPrompt(text, category, hasImage, hasAudio)
                val response = aiRepo.generateResponse(prompt, key, _apiProvider.value)
                if (response.startsWith("ERROR_")) {
                    addMessage(ChatMessage(text = "❌ $response", isUser = false))
                } else {
                    val isCode = (category == "Coding" || category == "Web Dev") && !hasImage && !hasAudio
                    val clean = response.replace("```html", "").replace("```css", "")
                        .replace("```javascript", "").replace("```js", "").replace("```", "").trim()
                    addMessage(ChatMessage(
                        text = if (isCode) "✅ Kode berhasil dibuat:" else response,
                        isUser = false,
                        hasCode = isCode,
                        codeSnippet = if (isCode) clean else null
                    ))
                }
            } catch (e: Exception) {
                addMessage(ChatMessage(text = "❌ Error: ${e.message}", isUser = false))
            }
            _isAiTyping.value = false
            saveToCloud()
        }
    }

    private fun buildPrompt(text: String, category: String, hasImage: Boolean, hasAudio: Boolean): String = when {
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
            try { cloudRepo.saveHistoryToCloud(_chatMessages.value) } catch (_: Exception) {}
        }
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
        chatRepo.saveHistory(emptyList())
    }

    // ── Discover ──────────────────────────────────────────────
    fun updateSearchQuery(q: String) { _searchQuery.value = q }
    fun setMediaType(type: String) { _mediaType.value = type }

    fun search() {
        val q = _searchQuery.value.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _searchResults.value = try {
                val ytResults = ytMusicRepo.search(q)
                if (ytResults.isNotEmpty()) {
                    ytResults.map { it.toTrack() }
                } else {
                    val saavn = saavnApi.searchSongs(q)
                    if (saavn.isNotEmpty()) saavn.map { it.toSaavnTrack() }
                    else musicRepo.searchSongs(q)
                }
            } catch (e: Exception) {
                try { musicRepo.searchSongs(q) } catch (_: Exception) { emptyList() }
            }
            _isLoading.value = false
        }
    }

    fun loadGenre(genre: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _searchResults.value = try {
                val ytResults = ytMusicRepo.search("$genre music hits")
                if (ytResults.isNotEmpty()) ytResults.map { it.toTrack() }
                else musicRepo.getTopCharts("ID", genre)
            } catch (e: Exception) { emptyList() }
            _isLoading.value = false
        }
    }

    // ── Track Selection ───────────────────────────────────────
    fun selectTrack(track: Track, playlistTracks: List<Track> = emptyList()) {
        _selectedTrack.value = track
        _lyrics.value = null
        stopAudio()
        if (playlistTracks.isNotEmpty()) _queue.value = playlistTracks
        else if (_searchResults.value.contains(track)) _queue.value = _searchResults.value
        else if (_topCharts.value.contains(track)) _queue.value = _topCharts.value
        addToRecentlyPlayed(track)
        fetchLyricsAndPlay(track)
        fetchRelated(track)
    }

    private fun addToRecentlyPlayed(track: Track) {
        val cur = _recentlyPlayed.value.toMutableList()
        cur.removeIf { it.trackId == track.trackId }
        cur.add(0, track)
        if (cur.size > 30) cur.removeAt(cur.size - 1)
        _recentlyPlayed.value = cur
        try { settingsRepo.saveRecentlyPlayed(json.encodeToString<List<Track>>(cur)) } catch (_: Exception) {}
    }

    private fun fetchLyricsAndPlay(track: Track) {
        // Set info untuk notification
        MusicPlayerManager.MUSIC_ID = track.saavnId.ifBlank { track.ytVideoId.ifBlank { track.trackId.toString() } }
        MusicPlayerManager.MUSIC_TITLE = track.trackName
        MusicPlayerManager.MUSIC_DESCRIPTION = track.artistName
        MusicPlayerManager.IMAGE_URL = track.artworkUrl100.replace("100x100", "500x500")

        val queueIds = _queue.value.map {
            it.ytVideoId.ifBlank { it.saavnId.ifBlank { it.trackId.toString() } }
        }.toMutableList()
        if (queueIds.isNotEmpty()) {
            MusicPlayerManager.trackQueue = queueIds
            MusicPlayerManager.track_position = _queue.value.indexOfFirst { it.trackId == track.trackId }
        }

        // Fetch lyrics
        viewModelScope.launch {
            _isLyricsLoading.value = true
            _lyrics.value = try {
                when {
                    track.saavnId.isNotBlank() -> saavnApi.getLyrics(track.saavnId)
                    else -> musicRepo.getLyrics(track.artistName, track.trackName)
                }
            } catch (_: Exception) { null } ?: "Lyrics not found."
            _isLyricsLoading.value = false
        }

        // Fetch & play audio
        viewModelScope.launch {
            val audioUrl: String = when {
                track.ytVideoId.isNotBlank() -> {
                    try {
                        val stream = ytMusicRepo.getStreamUrl(track.ytVideoId)
                        if (stream.isNotBlank()) stream else track.previewUrl
                    } catch (_: Exception) { track.previewUrl }
                }
                track.fullAudioUrl.isNotBlank() && !track.fullAudioUrl.startsWith("ytmusic://") ->
                    track.fullAudioUrl
                track.saavnId.isNotBlank() -> {
                    try {
                        val song = saavnApi.getSongById(track.saavnId)
                        val url = song?.downloadUrl?.lastOrNull()?.url ?: ""
                        if (url.startsWith("http:")) url.replace("http:", "https:") else url.ifBlank { track.previewUrl }
                    } catch (_: Exception) { track.previewUrl }
                }
                else -> track.previewUrl
            }
            if (audioUrl.isNotBlank() && !audioUrl.startsWith("ytmusic://")) {
                MusicPlayerManager.SONG_URL = audioUrl
                MusicPlayerManager.prepareMediaPlayer()
            }
        }
    }

    private fun fetchRelated(track: Track) {
        viewModelScope.launch {
            try {
                val related: List<Track> = when {
                    track.ytVideoId.isNotBlank() -> {
                        val ytRel = ytMusicRepo.getRelatedTracks(track.ytVideoId)
                        if (ytRel.isNotEmpty()) ytRel.map { it.toTrack() }
                        else musicRepo.getRelatedTracks(track.artistName)
                    }
                    track.saavnId.isNotBlank() ->
                        saavnApi.getSuggestions(track.saavnId).map { it.toSaavnTrack() }
                    else -> musicRepo.getRelatedTracks(track.artistName)
                }
                _relatedTracks.value = related
            } catch (_: Exception) {}
        }
    }

    // ── Favorites ─────────────────────────────────────────────
    fun toggleFavorite(track: Track) {
        val cur = _favorites.value.toMutableList()
        val existing = cur.find { it.trackId == track.trackId }
        if (existing != null) cur.remove(existing)
        else cur.add(0, FavoriteTrack(track.trackId, track.trackName, track.artistName, track.artworkUrl100, track.previewUrl, track.primaryGenreName))
        _favorites.value = cur
        try { settingsRepo.saveFavorites(json.encodeToString<List<FavoriteTrack>>(cur)) } catch (_: Exception) {}
    }
    fun isFavorite(trackId: Long) = _favorites.value.any { it.trackId == trackId }

    // ── Playlists ─────────────────────────────────────────────
    fun createPlaylist(name: String) {
        val cur = _playlists.value.toMutableList()
        cur.add(Playlist(name = name))
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
        MusicPlayerManager.player?.stop()
        _isPlaying.value = false
        _currentPosition.value = 0
    }

    fun seekTo(ms: Int) {
        MusicPlayerManager.player?.seekTo(ms.toLong())
        _currentPosition.value = ms
    }

    fun skipForward() { seekTo((_currentPosition.value + 10000).coerceAtMost(_duration.value)) }
    fun skipBackward() { seekTo((_currentPosition.value - 10000).coerceAtLeast(0)) }
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
    }

    fun previousTrack() {
        if (_currentPosition.value > 3000) { seekTo(0); return }
        val list = _queue.value
        val cur = _selectedTrack.value ?: return
        val idx = list.indexOfFirst { it.trackId == cur.trackId }
        if (idx > 0) selectTrack(list[idx - 1], list)
    }

    fun simulateAnalysis() {
        viewModelScope.launch {
            _isAnalyzing.value = true; _analysisResult.value = null
            delay(2000)
            _analysisResult.value = "Upload file audio untuk analisa real."
            _isAnalyzing.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        MusicPlayerManager.player?.stop()
    }
}
