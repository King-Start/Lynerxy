package com.agon.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.agon.app.ui.theme.ThemeState
import com.agon.app.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val apiKey       by viewModel.apiKey.collectAsState()
    val apiProvider  by viewModel.apiProvider.collectAsState()
    val apiValidation by viewModel.apiValidationResult.collectAsState()
    val themeMode    by viewModel.themeMode.collectAsState()
    val activeEq     by viewModel.activeEqProfile.collectAsState()

    var apiKeyInput      by remember(apiKey) { mutableStateOf(apiKey) }
    var showKey          by remember { mutableStateOf(false) }
    var selectedProvider by remember(apiProvider) { mutableStateOf(apiProvider) }
    var expandProvider   by remember { mutableStateOf(false) }
    var pureBlack        by remember { mutableStateOf(ThemeState.pureBlack) }

    val providers = listOf("OpenAI","Claude","Gemini","DeepSeek","GLM","Kimi","Mistral","GoAPI")
    val user = FirebaseAuth.getInstance().currentUser

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F0F), titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        },
        containerColor = Color(0xFF0A0A0F)
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Account ────────────────────────────────────────
            SettingsSection("Account") {
                if (user != null) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF7C3AED)), Alignment.Center) {
                            Text(user.displayName?.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(user.displayName ?: "User", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(user.email ?: "", color = Color.White.copy(0.5f), fontSize = 12.sp)
                        }
                        TextButton(onClick = { FirebaseAuth.getInstance().signOut() }) {
                            Text("Sign Out", color = Color(0xFFFF5252))
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = Color.White.copy(0.5f))
                        Spacer(Modifier.width(12.dp))
                        Text("Guest mode", color = Color.White.copy(0.6f))
                    }
                }
            }

            // ── Appearance ─────────────────────────────────────
            SettingsSection("Appearance") {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Theme", color = Color.White.copy(0.6f), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Dark","Light","System").forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF7C3AED),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF2A2A3E),
                                    labelColor = Color.White.copy(0.7f)
                                )
                            )
                        }
                    }
                }
                SettingsDivider()
                SettingsToggle(Icons.Default.Contrast, "Pure Black Background", pureBlack) {
                    pureBlack = it
                    ThemeState.pureBlack = it
                }
            }

            // ── AI API ─────────────────────────────────────────
            SettingsSection("AI Configuration") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Provider dropdown
                    ExposedDropdownMenuBox(expanded = expandProvider, onExpandedChange = { expandProvider = it }) {
                        OutlinedTextField(
                            value = selectedProvider,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("AI Provider") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandProvider) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(expanded = expandProvider, onDismissRequest = { expandProvider = false }, containerColor = Color(0xFF1E1E2E)) {
                            providers.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p, color = Color.White) },
                                    onClick = { selectedProvider = p; expandProvider = false }
                                )
                            }
                        }
                    }
                    // API Key
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("API Key") },
                        placeholder = { Text("sk-...", color = Color.White.copy(0.3f)) },
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = Color.White.copy(0.5f))
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = outlinedTextFieldColors()
                    )
                    // Provider links
                    Text("Free APIs: Gemini → aistudio.google.com | DeepSeek → platform.deepseek.com", color = Color.White.copy(0.35f), fontSize = 11.sp)
                    // Buttons
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.validateApiKey() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA78BFA))
                        ) { Text("Validate") }
                        Button(
                            onClick = { viewModel.saveApiConfig(apiKeyInput, selectedProvider) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                        ) { Text("Save") }
                    }
                    // Validation result
                    if (!apiValidation.isNullOrBlank()) {
                        Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(0.06f), modifier = Modifier.fillMaxWidth()) {
                            Text(apiValidation!!, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }

            // ── Playback ───────────────────────────────────────
            SettingsSection("Playback") {
                SettingsItem(Icons.Default.Equalizer, "Active EQ Profile") {
                    Text(activeEq?.name ?: "Off", color = Color(0xFFA78BFA), fontSize = 13.sp)
                }
                SettingsDivider()
                SettingsItem(Icons.Default.HighQuality, "Audio Quality") {
                    Text("Best Available", color = Color.White.copy(0.5f), fontSize = 13.sp)
                }
                SettingsDivider()
                SettingsItem(Icons.Default.CenterFocusStrong, "Stream Source") {
                    Text("YouTube Music", color = Color.White.copy(0.5f), fontSize = 13.sp)
                }
            }

            // ── Storage ────────────────────────────────────────
            SettingsSection("Storage") {
                SettingsItem(Icons.Default.FolderOpen, "Download Location") {
                    Text("Internal Storage", color = Color.White.copy(0.5f), fontSize = 13.sp)
                }
                SettingsDivider()
                SettingsItem(Icons.Default.Delete, "Clear Cache") {
                    TextButton(onClick = {}) { Text("Clear", color = Color(0xFFFF5252), fontSize = 13.sp) }
                }
            }

            // ── About ──────────────────────────────────────────
            SettingsSection("About") {
                SettingsItem(Icons.Default.Info, "Version") {
                    Text("1.0.0", color = Color.White.copy(0.5f), fontSize = 13.sp)
                }
                SettingsDivider()
                SettingsItem(Icons.Default.Code, "Powered by") {
                    Text("LyronixAi + RythimMusic", color = Color.White.copy(0.5f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(), color = Color(0xFFA78BFA), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF141428), modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun SettingsItem(icon: ImageVector, label: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SettingsToggle(icon: ImageVector, label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF7C3AED), checkedTrackColor = Color(0x557C3AED)))
    }
}

@Composable
fun SettingsDivider() = HorizontalDivider(color = Color.White.copy(0.06f), modifier = Modifier.padding(horizontal = 16.dp))

@Composable
fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFFA78BFA),
    unfocusedBorderColor = Color.White.copy(0.15f),
    focusedLabelColor = Color(0xFFA78BFA),
    unfocusedLabelColor = Color.White.copy(0.4f),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Color(0xFFA78BFA)
)
