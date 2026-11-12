package com.agon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.agon.app.viewmodel.MusicViewModel

@Composable
fun SleepTimerDialog(viewModel: MusicViewModel, onDismiss: () -> Unit) {
    val isActive   by viewModel.sleepTimerActive.collectAsState()
    val remaining  by viewModel.sleepTimerRemaining.collectAsState()
    var fadeOut    by remember { mutableStateOf(false) }
    var endOfSong  by remember { mutableStateOf(false) }

    val presets = listOf(5, 10, 15, 20, 30, 45, 60, 90)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1A1A2E),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Bedtime, null, tint = Color(0xFF7C4DFF))
                        Text("Sleep Timer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.6f))
                    }
                }

                if (isActive && remaining >= 0) {
                    val min = remaining / 60000
                    val sec = (remaining % 60000) / 1000
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF7C4DFF).copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Timer aktif", color = Color(0xFF7C4DFF), fontSize = 12.sp)
                            Text("%02d:%02d".format(min, sec), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.cancelSleepTimer(); onDismiss() },
                                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                            ) { Text("Cancel Timer") }
                        }
                    }
                }

                // Options
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = endOfSong, onCheckedChange = { endOfSong = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF7C4DFF)))
                        Text("Stop after this song", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = fadeOut, onCheckedChange = { fadeOut = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF7C4DFF)))
                    Text("Fade out last minute", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }

                // Preset buttons
                Text("Minutes", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(100.dp)
                ) {
                    items(presets) { min ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF2A2A3E),
                            modifier = Modifier
                                .aspectRatio(1.4f)
                                .clickable {
                                    viewModel.setSleepTimer(min, endOfSong, fadeOut)
                                    onDismiss()
                                }
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Text("$min", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            }
                        }
                    }
                }

                if (!isActive) {
                    // End of song only
                    Button(
                        onClick = { viewModel.setSleepTimer(-1, stopAfterCurrentSong = true, fadeOut = fadeOut); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                    ) { Text("End of Song") }
                }
            }
        }
    }
}
