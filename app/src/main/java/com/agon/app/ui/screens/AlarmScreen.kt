package com.agon.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.agon.app.alarm.MusicAlarmEntry
import com.agon.app.viewmodel.MusicViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val alarms by viewModel.alarms.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Music Alarm", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF12121A), titleContentColor = Color.White, navigationIconContentColor = Color.White),
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, null, tint = Color(0xFF7C4DFF))
                    }
                }
            )
        },
        containerColor = Color(0xFF0A0A0F),
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = Color(0xFF7C4DFF)) {
                Icon(Icons.Default.Add, null, tint = Color.White)
            }
        }
    ) { padding ->
        if (alarms.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Alarm, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    Text("No alarms yet", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp)
                    Text("Tap + to add a music alarm", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(alarms) { alarm ->
                    AlarmCard(alarm, onToggle = { viewModel.toggleAlarm(alarm.id) }, onDelete = { viewModel.deleteAlarm(alarm.id) })
                }
            }
        }
    }

    if (showAdd) {
        AddAlarmDialog(
            onDismiss = { showAdd = false },
            onAdd = { entry -> viewModel.addAlarm(entry); showAdd = false }
        )
    }
}

@Composable
fun AlarmCard(alarm: MusicAlarmEntry, onToggle: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1A1A2E),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "%02d:%02d".format(alarm.hour, alarm.minute),
                    color = if (alarm.enabled) Color.White else Color.White.copy(alpha = 0.4f),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Light
                )
                if (alarm.label.isNotBlank())
                    Text(alarm.label, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                if (alarm.nextTriggerAt > 0) {
                    val diff = alarm.nextTriggerAt - System.currentTimeMillis()
                    val h = diff / 3_600_000; val m = (diff % 3_600_000) / 60_000
                    Text("in ${h}h ${m}m", color = Color(0xFF7C4DFF), fontSize = 11.sp)
                }
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF7C4DFF), checkedTrackColor = Color(0x557C4DFF))
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = Color.White.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
fun AddAlarmDialog(onDismiss: () -> Unit, onAdd: (MusicAlarmEntry) -> Unit) {
    var hour   by remember { mutableStateOf(7) }
    var minute by remember { mutableStateOf(0) }
    var label  by remember { mutableStateOf("") }
    var random by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF1A1A2E), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("New Alarm", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)

                // Time picker row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    // Hour
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { hour = (hour + 1) % 24 }) { Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White) }
                        Text("%02d".format(hour), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light)
                        IconButton(onClick = { hour = (hour - 1 + 24) % 24 }) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White) }
                    }
                    Text(":", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 8.dp))
                    // Minute
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { minute = (minute + 1) % 60 }) { Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White) }
                        Text("%02d".format(minute), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light)
                        IconButton(onClick = { minute = (minute - 1 + 60) % 60 }) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White) }
                    }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF7C4DFF), focusedLabelColor = Color(0xFF7C4DFF), cursorColor = Color(0xFF7C4DFF), unfocusedTextColor = Color.White, focusedTextColor = Color.White)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = random, onCheckedChange = { random = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF7C4DFF)))
                    Text("Random song from library", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) { Text("Cancel") }
                    Button(
                        onClick = { onAdd(MusicAlarmEntry(id = UUID.randomUUID().toString(), hour = hour, minute = minute, label = label, randomSong = random)) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                    ) { Text("Save") }
                }
            }
        }
    }
}
