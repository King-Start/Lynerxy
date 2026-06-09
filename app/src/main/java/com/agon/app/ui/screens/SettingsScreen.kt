package com.agon.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val currentApiKey by viewModel.apiKey.collectAsState()
    val currentProvider by viewModel.apiProvider.collectAsState()
    val currentTheme by viewModel.themeMode.collectAsState()
    val apiValidation by viewModel.apiValidationResult.collectAsState()

    var apiKeyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var showKey by remember { mutableStateOf(false) }
    var expandedProvider by remember { mutableStateOf(false) }
    var selectedProvider by remember(currentProvider) { mutableStateOf(currentProvider) }
    val providers = listOf("OpenAI", "Claude", "Gemini", "DeepSeek", "GLM", "Kimi", "Mistral", "GoAPI")
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Theme ──────────────────────────────────────────
            Text("Tema Aplikasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark" to "🌙 Dark", "light" to "☀️ Light", "amoled" to "⬛ AMOLED").forEach { (mode, label) ->
                    FilterChip(
                        selected = currentTheme == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            HorizontalDivider()

            // ── API Config ─────────────────────────────────────
            Text("API Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Pilih AI Provider dan masukkan API Key lo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            ExposedDropdownMenuBox(expanded = expandedProvider, onExpandedChange = { expandedProvider = !expandedProvider }) {
                OutlinedTextField(
                    value = selectedProvider, onValueChange = {}, readOnly = true,
                    label = { Text("AI Provider") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvider) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = expandedProvider, onDismissRequest = { expandedProvider = false }) {
                    providers.forEach { p ->
                        DropdownMenuItem(text = { Text(p) }, onClick = { selectedProvider = p; expandedProvider = false })
                    }
                }
            }

            OutlinedTextField(
                value = apiKeyInput, onValueChange = { apiKeyInput = it },
                label = { Text("$selectedProvider API Key") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp), singleLine = true
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showKey, onCheckedChange = { showKey = it })
                Text("Tampilkan API Key", fontSize = 14.sp)
            }

            if (apiValidation != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) { Text(apiValidation!!, modifier = Modifier.padding(12.dp), fontSize = 13.sp) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (apiKeyInput.isBlank()) { Toast.makeText(context, "API Key kosong!", Toast.LENGTH_SHORT).show(); return@Button }
                        viewModel.saveApiConfig(apiKeyInput.trim(), selectedProvider)
                        Toast.makeText(context, "✅ Tersimpan! Provider: $selectedProvider", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(12.dp)
                ) { Text("Simpan", fontWeight = FontWeight.Bold) }

                OutlinedButton(
                    onClick = {
                        viewModel.saveApiConfig(apiKeyInput.trim(), selectedProvider)
                        viewModel.validateApiKey()
                    },
                    modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(12.dp)
                ) { Text("Test Key") }
            }

            HorizontalDivider()

            // ── Provider Info ──────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🔑 Cara dapet API Key GRATIS:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    listOf(
                        "Gemini → aistudio.google.com (GRATIS)",
                        "DeepSeek → platform.deepseek.com (MURAH)",
                        "Claude → console.anthropic.com",
                        "OpenAI → platform.openai.com",
                        "GLM → open.bigmodel.cn (GRATIS)",
                        "Kimi → platform.moonshot.cn",
                        "Mistral → console.mistral.ai (ada free tier)"
                    ).forEach { Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
