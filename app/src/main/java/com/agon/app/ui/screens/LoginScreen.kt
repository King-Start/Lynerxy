package com.agon.app.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (auth.currentUser != null) onLoginSuccess()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(Exception::class.java)!!
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential).addOnCompleteListener { t ->
                    if (t.isSuccessful) { onLoginSuccess() }
                    else {
                        isLoading = false
                        Toast.makeText(context, "Login Gagal: ${t.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                isLoading = false
                Toast.makeText(context, "Google Sign In Failed", Toast.LENGTH_SHORT).show()
            }
        } else { isLoading = false }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0F0F1A), Color(0xFF1A0A2E), Color(0xFF0F0F1A)))
        ),
        contentAlignment = Alignment.Center
    ) {
        // Background decoration circles
        Box(Modifier.size(300.dp).offset((-80).dp, (-120).dp).clip(CircleShape)
            .background(Color(0xFF7C3AED).copy(alpha = 0.08f)))
        Box(Modifier.size(200.dp).offset(100.dp, 80.dp).clip(CircleShape)
            .background(Color(0xFF9ECAFF).copy(alpha = 0.06f)))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Logo
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFA78BFA)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(52.dp))
            }

            Spacer(Modifier.height(28.dp))

            Text("LyronixAi", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(6.dp))
            Text("Your AI-Powered Music Player", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, textAlign = TextAlign.Center)

            Spacer(Modifier.height(48.dp))

            // Feature pills
            listOf("🎵 YouTube Music", "🤖 AI Chat", "📥 Offline Download", "🎤 Synced Lyrics").forEach { feat ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(feat, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(40.dp))

            // Google Sign In
            Button(
                onClick = {
                    isLoading = true
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken("default_web_client_id")
                        .requestEmail().build()
                    launcher.launch(GoogleSignIn.getClient(context, gso).signInIntent)
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF7C3AED), strokeWidth = 2.dp)
                else {
                    Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Spacer(Modifier.width(12.dp))
                    Text("Continue with Google", color = Color(0xFF333333), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Skip login (guest)
            TextButton(onClick = onLoginSuccess) {
                Text("Continue as Guest", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
            }
        }
    }
}
