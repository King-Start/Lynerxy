package com.agon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.eq.data.EQProfileRepository
import com.agon.app.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val active  by viewModel.activeEqProfile.collectAsState()
    val presets = EQProfileRepository.PRESETS
    val custom  by viewModel.eqProfileRepo.profiles.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Equalizer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF12121A),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0A0A0F)
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // EQ on/off toggle
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Equalizer", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Switch(
                    checked = active != null,
                    onCheckedChange = { on ->
                        if (on) viewModel.applyEqProfile(presets.first())
                        else viewModel.disableEq()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF7C4DFF), checkedTrackColor = Color(0x557C4DFF))
                )
            }

            if (active != null) {
                // Presets
                Text("Presets", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { preset ->
                        val sel = active?.id == preset.id
                        FilterChip(
                            selected = sel,
                            onClick = { viewModel.applyEqProfile(preset) },
                            label = { Text(preset.name, fontSize = 13.sp) },
                            leadingIcon = if (sel) {{ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }} else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF7C4DFF),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E1E2E),
                                labelColor = Color.White.copy(alpha = 0.7f)
                            )
                        )
                    }
                }

                if (custom.isNotEmpty()) {
                    Text("Custom", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(custom) { profile ->
                            val sel = active?.id == profile.id
                            FilterChip(
                                selected = sel,
                                onClick = { viewModel.applyEqProfile(profile) },
                                label = { Text(profile.name, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF03DAC6),
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color(0xFF1E1E2E),
                                    labelColor = Color.White.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                }

                // EQ Visualizer (simple bar sliders untuk 5 band utama)
                Text("Bands", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                val bands = listOf(60 to "60Hz", 250 to "250Hz", 1000 to "1kHz", 4000 to "4kHz", 14000 to "14kHz")
                val activeBands = active?.bands?.associate { it.frequency.toInt() to it.gain.toFloat() } ?: emptyMap()

                Row(
                    Modifier.fillMaxWidth().height(180.dp).background(Color(0xFF1E1E2E), shape = MaterialTheme.shapes.medium).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bands.forEach { (freq, label) ->
                        val gainDb = activeBands[freq] ?: 0f
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(40.dp)
                        ) {
                            Text(
                                "${if (gainDb >= 0) "+" else ""}${gainDb.toInt()}dB",
                                color = Color(0xFF7C4DFF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = gainDb,
                                onValueChange = {},
                                valueRange = -12f..12f,
                                modifier = Modifier
                                    .height(120.dp)
                                    .width(40.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF7C4DFF),
                                    activeTrackColor = Color(0xFF7C4DFF),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                )
                            )
                            Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                        }
                    }
                }

                // Preamp
                val preamp = active?.preamp?.toFloat() ?: 0f
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Preamp", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.width(70.dp))
                    Slider(
                        value = preamp,
                        onValueChange = {},
                        valueRange = -12f..12f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF03DAC6),
                            activeTrackColor = Color(0xFF03DAC6)
                        )
                    )
                    Text("${if (preamp >= 0) "+" else ""}${preamp.toInt()}dB", color = Color(0xFF03DAC6), fontSize = 12.sp, modifier = Modifier.width(40.dp))
                }
            }
        }
    }
}
