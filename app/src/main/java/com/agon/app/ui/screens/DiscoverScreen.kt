package com.agon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.agon.app.data.Playlist
import com.agon.app.data.Track
import com.agon.app.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: MusicViewModel,
    onTrackSelected: (Track) -> Unit,
    onOpenDrawer: () -> Unit = {}
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val topCharts by viewModel.topCharts.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState(initial = emptyList())
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Home", "Search", "Library")

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Music", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
            navigationIcon = {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.background) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                    text = { Text(t, fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal) })
            }
        }

        when (selectedTab) {
            0 -> HomeTab(topCharts, recentlyPlayed, onTrackSelected, viewModel)
            1 -> SearchTab(searchQuery, searchResults, isLoading, keyboardController, viewModel, onTrackSelected)
            2 -> LibraryTab(viewModel, onTrackSelected)
        }
    }
}

@Composable
fun HomeTab(topCharts: List<Track>, recentlyPlayed: List<com.agon.app.db.entities.SongEntity>, onSelect: (Track) -> Unit, viewModel: MusicViewModel) {
    val genres = listOf("Pop", "Rock", "Hip-Hop", "Electronic", "R&B", "Jazz", "K-Pop", "Dangdut", "Indie", "Lo-fi", "Metal", "Classical")
    val genreColors = listOf(
        Color(0xFF1DB954), Color(0xFFE91429), Color(0xFF509BF5),
        Color(0xFF8D67AB), Color(0xFFE8115B), Color(0xFFF59B23),
        Color(0xFFFF5E83), Color(0xFFFF8C00), Color(0xFF4CAF50),
        Color(0xFF9B59B6), Color(0xFF2C3E50), Color(0xFF8B4513)
    )

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        // Recently played
        if (recentlyPlayed.isNotEmpty()) {
            item {
                Text("Baru Diputar", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp))
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recentlyPlayed.take(10)) { song ->
                        val track = Track(trackId = song.id.hashCode().toLong(), trackName = song.title, artistName = song.artist, artworkUrl100 = song.thumbnailUrl, previewUrl = song.audioUrl, ytVideoId = song.id)
                        RecentCard(track) { onSelect(track) }
                    }
                }
            }
        }

        // Genre bubbles
        item {
            Text("Browse Genre", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 24.dp, 16.dp, 8.dp))
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(genres.zip(genreColors)) { (genre, color) ->
                    Box(
                        modifier = Modifier.size(width = 110.dp, height = 56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(color)
                            .clickable { viewModel.loadGenre(genre) },
                        contentAlignment = Alignment.Center
                    ) { Text(genre, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                }
            }
        }

        // Top Charts
        if (topCharts.isNotEmpty()) {
            item {
                Text("Top Charts", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 24.dp, 16.dp, 8.dp))
            }
            items(topCharts.take(30)) { track ->
                val idx = topCharts.indexOf(track) + 1
                TrackRow(track = track, index = idx, onSelect = { onSelect(track) }, viewModel = viewModel)
            }
        }
    }
}

@Composable
fun RecentCard(track: Track, onClick: () -> Unit) {
    Column(modifier = Modifier.width(100.dp).clickable(onClick = onClick)) {
        Box(modifier = Modifier.size(100.dp).clip(RoundedCornerShape(12.dp))) {
            AsyncImage(model = track.artworkUrl100.replace("100x100", "300x300"),
                contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.height(4.dp))
        Text(track.trackName, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(track.artistName, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TrackRow(track: Track, index: Int = 0, onSelect: () -> Unit, viewModel: MusicViewModel) {
    val isFav = viewModel.isFavorite(track.trackId)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (index > 0) {
            Text("$index", modifier = Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))) {
            AsyncImage(model = track.artworkUrl100, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.trackName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(track.artistName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { viewModel.toggleFavorite(track) }, modifier = Modifier.size(36.dp)) {
            Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = if (isFav) Color(0xFF1DB954) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
        }
        Icon(Icons.Default.PlayArrow, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTab(
    searchQuery: String, results: List<Track>, isLoading: Boolean,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    viewModel: MusicViewModel, onSelect: (Track) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
            placeholder = { Text("Cari di YouTube Music...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search(); keyboardController?.hide() })
        )

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF1DB954))
            }
        } else if (results.isEmpty() && searchQuery.isNotBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Tidak ditemukan", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                items(results) { track ->
                    TrackRow(track = track, onSelect = { onSelect(track) }, viewModel = viewModel)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun LibraryTab(viewModel: MusicViewModel, onSelect: (Track) -> Unit) {
    val playlists by viewModel.playlists.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create Playlist", tint = Color(0xFF1DB954))
                }
            }
        }

        // Liked Songs card
        item {
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    favorites.forEach { f ->
                        viewModel.addTrackToPlaylist("liked", com.agon.app.data.Track(
                            trackId = f.trackId, trackName = f.trackName, artistName = f.artistName,
                            artworkUrl100 = f.artworkUrl100, previewUrl = f.previewUrl, primaryGenreName = f.primaryGenreName
                        ))
                    }
                }.padding(16.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF4B2EC8), Color(0xFF1DB954)))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Liked Songs", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${favorites.size} lagu", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Playlists
        items(playlists) { playlist ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))) {
                    if (playlist.coverUrl.isNotBlank()) {
                        AsyncImage(model = playlist.coverUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${playlist.tracks.size} lagu", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Buat Playlist Baru") },
            text = {
                OutlinedTextField(value = newPlaylistName, onValueChange = { newPlaylistName = it },
                    label = { Text("Nama Playlist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) { viewModel.createPlaylist(newPlaylistName); newPlaylistName = "" }
                    showCreateDialog = false
                }) { Text("Buat", color = Color(0xFF1DB954)) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Batal") } }
        )
    }
}
