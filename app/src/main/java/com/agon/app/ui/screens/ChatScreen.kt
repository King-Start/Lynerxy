package com.agon.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import com.agon.app.data.ChatMessage
import com.agon.app.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: MusicViewModel, onOpenDrawer: () -> Unit = {}) {
    val messages       by viewModel.chatMessages.collectAsState()
    val isTyping       by viewModel.isAiTyping.collectAsState()
    val selectedTrack  by viewModel.selectedTrack.collectAsState()
    var inputText      by remember { mutableStateOf("") }
    var selectedCat    by remember { mutableStateOf("Chat") }
    val categories     = listOf("Chat","Analisis Musik","Lirik","Suno Prompt","Coding","Web Dev","Image Prompt")
    val listState      = rememberLazyListState()
    val keyboard       = LocalSoftwareKeyboardController.current
    val focusManager   = LocalFocusManager.current

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFA78BFA)))), Alignment.Center) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text("LyronixAi", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("AI Music Assistant", fontSize = 11.sp, color = Color.White.copy(0.5f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, null, tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(Icons.Default.Delete, null, tint = Color.White.copy(0.5f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0F))
            )
        },
        containerColor = Color(0xFF0A0A0F),
        contentWindowInsets = WindowInsets.ime
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Category chips ────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCat == cat,
                        onClick = { selectedCat = cat },
                        label = { Text(cat, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF7C3AED),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1A1A2E),
                            labelColor = Color.White.copy(0.6f)
                        )
                    )
                }
            }

            // ── Now playing context ───────────────────────────
            selectedTrack?.let { track ->
                Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp), color = Color.White.copy(0.05f)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AsyncImage(model = track.artworkUrl100, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f)) {
                            Text("Now Playing", color = Color(0xFFA78BFA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(track.trackName, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = {
                            inputText = "Analisis lagu: ${track.trackName} - ${track.artistName}"
                            selectedCat = "Analisis Musik"
                        }) { Text("Analisis", color = Color(0xFFA78BFA), fontSize = 11.sp) }
                    }
                }
            }

            // ── Messages ──────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg -> MessageBubble(msg) }
                if (isTyping) item { TypingIndicator() }
            }

            // ── Input ─────────────────────────────────────────
            Surface(color = Color(0xFF111118), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(
                            when (selectedCat) {
                                "Analisis Musik" -> "Nama lagu / artis..."
                                "Lirik" -> "Tema lirik..."
                                "Suno Prompt" -> "Deskripsi musik..."
                                "Coding","Web Dev" -> "Apa yang ingin dibuat?"
                                "Image Prompt" -> "Deskripsi gambar..."
                                else -> "Tanya LyronixAi..."
                            }, color = Color.White.copy(0.3f), fontSize = 14.sp
                        )},
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputText.isNotBlank()) { viewModel.sendMessage(inputText, selectedCat); inputText = ""; keyboard?.hide(); focusManager.clearFocus() }
                        }),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF7C3AED),
                            unfocusedBorderColor = Color.White.copy(0.1f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFA78BFA),
                            unfocusedContainerColor = Color.White.copy(0.04f),
                    focusedContainerColor = Color.White.copy(0.06f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Box(
                        Modifier.size(48.dp).clip(CircleShape)
                            .background(if (inputText.isNotBlank() && !isTyping) Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFA78BFA))) else Brush.linearGradient(listOf(Color(0xFF2A2A3E), Color(0xFF2A2A3E))))
                            .clickable(enabled = inputText.isNotBlank() && !isTyping) {
                                viewModel.sendMessage(inputText, selectedCat)
                                inputText = ""
                                keyboard?.hide()
                                focusManager.clearFocus()
                            },
                        Alignment.Center
                    ) {
                        Icon(Icons.Default.Send, null, tint = if (inputText.isNotBlank() && !isTyping) Color.White else Color.White.copy(0.3f), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!msg.isUser) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFA78BFA)))).align(Alignment.Bottom), Alignment.Center) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(6.dp))
        }
        Surface(
            shape = RoundedCornerShape(
                topStart = if (msg.isUser) 16.dp else 4.dp,
                topEnd = if (msg.isUser) 4.dp else 16.dp,
                bottomStart = 16.dp, bottomEnd = 16.dp
            ),
            color = if (msg.isUser) Color(0xFF7C3AED) else Color(0xFF1A1A2E),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(Modifier.padding(12.dp, 10.dp)) {
                if (msg.hasImageResult && !msg.imageResultUrl.isNullOrBlank()) {
                    AsyncImage(model = msg.imageResultUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.height(6.dp))
                }
                if (msg.hasCode && !msg.codeSnippet.isNullOrBlank()) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF0D0D1A), modifier = Modifier.fillMaxWidth()) {
                        Text(msg.codeSnippet!!.take(600), color = Color(0xFF9ECAFF), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.padding(10.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(msg.text, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha by infiniteTransition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "alpha")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFA78BFA)))), Alignment.Center) {
            Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(4.dp))
        Surface(shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp), color = Color(0xFF1A1A2E)) {
            Row(Modifier.padding(14.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { i ->
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFA78BFA).copy(alpha = if (i == 1) alpha else 1f - alpha * 0.5f)))
                }
            }
        }
    }
}
