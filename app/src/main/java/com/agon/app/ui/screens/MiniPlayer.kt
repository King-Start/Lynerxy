package com.agon.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.agon.app.viewmodel.MusicViewModel

@Composable
fun MiniPlayer(viewModel: MusicViewModel, onExpand: () -> Unit) {
    val track by viewModel.selectedTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()

    AnimatedVisibility(
        visible = track != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Progress bar
            if (duration > 0) {
                LinearProgressIndicator(
                    progress = { currentPosition.toFloat() / duration.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color(0xFF1DB954),
                    trackColor = Color.Transparent
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onExpand)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))) {
                    AsyncImage(
                        model = track?.artworkUrl100,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Track info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track?.trackName ?: "",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                    Text(
                        track?.artistName ?: "",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Controls
                IconButton(onClick = { viewModel.previousTrack() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipPrevious, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { viewModel.togglePlayPause() }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null, tint = Color(0xFF1DB954), modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(onClick = { viewModel.nextTrack() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipNext, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
