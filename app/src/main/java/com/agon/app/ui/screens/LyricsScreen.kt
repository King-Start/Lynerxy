package com.agon.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.agon.app.viewmodel.MusicViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val track          by viewModel.selectedTrack.collectAsState()
    val lyrics         by viewModel.lyrics.collectAsState()
    val lyricsEntries  by viewModel.lyricsEntries.collectAsState()
    val isLoading      by viewModel.isLyricsLoading.collectAsState()
    val isSynced       by viewModel.lyricsIsSynced.collectAsState()
    val currentIndex   by viewModel.currentLyricsIndex.collectAsState()
    val isPlaying      by viewModel.isPlaying.collectAsState()
    val position       by viewModel.currentPosition.collectAsState()
    val duration       by viewModel.duration.collectAsState()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll ke baris aktif
    LaunchedEffect(currentIndex) {
        if (isSynced && currentIndex >= 0 && lyricsEntries.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(
                    index = (currentIndex - 2).coerceAtLeast(0),
                    scrollOffset = 0
                )
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0F))) {
        // Background blur art
        track?.let {
            AsyncImage(
                model = it.artworkUrl100.replace("100x100", "500x500"),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(40.dp).padding(0.dp),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Color(0xCC0A0A0F)))
        }

        Column(Modifier.fillMaxSize()) {
            // Top bar
            TopAppBar(
                title = {
                    Column {
                        Text(
                            track?.trackName ?: "Lyrics",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        if (track != null)
                            Text(track!!.artistName, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )

            // Progress bar kecil
            if (duration > 0) {
                LinearProgressIndicator(
                    progress = { position.toFloat() / duration },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color(0xFF7C4DFF),
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }

            // Status badge
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MusicNote, null, tint = Color(0xFF7C4DFF), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isSynced) "Synced Lyrics" else "Static Lyrics",
                    fontSize = 11.sp,
                    color = Color(0xFF7C4DFF)
                )
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF7C4DFF))
                }
                isSynced && lyricsEntries.isNotEmpty() -> {
                    // Karaoke mode — highlight baris aktif
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 60.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(lyricsEntries) { idx, entry ->
                            if (entry.text.isBlank()) {
                                Spacer(Modifier.height(12.dp))
                            } else {
                                val isActive = idx == currentIndex
                                val isPast   = idx < currentIndex
                                AnimatedContent(
                                    targetState = isActive,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label = "lyric_line"
                                ) { active ->
                                    Text(
                                        text = entry.text,
                                        fontSize = if (active) 22.sp else 17.sp,
                                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Normal,
                                        color = when {
                                            active -> Color.White
                                            isPast -> Color.White.copy(alpha = 0.35f)
                                            else   -> Color.White.copy(alpha = 0.55f)
                                        },
                                        textAlign = TextAlign.Center,
                                        lineHeight = 30.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.seekTo(entry.time.toInt()) }
                                            .padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                !lyrics.isNullOrBlank() -> {
                    // Plain lyrics
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp)
                    ) {
                        item {
                            Text(
                                text = lyrics ?: "",
                                fontSize = 15.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                lineHeight = 26.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                else -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Lyrics not available", color = Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }
}
