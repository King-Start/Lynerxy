package com.agon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.agon.app.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzeScreen(viewModel: MusicViewModel, onOpenDrawer: () -> Unit = {}) {
    val favorites by viewModel.favorites.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val apiValidation by viewModel.apiValidationResult.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val apiProvider by viewModel.apiProvider.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Tools", "Favorit", "Recent")

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Tools & Library", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { idx, title ->
                Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(title) })
            }
        }

        when (selectedTab) {
            0 -> ToolsTab(viewModel, apiKey, apiProvider, apiValidation)
            1 -> FavoritesTab(favorites) { track ->
                viewModel.selectTrack(com.agon.app.data.Track(
                    trackId = track.trackId,
                    trackName = track.trackName,
                    artistName = track.artistName,
                    artworkUrl100 = track.artworkUrl100,
                    previewUrl = track.previewUrl,
                    primaryGenreName = track.primaryGenreName
                ))
            }
            2 -> RecentTab(recentlyPlayed) { viewModel.selectTrack(it) }
        }
    }
}

@Composable
fun ToolsTab(viewModel: MusicViewModel, apiKey: String, apiProvider: String, apiValidation: String?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // API Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (apiKey.length > 10)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (apiKey.length > 10) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (apiKey.length > 10) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (apiKey.length > 10) "API Key Terpasang ✅" else "API Key Belum Diset ⚠️",
                            fontWeight = FontWeight.Bold,
                            color = if (apiKey.length > 10) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Provider: $apiProvider",
                            fontSize = 12.sp,
                            color = if (apiKey.length > 10) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Button(
                        onClick = { viewModel.validateApiKey() },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) { Text("Test", fontSize = 12.sp) }
                }
                if (apiValidation != null) {
                    Text(
                        apiValidation,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        fontSize = 12.sp,
                        color = if (apiKey.length > 10) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        item {
            Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(
                    icon = Icons.Default.Delete,
                    title = "Clear Chat",
                    subtitle = "Hapus riwayat",
                    modifier = Modifier.weight(1f)
                ) { viewModel.clearChat() }
                QuickActionCard(
                    icon = Icons.Default.Shuffle,
                    title = "Shuffle",
                    subtitle = "Acak playlist",
                    modifier = Modifier.weight(1f)
                ) { viewModel.toggleShuffle() }
            }
        }

        item {
            Text("Tips & Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "🎵 Kategori 'Analisis Musik' → AI bedah genre, BPM, mood lagu lo",
                        "🖼️ Kategori 'Image Prompt' → Generate gambar GRATIS via Pollinations AI",
                        "📝 Kategori 'Suno Prompt' → Buat prompt optimal buat Suno AI",
                        "💻 Kategori 'Coding' → AI bikin kode HTML/CSS/JS langsung preview",
                        "🎤 Kategori 'Lirik' → AI bikin lirik lengkap dengan struktur",
                        "🔑 Gemini API Key GRATIS di aistudio.google.com",
                        "🔑 DeepSeek API Key MURAH di platform.deepseek.com"
                    ).forEach { tip ->
                        Text(tip, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FavoritesTab(favorites: List<com.agon.app.data.FavoriteTrack>, onPlay: (com.agon.app.data.FavoriteTrack) -> Unit) {
    if (favorites.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Belum ada favorit", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Tap ♡ di detail lagu untuk tambah", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(favorites) { fav ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(model = fav.artworkUrl100, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(fav.trackName, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(fav.artistName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = { onPlay(fav) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun RecentTab(recent: List<com.agon.app.data.Track>, onPlay: (com.agon.app.data.Track) -> Unit) {
    if (recent.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Belum ada riwayat", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(recent) { track ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(model = track.artworkUrl100, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.trackName, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(track.artistName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = { onPlay(track) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
