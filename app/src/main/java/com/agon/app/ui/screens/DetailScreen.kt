package com.agon.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.agon.app.data.Playlist
import com.agon.app.data.Track
import com.agon.app.ui.theme.extractGradientColors
import com.agon.app.ui.theme.extractThemeColor
import com.agon.app.viewmodel.MusicViewModel
import kotlinx.coroutines.launch

fun formatTime(ms: Int): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(viewModel: MusicViewModel, onBack: () -> Unit, onLyrics: () -> Unit = {}) {
    val track           by viewModel.selectedTrack.collectAsState()
    val isPlaying       by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration        by viewModel.duration.collectAsState()
    val isShuffled      by viewModel.isShuffled.collectAsState()
    val repeatMode      by viewModel.repeatMode.collectAsState()
    val relatedTracks   by viewModel.relatedTracks.collectAsState()
    val playlists       by viewModel.playlists.collectAsState()
    val lyrics          by viewModel.lyrics.collectAsState()
    val isLyricsLoading by viewModel.isLyricsLoading.collectAsState()
    val isFav           = viewModel.isFavorite(track?.trackId ?: 0)
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val context         = LocalContext.current
    val scope           = rememberCoroutineScope()

    // ── Dynamic gradient from album art ──────────────────────
    var gradientColors by remember { mutableStateOf(listOf(Color(0xFF1A0A2E), Color(0xFF0F0F0F))) }
    var dominantColor  by remember { mutableStateOf(Color(0xFF7C3AED)) }

    LaunchedEffect(track?.artworkUrl100) {
        val url = track?.artworkUrl100?.replace("100x100", "500x500") ?: return@LaunchedEffect
        try {
            val req = ImageRequest.Builder(context).data(url).allowHardware(false).build()
            val bmp = context.imageLoader.execute(req).image?.toBitmap() ?: return@LaunchedEffect
            gradientColors = bmp.extractGradientColors()
            dominantColor  = bmp.extractThemeColor()
        } catch (_: Exception) {}
    }

    // ── Swipe gesture for next/prev ───────────────────────────
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "art_scale"
    )

    if (track == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0F0F0F)), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("No track selected", color = Color.White.copy(0.4f))
            }
        }
        return
    }

    val t = track!!
    val artUrl = t.artworkUrl100.replace("100x100", "600x600")
    val songId = t.ytVideoId.ifBlank { t.saavnId.ifBlank { t.trackId.toString() } }
    val isDownloading = downloadProgress.containsKey(songId)
    val isDownloaded  = downloadProgress[songId] == 1f

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(gradientColors[0].copy(alpha = 0.9f), Color(0xFF0A0A0F), Color(0xFF0A0A0F)))
        )
    ) {
        // Blurred bg art
        AsyncImage(
            model = artUrl, contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(380.dp).blur(80.dp).alpha(0.25f),
            contentScale = ContentScale.Crop
        )

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {

            // ── Top bar ──────────────────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Now Playing", color = Color.White.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
                        Text(t.collectionName.ifBlank { t.primaryGenreName }.take(24), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, null, tint = Color.White)
                        }
                        DropdownMenu(showMenu, { showMenu = false }, containerColor = Color(0xFF1E1E2E)) {
                            DropdownMenuItem({ Text("Add to Playlist", color = Color.White) }, {
                                if (playlists.isNotEmpty()) viewModel.addTrackToPlaylist(playlists.first().id, t)
                                showMenu = false
                            })
                            DropdownMenuItem({ Text(if (isDownloaded) "Delete Download" else "Download", color = Color.White) }, {
                                if (isDownloaded) viewModel.deleteDownload(songId) else viewModel.downloadTrack(t)
                                showMenu = false
                            })
                            DropdownMenuItem({ Text("View Lyrics", color = Color.White) }, { onLyrics(); showMenu = false })
                        }
                    }
                }
            }

            // ── Album Art ─────────────────────────────────────
            item {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 16.dp),
                    Alignment.Center
                ) {
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .fillMaxWidth()
                            .scale(artScale)
                            .clip(RoundedCornerShape(20.dp))
                            .shadow(elevation = 24.dp, shape = RoundedCornerShape(20.dp), ambientColor = dominantColor, spotColor = dominantColor)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (dragOffset < -80f) viewModel.nextTrack()
                                        else if (dragOffset > 80f) viewModel.previousTrack()
                                        dragOffset = 0f
                                    }
                                ) { _, delta -> dragOffset += delta }
                            }
                    ) {
                        AsyncImage(
                            model = artUrl, contentDescription = t.trackName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // Download progress overlay
                        if (isDownloading && !isDownloaded) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)), Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { downloadProgress[songId] ?: 0f },
                                    color = dominantColor, trackColor = Color.White.copy(0.2f),
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Title + Artist + Fav ──────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.trackName, color = Color.White,
                            fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            t.artistName, color = Color.White.copy(0.6f),
                            fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { viewModel.toggleFavorite(t) }) {
                        Icon(
                            if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            null, tint = if (isFav) Color(0xFFFF4081) else Color.White.copy(0.6f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // ── Progress Slider ───────────────────────────────
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp)) {
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                        onValueChange = { viewModel.seekTo((it * duration).toInt()) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = dominantColor,
                            inactiveTrackColor = Color.White.copy(0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(formatTime(currentPosition), color = Color.White.copy(0.5f), fontSize = 12.sp)
                        Text(formatTime(duration), color = Color.White.copy(0.5f), fontSize = 12.sp)
                    }
                }
            }

            // ── Controls ──────────────────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleShuffle() }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Shuffle, null,
                            tint = if (isShuffled) dominantColor else Color.White.copy(0.5f),
                            modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { viewModel.previousTrack() }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    // Play/Pause button
                    Box(
                        Modifier.size(68.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(dominantColor, dominantColor.copy(0.7f))))
                            .clickable { viewModel.togglePlayPause() },
                        Alignment.Center
                    ) {
                        AnimatedContent(targetState = isPlaying, label = "play") { playing ->
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                null, tint = Color.White, modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.nextTrack() }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = { viewModel.toggleRepeat() }, modifier = Modifier.size(44.dp)) {
                        Icon(
                            when (repeatMode) { 2 -> Icons.Default.RepeatOne; else -> Icons.Default.Repeat },
                            null,
                            tint = if (repeatMode > 0) dominantColor else Color.White.copy(0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // ── Action Row (Lyrics, EQ, Download) ────────────
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionChip(Icons.Default.Lyrics, "Lyrics") { onLyrics() }
                    ActionChip(Icons.Default.Equalizer, "EQ") {}
                    ActionChip(
                        when {
                            isDownloaded -> Icons.Default.DownloadDone
                            isDownloading -> Icons.Default.Downloading
                            else -> Icons.Default.Download
                        }, "Save"
                    ) { if (!isDownloaded && !isDownloading) viewModel.downloadTrack(t) }
                    ActionChip(Icons.Default.Share, "Share") {}
                }
            }

            // ── Lyrics Preview ────────────────────────────────
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { onLyrics() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.07f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lyrics, null, tint = dominantColor, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Lyrics", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Text("Full View →", color = dominantColor, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        when {
                            isLyricsLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = dominantColor)
                                Spacer(Modifier.width(8.dp))
                                Text("Loading lyrics...", color = Color.White.copy(0.4f), fontSize = 13.sp)
                            }
                            !lyrics.isNullOrBlank() -> Text(
                                lyrics!!.lines().filter { it.isNotBlank() }.take(5).joinToString("\n"),
                                color = Color.White.copy(0.8f), fontSize = 14.sp, lineHeight = 22.sp
                            )
                            else -> Text("Lyrics not available", color = Color.White.copy(0.3f), fontSize = 13.sp)
                        }
                    }
                }
            }

            // ── Related Tracks ────────────────────────────────
            if (relatedTracks.isNotEmpty()) {
                item {
                    Text("Related", color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 18.sp, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
                }
                items(relatedTracks.take(10)) { rel ->
                    RelatedTrackItem(rel, dominantColor) { viewModel.selectTrack(rel, relatedTracks) }
                }
            }
        }
    }
}

@Composable
fun ActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(0.08f)),
            Alignment.Center
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(0.5f), fontSize = 11.sp)
    }
}

@Composable
fun RelatedTrackItem(track: Track, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = track.artworkUrl100, contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
        Column(Modifier.weight(1f)) {
            Text(track.trackName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artistName, color = Color.White.copy(0.5f), fontSize = 12.sp, maxLines = 1)
        }
        Icon(Icons.Default.PlayCircleOutline, null, tint = accent.copy(0.7f), modifier = Modifier.size(22.dp))
    }
}

// Modifier shadow extension helper
private fun Modifier.shadow(elevation: androidx.compose.ui.unit.Dp, shape: androidx.compose.ui.graphics.Shape, ambientColor: Color, spotColor: Color) = this
