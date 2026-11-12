package com.agon.app.ui.screens

import androidx.compose.animation.*
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
    val track     by viewModel.selectedTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position  by viewModel.currentPosition.collectAsState()
    val duration  by viewModel.duration.collectAsState()
    val context   = LocalContext.current
    var accent    by remember { mutableStateOf(Color(0xFFA78BFA)) }
    var dragX     by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(track?.artworkUrl100) {
        val url = track?.artworkUrl100 ?: return@LaunchedEffect
        try {
            val bmp = context.imageLoader.execute(
                ImageRequest.Builder(context).data(url).allowHardware(false).build()
            ).image?.toBitmap() ?: return@LaunchedEffect
            accent = bmp.extractThemeColor()
        } catch (_: Exception) {}
    }

    AnimatedVisibility(
        visible = track != null,
        enter = slideInVertically { it } + fadeIn(),
        exit  = slideOutVertically { it } + fadeOut()
    ) {
        val t = track ?: return@AnimatedVisibility
        val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragX < -80f) viewModel.nextTrack()
                            else if (dragX > 80f) viewModel.previousTrack()
                            dragX = 0f
                        }
                    ) { _, delta -> dragX += delta }
                },
            color = Color(0xFF151528),
            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
        ) {
            Box(
                Modifier.fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        // progress bar di atas
                        drawLine(
                            color = accent.copy(alpha = 0.2f),
                            start = Offset(0f, 0f), end = Offset(size.width, 0f),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawLine(
                            color = accent,
                            start = Offset(0f, 0f), end = Offset(size.width * progress, 0f),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                    .clickable { onExpand() }
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Art
                    AsyncImage(
                        model = t.artworkUrl100,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    // Title
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            t.trackName,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            t.artistName,
                            color = Color.White.copy(0.5f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Controls
                    IconButton(
                        onClick = { viewModel.previousTrack() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.SkipPrevious, null,
                            tint = Color.White.copy(0.7f), modifier = Modifier.size(22.dp))
                    }
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .background(accent)
                            .clickable { viewModel.togglePlayPause() },
                        Alignment.Center
                    ) {
                        AnimatedContent(targetState = isPlaying, label = "mp") { p ->
                            Icon(
                                if (p) Icons.Default.Pause else Icons.Default.PlayArrow,
                                null, tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.nextTrack() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, null,
                            tint = Color.White.copy(0.7f), modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}
