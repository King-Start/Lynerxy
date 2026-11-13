package com.agon.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.agon.app.data.Track
import com.agon.app.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: MusicViewModel,
    onTrackSelected: (Track) -> Unit,
    onOpenDrawer: () -> Unit = {}
) {
    val searchQuery    by viewModel.searchQuery.collectAsState()
    val searchResults  by viewModel.searchResults.collectAsState()
    val isLoading      by viewModel.isLoading.collectAsState()
    val topCharts      by viewModel.topCharts.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState(initial = emptyList())
    val suggestions    by viewModel.searchSuggestions.collectAsState()
    val keyboard       = LocalSoftwareKeyboardController.current
    val focusManager   = LocalFocusManager.current
    var selectedTab    by remember { mutableStateOf(0) }
    val tabs           = listOf("Home", "Charts", "Genre", "Library")

    Column(
        Modifier.fillMaxSize().background(Color(0xFF0A0A0F))
    ) {
        // ── Status bar + Search bar ───────────────────────────
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF1A0A2E), Color(0xFF0A0A0F))))
        ) {
            Column(Modifier.statusBarsPadding().padding(12.dp, 8.dp, 12.dp, 12.dp)) {
                // Title row
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, null, tint = Color.White)
                    }
                    Text("LyronixAi", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.NotificationsNone, null, tint = Color.White.copy(0.6f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Cari lagu, artis...", color = Color.White.copy(0.4f), fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(0.5f)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank())
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Close, null, tint = Color.White.copy(0.5f))
                            }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        viewModel.search(); keyboard?.hide(); focusManager.clearFocus()
                    }),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color(0xFFA78BFA),
                        unfocusedBorderColor = Color.White.copy(0.12f),
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        cursorColor          = Color(0xFFA78BFA),
                        focusedContainerColor   = Color.White.copy(0.05f),
                        unfocusedContainerColor = Color.White.copy(0.04f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }

        // ── Suggestions dropdown ──────────────────────────────
        if (searchQuery.length >= 2 && suggestions.isNotEmpty()) {
            Surface(Modifier.fillMaxWidth(), color = Color(0xFF16162A)) {
                LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    items(suggestions.take(5)) { q ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                viewModel.updateSearchQuery(q)
                                viewModel.search()
                                keyboard?.hide(); focusManager.clearFocus()
                            }.padding(horizontal = 20.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Search, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(16.dp))
                            Text(q, color = Color.White.copy(0.85f), fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // ── Content ───────────────────────────────────────────
        when {
            isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFA78BFA), modifier = Modifier.size(40.dp))
            }
            searchResults.isNotEmpty() && searchQuery.isNotBlank() -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item {
                        Text(
                            "${searchResults.size} hasil untuk \"$searchQuery\"",
                            color = Color.White.copy(0.4f), fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        )
                    }
                    items(searchResults, key = { it.trackId }) { track ->
                        TrackListItem(track) { onTrackSelected(track) }
                    }
                }
            }
            else -> {
                // Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = Color.Transparent,
                    contentColor     = Color(0xFFA78BFA),
                    edgePadding      = 12.dp,
                    divider          = {}
                ) {
                    tabs.forEachIndexed { idx, tab ->
                        Tab(
                            selected  = selectedTab == idx,
                            onClick   = { selectedTab = idx },
                            text      = {
                                Text(
                                    tab,
                                    fontSize   = 13.sp,
                                    fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            selectedContentColor   = Color.White,
                            unselectedContentColor = Color.White.copy(0.4f)
                        )
                    }
                }

                when (selectedTab) {
                    0 -> DiscHomeTab(topCharts, recentlyPlayed, onTrackSelected, viewModel)
                    1 -> DiscChartsTab(topCharts, onTrackSelected)
                    2 -> DiscGenreTab(viewModel, onTrackSelected)
                    3 -> DiscLibraryTab(viewModel, onTrackSelected)
                }
            }
        }
    }
}

@Composable
fun DiscHomeTab(
    topCharts: List<Track>,
    recentlyPlayed: List<com.agon.app.db.entities.SongEntity>,
    onSelect: (Track) -> Unit,
    viewModel: MusicViewModel
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        if (topCharts.isNotEmpty()) {
            item {
                Text(
                    "Featured", color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 10.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(topCharts.take(8), key = { it.trackId }) { t -> DiscFeaturedCard(t) { onSelect(t) } }
                }
            }
        }
        if (recentlyPlayed.isNotEmpty()) {
            item {
                Text(
                    "Recently Played", color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 10.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentlyPlayed.take(10)) { song ->
                        val t = Track(trackId = song.id.hashCode().toLong(), trackName = song.title, artistName = song.artist, artworkUrl100 = song.thumbnailUrl, previewUrl = song.audioUrl, ytVideoId = song.id)
                        DiscRecentCard(t) { onSelect(t) }
                    }
                }
            }
        }
        if (topCharts.isNotEmpty()) {
            item {
                Text(
                    "Top Charts", color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
                )
            }
            items(topCharts.take(15), key = { it.trackId }) { t -> TrackListItem(t) { onSelect(t) } }
        }
    }
}

@Composable
fun DiscChartsTab(charts: List<Track>, onSelect: (Track) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(charts, key = { _, t -> t.trackId }) { idx, t ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(t) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "%02d".format(idx + 1),
                    color = if (idx < 3) Color(0xFFA78BFA) else Color.White.copy(0.3f),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp)
                )
                AsyncImage(
                    model = t.artworkUrl100, contentDescription = null,
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(Modifier.weight(1f)) {
                    Text(t.trackName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artistName, color = Color.White.copy(0.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun DiscGenreTab(viewModel: MusicViewModel, onSelect: (Track) -> Unit) {
    val genres = listOf(
        "Pop" to Color(0xFFFF6B6B), "Hip-Hop" to Color(0xFF4ECDC4),
        "Rock" to Color(0xFF45B7D1), "R&B" to Color(0xFFFFA07A),
        "Electronic" to Color(0xFFA78BFA), "Jazz" to Color(0xFF98D8C8),
        "Classical" to Color(0xFFDDA0DD), "K-Pop" to Color(0xFFFF69B4),
        "Indie" to Color(0xFF90EE90), "Metal" to Color(0xFFB8B8B8),
        "Latin" to Color(0xFFFFD700), "Country" to Color(0xFFDEB887)
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(genres) { (genre, color) ->
            Box(
                Modifier.height(76.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(color, color.copy(0.5f))))
                    .clickable { viewModel.loadGenre(genre) },
                Alignment.CenterStart
            ) {
                Text(genre, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
fun DiscLibraryTab(viewModel: MusicViewModel, onSelect: (Track) -> Unit) {
    val favorites by viewModel.favorites.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Text("Playlists", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, null, tint = Color(0xFFA78BFA))
                }
            }
        }
        items(playlists) { pl ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2A3E)),
                    Alignment.Center
                ) {
                    if (pl.coverUrl.isNotBlank())
                        AsyncImage(model = pl.coverUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else
                        Icon(Icons.Default.QueueMusic, null, tint = Color(0xFFA78BFA))
                }
                Column(Modifier.weight(1f)) {
                    Text(pl.name, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("${pl.tracks.size} songs", color = Color.White.copy(0.4f), fontSize = 12.sp)
                }
            }
        }
        if (favorites.isNotEmpty()) {
            item {
                Text("Liked Songs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
            }
            items(favorites, key = { it.trackId }) { fav ->
                val t = Track(trackId = fav.trackId, trackName = fav.trackName, artistName = fav.artistName, artworkUrl100 = fav.artworkUrl100, previewUrl = fav.previewUrl)
                TrackListItem(t) { onSelect(t) }
            }
        }
    }
    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New Playlist", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFA78BFA), focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White, cursorColor = Color(0xFFA78BFA)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { if (name.isNotBlank()) { viewModel.createPlaylist(name); showCreate = false } }) { Text("Create", color = Color(0xFFA78BFA)) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
            containerColor = Color(0xFF1E1E2E)
        )
    }
}

// ── Shared composables ─────────────────────────────────────

@Composable
fun DiscFeaturedCard(track: Track, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 155.dp, height = 175.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = track.artworkUrl100.replace("100x100", "300x300"),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f))))
        )
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(track.trackName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artistName, color = Color.White.copy(0.7f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun DiscRecentCard(track: Track, onClick: () -> Unit) {
    Column(
        Modifier.width(80.dp).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = track.artworkUrl100,
            contentDescription = null,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(4.dp))
        Text(track.trackName, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artistName, color = Color.White.copy(0.4f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TrackListItem(track: Track, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onSelect() }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = track.artworkUrl100, contentDescription = null,
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
        Column(Modifier.weight(1f)) {
            Text(track.trackName, color = Color.White, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artistName, color = Color.White.copy(0.5f), fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.PlayCircleOutline, null,
            tint = Color.White.copy(0.25f), modifier = Modifier.size(20.dp))
    }
}
