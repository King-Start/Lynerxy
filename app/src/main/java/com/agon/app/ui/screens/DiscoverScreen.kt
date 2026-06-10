package com.agon.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
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
    val searchHistory  by viewModel.searchHistory.collectAsState()
    val keyboard       = LocalSoftwareKeyboardController.current
    val focusManager   = LocalFocusManager.current
    var isFocused      by remember { mutableStateOf(false) }
    var selectedTab    by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // ── Search Bar ────────────────────────────────────────
        Box(Modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(Color(0xFF1A0A2E).copy(0.6f), Color.Transparent))
        ).padding(12.dp, 12.dp, 12.dp, 8.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search songs, artists...", color = Color.White.copy(0.4f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(0.5f)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, null, tint = Color.White.copy(0.5f))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(); keyboard?.hide(); focusManager.clearFocus(); isFocused = false }),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFA78BFA),
                    unfocusedBorderColor = Color.White.copy(0.15f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFFA78BFA),
                    containerColor = Color.White.copy(0.06f)
                ),
                shape = RoundedCornerShape(16.dp)
            )
        }

        // ── Suggestions / History overlay ─────────────────────
        if (searchQuery.isNotBlank() && (suggestions.isNotEmpty() || searchHistory.isNotEmpty())) {
            Surface(color = Color(0xFF12121A), modifier = Modifier.fillMaxWidth()) {
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    val items = if (suggestions.isNotEmpty()) suggestions else searchHistory.take(5)
                    items(items) { q ->
                        Row(
                            Modifier.fillMaxWidth().clickable { viewModel.updateSearchQuery(q); viewModel.search(); keyboard?.hide(); focusManager.clearFocus() }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(if (suggestions.isNotEmpty()) Icons.Default.Search else Icons.Default.History,
                                null, tint = Color.White.copy(0.4f), modifier = Modifier.size(16.dp))
                            Text(q, color = Color.White.copy(0.8f), fontSize = 14.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ── Tabs ─────────────────────────────────────────────
        if (searchResults.isEmpty() || searchQuery.isBlank()) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFFA78BFA),
                edgePadding = 16.dp,
                indicator = { tabs ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabs[selectedTab]),
                        color = Color(0xFFA78BFA)
                    )
                }
            ) {
                listOf("Home", "Charts", "Genre", "Library").forEachIndexed { idx, tab ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(tab, fontSize = 14.sp, fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal) },
                        selectedContentColor = Color.White,
                        unselectedContentColor = Color.White.copy(0.4f)
                    )
                }
            }
        }

        // ── Content ───────────────────────────────────────────
        when {
            isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFA78BFA))
            }
            searchResults.isNotEmpty() && searchQuery.isNotBlank() -> {
                SearchResultsList(searchResults, onTrackSelected, viewModel)
            }
            else -> when (selectedTab) {
                0 -> HomeTab(topCharts, recentlyPlayed, onTrackSelected, viewModel)
                1 -> ChartsTab(topCharts, onTrackSelected, viewModel)
                2 -> GenreTab(viewModel, onTrackSelected)
                3 -> LibraryTab(viewModel, onTrackSelected)
            }
        }
    }
}

@Composable
fun SearchResultsList(results: List<Track>, onSelect: (Track) -> Unit, viewModel: MusicViewModel) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Text("${results.size} results", color = Color.White.copy(0.4f), fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        items(results) { track ->
            TrackListItem(track, onSelect)
        }
    }
}

@Composable
fun HomeTab(
    topCharts: List<Track>,
    recentlyPlayed: List<com.agon.app.db.entities.SongEntity>,
    onSelect: (Track) -> Unit,
    viewModel: MusicViewModel
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        // Featured carousel
        if (topCharts.isNotEmpty()) {
            item {
                Text("Featured", color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 20.sp, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(topCharts.take(8)) { track -> FeaturedCard(track) { onSelect(track) } }
                }
            }
        }
        // Recently played
        if (recentlyPlayed.isNotEmpty()) {
            item {
                Text("Recently Played", color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentlyPlayed.take(10)) { song ->
                        val track = Track(trackId = song.id.hashCode().toLong(), trackName = song.title, artistName = song.artist, artworkUrl100 = song.thumbnailUrl, previewUrl = song.audioUrl, ytVideoId = song.id)
                        RecentCard(track) { onSelect(track) }
                    }
                }
            }
        }
        // Top charts list
        if (topCharts.isNotEmpty()) {
            item {
                Text("Top Charts", color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
            }
            items(topCharts.take(15)) { track -> TrackListItem(track, onSelect) }
        }
    }
}

@Composable
fun ChartsTab(charts: List<Track>, onSelect: (Track) -> Unit, viewModel: MusicViewModel) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(charts) { idx, track ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(track) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "%02d".format(idx + 1),
                    color = if (idx < 3) Color(0xFFA78BFA) else Color.White.copy(0.3f),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp)
                )
                AsyncImage(
                    model = track.artworkUrl100, contentDescription = null,
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(Modifier.weight(1f)) {
                    Text(track.trackName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artistName, color = Color.White.copy(0.5f), fontSize = 12.sp, maxLines = 1)
                }
                if (idx < 3) Icon(Icons.Default.TrendingUp, null, tint = Color(0xFFA78BFA), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun GenreTab(viewModel: MusicViewModel, onSelect: (Track) -> Unit) {
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
                Modifier.height(80.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(color, color.copy(0.5f))))
                    .clickable { viewModel.loadGenre(genre) },
                Alignment.CenterStart
            ) {
                Text(genre, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
fun LibraryTab(viewModel: MusicViewModel, onSelect: (Track) -> Unit) {
    val favorites by viewModel.favorites.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
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
            item { Text("Liked Songs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)) }
            items(favorites) { fav ->
                val track = Track(trackId = fav.trackId, trackName = fav.trackName, artistName = fav.artistName, artworkUrl100 = fav.artworkUrl100, previewUrl = fav.previewUrl)
                TrackListItem(track, onSelect)
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New Playlist") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) { viewModel.createPlaylist(name); showCreate = false } }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
            containerColor = Color(0xFF1E1E2E)
        )
    }
}

@Composable
fun FeaturedCard(track: Track, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 160.dp, height = 180.dp).clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = track.artworkUrl100.replace("100x100", "300x300"),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f)))
        ))
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(track.trackName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artistName, color = Color.White.copy(0.7f), fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
fun RecentCard(track: Track, onClick: () -> Unit) {
    Column(
        Modifier.width(90.dp).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = track.artworkUrl100,
            contentDescription = null,
            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(4.dp))
        Text(track.trackName, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artistName, color = Color.White.copy(0.5f), fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
fun TrackListItem(track: Track, onSelect: (Track) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onSelect(track) }.padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = track.artworkUrl100, contentDescription = null,
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
        Column(Modifier.weight(1f)) {
            Text(track.trackName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artistName, color = Color.White.copy(0.5f), fontSize = 12.sp, maxLines = 1)
        }
        Icon(Icons.Default.MoreVert, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(18.dp))
    }
}
