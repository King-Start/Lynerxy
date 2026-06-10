package com.agon.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.agon.app.data.Playlist
import com.agon.app.data.Track
import com.agon.app.viewmodel.MusicViewModel

fun formatTime(ms: Int): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(viewModel: MusicViewModel, onBack: () -> Unit, onLyrics: () -> Unit = {}) {
    val track by viewModel.selectedTrack.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val isLyricsLoading by viewModel.isLyricsLoading.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val mediaType by viewModel.mediaType.collectAsState()
    val isShuffled by viewModel.isShuffled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val relatedTracks by viewModel.relatedTracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val context = LocalContext.current

    if (track == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No track selected", color = Color.White)
        }
        return
    }

    val isFav = viewModel.isFavorite(track!!.trackId)
    val highRes = track!!.artworkUrl100.replace("100x100", "600x600")
    val videoId = track!!.previewUrl

    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showVideoPlayer by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Blurred background
        AsyncImage(
            model = if (mediaType == "Video") "https://img.youtube.com/vi/$videoId/maxresdefault.jpg" else highRes,
            contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(40.dp)
        )
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Black.copy(.4f), Color.Black.copy(.7f), Color.Black))
        ))

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                // Top bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (mediaType == "Video") "Video" else "Now Playing",
                            color = Color.White.copy(.7f), fontSize = 12.sp)
                        Text(track!!.collectionName.take(20).ifBlank { track!!.artistName },
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { showAddToPlaylist = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = Color.White)
                    }
                }
            }

            item {
                if (mediaType == "Video") {
                    // YouTube thumbnail + player
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        if (!showVideoPlayer) {
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp))) {
                                AsyncImage(
                                    model = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg",
                                    contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                )
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(.3f)))
                                FloatingActionButton(
                                    onClick = { showVideoPlayer = true },
                                    modifier = Modifier.align(Alignment.Center).size(60.dp),
                                    shape = CircleShape, containerColor = Color.White
                                ) { Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(32.dp)) }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
                                AndroidView(factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        webChromeClient = WebChromeClient()
                                        webViewClient = WebViewClient()
                                        loadDataWithBaseURL("https://www.youtube.com",
                                            """<html><body style="margin:0;padding:0;background:black;"><iframe width="100%" height="100%" src="https://www.youtube.com/embed/$videoId?autoplay=1&rel=0" frameborder="0" allowfullscreen></iframe></body></html>""",
                                            "text/html", "UTF-8", null)
                                    }
                                }, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Tap untuk putar video full", color = Color.White.copy(.6f), fontSize = 12.sp)
                    }
                } else {
                    // Album art
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp, 16.dp), contentAlignment = Alignment.Center) {
                        Card(
                            modifier = Modifier.size(280.dp),
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(24.dp)
                        ) {
                            AsyncImage(model = highRes, contentDescription = null,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }

            item {
                // Track info + favorite
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track!!.trackName, color = Color.White, fontSize = 22.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(track!!.artistName, color = Color.White.copy(.7f), fontSize = 16.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { viewModel.toggleFavorite(track!!) }) {
                        Icon(
                            if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            null, tint = if (isFav) Color(0xFF1DB954) else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            if (mediaType == "Music") {
                item {
                    // Seekbar
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                            onValueChange = { viewModel.seekTo((it * duration).toInt()) },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(.3f)
                            )
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(currentPosition), color = Color.White.copy(.7f), fontSize = 12.sp)
                            Text(if (duration > 0) formatTime(duration) else if (track!!.durationSeconds > 0) formatTime(track!!.durationSeconds * 1000) else "--:--", color = Color.White.copy(.7f), fontSize = 12.sp)
                        }
                    }
                }

                item {
                    // Controls
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = { viewModel.toggleShuffle() }) {
                            Icon(Icons.Default.Shuffle, null,
                                tint = if (isShuffled) Color(0xFF1DB954) else Color.White.copy(.7f),
                                modifier = Modifier.size(26.dp))
                        }
                        IconButton(onClick = { viewModel.previousTrack() }) {
                            Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(38.dp))
                        }
                        FloatingActionButton(
                            onClick = { viewModel.togglePlayPause() },
                            shape = CircleShape, containerColor = Color.White,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                null, tint = Color.Black, modifier = Modifier.size(38.dp))
                        }
                        IconButton(onClick = { viewModel.nextTrack() }) {
                            Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(38.dp))
                        }
                        IconButton(onClick = { viewModel.toggleRepeat() }) {
                            Icon(
                                if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat, null,
                                tint = if (repeatMode > 0) Color(0xFF1DB954) else Color.White.copy(.7f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                item {
                    // Skip buttons + download
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.skipBackward() }) {
                            Icon(Icons.Default.Replay10, null, tint = Color.White.copy(.7f), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("-10s", color = Color.White.copy(.7f), fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            val url = track!!.previewUrl
                            if (url.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }) {
                            Icon(Icons.Default.Download, null, tint = Color.White.copy(.7f), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Preview", color = Color.White.copy(.7f), fontSize = 12.sp)
                        }
                        TextButton(onClick = { viewModel.skipForward() }) {
                            Text("+10s", color = Color.White.copy(.7f), fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.Forward10, null, tint = Color.White.copy(.7f), modifier = Modifier.size(22.dp))
                        }
                    }
                }

                // Lyrics card with open button
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(.08f)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Lyrics", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                if (!isLyricsLoading && lyrics != null) {
                                    TextButton(onClick = onLyrics) {
                                        Text("Full View", color = Color(0xFF7C4DFF), fontSize = 13.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            when {
                                isLyricsLoading -> CircularProgressIndicator(color = Color(0xFF1DB954))
                                lyrics != null -> Text(
                                    lyrics!!.lines().take(6).joinToString("\n"),
                                    color = Color.White.copy(.85f),
                                    textAlign = TextAlign.Center, lineHeight = 26.sp, fontSize = 15.sp
                                )
                                else -> Text("Lyrics tidak tersedia.", color = Color.White.copy(.5f), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }

                // Related tracks
                if (relatedTracks.isNotEmpty()) {
                    item {
                        Text("Lagu Lainnya dari ${track!!.artistName}",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            modifier = Modifier.padding(16.dp, 8.dp))
                    }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(relatedTracks.filter { it.trackId != track!!.trackId }.take(10)) { rel ->
                                Column(modifier = Modifier.width(100.dp).clickable { viewModel.selectTrack(rel) }) {
                                    Box(Modifier.size(100.dp).clip(RoundedCornerShape(10.dp))) {
                                        AsyncImage(model = rel.artworkUrl100, contentDescription = null,
                                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(rel.trackName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                    Text(rel.artistName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontSize = 10.sp, color = Color.White.copy(.6f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add to playlist dialog
    if (showAddToPlaylist) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylist = false },
            title = { Text("Tambah ke Playlist") },
            text = {
                Column {
                    playlists.forEach { pl ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.addTrackToPlaylist(pl.id, track!!)
                                showAddToPlaylist = false
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.QueueMusic, null, tint = Color(0xFF1DB954))
                            Spacer(Modifier.width(12.dp))
                            Text(pl.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddToPlaylist = false }) { Text("Batal") } }
        )
    }
}
