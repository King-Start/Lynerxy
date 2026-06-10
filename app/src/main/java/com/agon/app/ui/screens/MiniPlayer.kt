package com.agon.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.agon.app.ui.theme.extractThemeColor
import com.agon.app.viewmodel.MusicViewModel

@Composable
fun MiniPlayer(viewModel: MusicViewModel, onExpand: () -> Unit) {
    val track    by viewModel.selectedTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val context  = LocalContext.current

    var accentColor by remember { mutableStateOf(Color(0xFFA78BFA)) }
    var dragOffset  by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(track?.artworkUrl100) {
        val url = track?.artworkUrl100 ?: return@LaunchedEffect
        try {
            val req = ImageRequest.Builder(context).data(url).allowHardware(false).build()
            val bmp = context.imageLoader.execute(req).image?.toBitmap() ?: return@LaunchedEffect
            accentColor = bmp.extractThemeColor()
        } catch (_: Exception) {}
    }

    AnimatedVisibility(
        visible = track != null,
        enter = slideInVertically { it } + fadeIn(),
        exit  = slideOutVertically { it } + fadeOut()
    ) {
        val t = track ?: return@AnimatedVisibility
        val progress = if (duration > 0) position.toFloat() / duration else 0f

        Box(
            Modifier.fillMaxWidth().height(64.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(Color(0xFF1A1A2E))
                .drawWithContent {
                    drawContent()
                    // Progress line at bottom
                    drawLine(
                        color = accentColor.copy(alpha = 0.3f),
                        start = Offset(0f, size.height - 2.dp.toPx()),
                        end = Offset(size.width, size.height - 2.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = accentColor,
                        start = Offset(0f, size.height - 2.dp.toPx()),
                        end = Offset(size.width * progress, size.height - 2.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                }
                .clickable { onExpand() }
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
            Row(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Album art
                AsyncImage(
                    model = t.artworkUrl100.replace("100x100","200x200"),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                // Title
                Column(Modifier.weight(1f)) {
                    Text(
                        t.trackName, color = Color.White, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        t.artistName, color = Color.White.copy(0.55f), fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                // Prev
                IconButton(onClick = { viewModel.previousTrack() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipPrevious, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(20.dp))
                }
                // Play/Pause
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(accentColor).clickable { viewModel.togglePlayPause() },
                    Alignment.Center
                ) {
                    AnimatedContent(targetState = isPlaying, label = "mini_play") { playing ->
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                // Next
                IconButton(onClick = { viewModel.nextTrack() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipNext, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
