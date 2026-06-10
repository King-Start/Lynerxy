package com.agon.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.agon.app.data.FavoriteTrack
import com.agon.app.data.Track
import com.agon.app.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzeScreen(viewModel: MusicViewModel, onOpenDrawer: () -> Unit = {}) {
    val favorites      by viewModel.favorites.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState(initial = emptyList())
    val selectedTrack  by viewModel.selectedTrack.collectAsState()
    val isAnalyzing    by viewModel.isAnalyzing.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    var selectedTab    by remember { mutableStateOf(0) }
    val tabs = listOf("Tools","Favorit","Recent")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analyze", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0F), titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        },
        containerColor = Color(0xFF0A0A0F)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, contentColor = Color(0xFFA78BFA)) {
                tabs.forEachIndexed { idx, tab ->
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx },
                        text = { Text(tab, fontSize = 13.sp) },
                        selectedContentColor = Color.White, unselectedContentColor = Color.White.copy(0.4f))
                }
            }
            when (selectedTab) {
                0 -> ToolsTab(viewModel, selectedTrack, isAnalyzing, analysisResult)
                1 -> FavoritesTab(favorites) { viewModel.selectTrack(it) }
                2 -> RecentTab(
                    recentlyPlayed.map { s ->
                        Track(trackId = s.id.hashCode().toLong(), trackName = s.title, artistName = s.artist, artworkUrl100 = s.thumbnailUrl, previewUrl = s.audioUrl, ytVideoId = s.id)
                    }
                ) { viewModel.selectTrack(it) }
            }
        }
    }
}

@Composable
fun ToolsTab(viewModel: MusicViewModel, selectedTrack: com.agon.app.data.Track?, isAnalyzing: Boolean, analysisResult: String?) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Now playing card
        selectedTrack?.let { track ->
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF1A1A2E), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(model = track.artworkUrl100, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f)) {
                            Text("Now Playing", color = Color(0xFFA78BFA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(track.trackName, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artistName, color = Color.White.copy(0.5f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Analysis tools grid
        item {
            Text("Music Tools", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        val tools = listOf(
            Triple(Icons.Default.Analytics, "Analisis Lagu", "Analisis mendalam genre, BPM, mood"),
            Triple(Icons.Default.MicNone, "Generate Lirik", "Buat lirik original Bahasa Indonesia"),
            Triple(Icons.Default.Tune, "Suno AI Prompt", "Buat prompt optimal untuk Suno AI"),
            Triple(Icons.Default.Code, "Music Info", "Info detail dari YouTube Music"),
            Triple(Icons.Default.Image, "Generate Artwork", "Buat cover art dengan AI"),
            Triple(Icons.Default.Share, "Share Analysis", "Export analisis ke teks")
        )

        items(tools.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (icon, title, desc) ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF141428),
                        modifier = Modifier.weight(1f).clickable {
                            val track = selectedTrack ?: return@clickable
                            viewModel.sendMessage("$title: ${track.trackName} - ${track.artistName}", title)
                        }
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF7C3AED).copy(0.2f)), Alignment.Center) {
                                Icon(icon, null, tint = Color(0xFFA78BFA), modifier = Modifier.size(20.dp))
                            }
                            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(desc, color = Color.White.copy(0.4f), fontSize = 10.sp, lineHeight = 14.sp)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (isAnalyzing) item {
            Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFA78BFA))
            }
        }

        if (!analysisResult.isNullOrBlank()) item {
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF141428), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Analysis Result", color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(analysisResult, color = Color.White, fontSize = 14.sp, lineHeight = 22.sp)
                }
            }
        }
    }
}

@Composable
fun FavoritesTab(favorites: List<FavoriteTrack>, onPlay: (Track) -> Unit) {
    if (favorites.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FavoriteBorder, null, tint = Color.White.copy(0.2f), modifier = Modifier.size(56.dp))
                Text("No favorites yet", color = Color.White.copy(0.4f))
                Text("Tap ♥ on any song", color = Color.White.copy(0.25f), fontSize = 13.sp)
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(favorites) { fav ->
                val track = Track(trackId = fav.trackId, trackName = fav.trackName, artistName = fav.artistName, artworkUrl100 = fav.artworkUrl100, previewUrl = fav.previewUrl, primaryGenreName = fav.genre)
                Row(
                    Modifier.fillMaxWidth().clickable { onPlay(track) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(model = fav.artworkUrl100, contentDescription = null, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f)) {
                        Text(fav.trackName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(fav.artistName, color = Color.White.copy(0.5f), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF4081), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun RecentTab(recent: List<Track>, onPlay: (Track) -> Unit) {
    if (recent.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.History, null, tint = Color.White.copy(0.2f), modifier = Modifier.size(56.dp))
                Text("No history yet", color = Color.White.copy(0.4f))
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(recent) { track ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPlay(track) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(model = track.artworkUrl100, contentDescription = null, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f)) {
                        Text(track.trackName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artistName, color = Color.White.copy(0.5f), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
