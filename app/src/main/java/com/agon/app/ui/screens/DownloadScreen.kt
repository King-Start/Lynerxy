package com.agon.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.agon.app.db.entities.SongEntity
import com.agon.app.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val downloaded by viewModel.downloadedSongs.collectAsStateWithLifecycle(emptyList())
    val progress   by viewModel.downloadProgress.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Downloads", fontWeight = FontWeight.Bold)
                        Text("${downloaded.size} songs", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF12121A), titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        },
        containerColor = Color(0xFF0A0A0F)
    ) { padding ->
        if (downloaded.isEmpty() && progress.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.DownloadDone, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    Text("No downloads yet", color = Color.White.copy(alpha = 0.5f))
                    Text("Tap ↓ on any song to download", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Active downloads
                if (progress.isNotEmpty()) {
                    item {
                        Text("Downloading", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    items(progress.entries.toList()) { (id, prog) ->
                        ActiveDownloadCard(id, prog, onCancel = { viewModel.cancelDownload(id) })
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
                // Downloaded
                if (downloaded.isNotEmpty()) {
                    item { Text("Saved offline", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp)) }
                    items(downloaded) { song ->
                        DownloadedSongCard(song, onDelete = { viewModel.deleteDownload(song.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveDownloadCard(id: String, progress: Float, onCancel: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1A1A2E), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(36.dp), color = Color(0xFF7C4DFF), trackColor = Color.White.copy(alpha = 0.1f))
            Column(Modifier.weight(1f)) {
                Text(id.take(16), color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${(progress * 100).toInt()}%", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun DownloadedSongCard(song: SongEntity, onDelete: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1A1A2E), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (song.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A3E)), Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = Color(0xFF7C4DFF), modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(song.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.OfflineBolt, null, tint = Color(0xFF7C4DFF), modifier = Modifier.size(12.dp))
                    Text("Offline", color = Color(0xFF7C4DFF), fontSize = 11.sp)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, null, tint = Color.White.copy(alpha = 0.4f))
            }
        }
    }
}
