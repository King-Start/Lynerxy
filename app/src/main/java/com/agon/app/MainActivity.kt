package com.agon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.agon.app.ui.screens.*
import com.agon.app.ui.theme.AgonAppTheme
import com.agon.app.utils.MusicPlayerManager
import com.agon.app.viewmodel.MusicViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MusicPlayerManager.init(this)
        setContent {
            val darkTheme = isSystemInDarkTheme()
            AgonAppTheme(darkTheme = darkTheme) { MainApp() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val vm: MusicViewModel = viewModel()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSleepTimer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.initPlayer(context) }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val fullscreen = setOf("detail","login","lyrics","equalizer","alarm","downloads","settings")

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color(0xFF0F0F1A)) {
                Spacer(Modifier.height(32.dp))
                // Header
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Box(Modifier.size(40.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Icon(Icons.Default.MusicNote, null, tint = Color(0xFFA78BFA), modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("LyronixAi", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text("Music Player", color = Color.White.copy(0.4f), fontSize = 12.sp)
                    }
                }
                HorizontalDivider(color = Color.White.copy(0.08f), modifier = Modifier.padding(vertical = 12.dp))
                DrawerItem(Icons.Default.Download, "Downloads")     { scope.launch { drawerState.close() }; navController.navigate("downloads") }
                DrawerItem(Icons.Default.Equalizer, "Equalizer")   { scope.launch { drawerState.close() }; navController.navigate("equalizer") }
                DrawerItem(Icons.Default.Alarm, "Music Alarm")     { scope.launch { drawerState.close() }; navController.navigate("alarm") }
                DrawerItem(Icons.Default.Bedtime, "Sleep Timer")   { scope.launch { drawerState.close() }; showSleepTimer = true }
                DrawerItem(Icons.Default.Lyrics, "Lyrics View")    { scope.launch { drawerState.close() }; navController.navigate("lyrics") }
                DrawerItem(Icons.Default.Settings, "Settings")     { scope.launch { drawerState.close() }; navController.navigate("settings") }
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                if (currentRoute !in fullscreen) {
                    Column {
                        MiniPlayer(vm) { navController.navigate("detail") }
                        BottomNav(navController)
                    }
                }
            },
            contentWindowInsets = WindowInsets(0)
        ) { inner ->
            NavHost(navController, startDestination = "login", modifier = Modifier.fillMaxSize().padding(inner)) {
                composable("login") { LoginScreen { navController.navigate("discover") { popUpTo("login") { inclusive = true } } } }
                composable("discover") { DiscoverScreen(vm, { t -> vm.selectTrack(t); navController.navigate("detail") }, { scope.launch { drawerState.open() } }) }
                composable("chat")    { ChatScreen(vm) { scope.launch { drawerState.open() } } }
                composable("analyze") { AnalyzeScreen(vm) { scope.launch { drawerState.open() } } }
                composable("detail")  { DetailScreen(vm, { navController.popBackStack() }, { navController.navigate("lyrics") }) }
                composable("lyrics")  { LyricsScreen(vm) { navController.popBackStack() } }
                composable("settings") { SettingsScreen(vm) { navController.popBackStack() } }
                composable("equalizer") { EqualizerScreen(vm) { navController.popBackStack() } }
                composable("alarm")   { AlarmScreen(vm) { navController.popBackStack() } }
                composable("downloads") { DownloadScreen(vm) { navController.popBackStack() } }
            }
        }
    }

    if (showSleepTimer) SleepTimerDialog(vm) { showSleepTimer = false }
}

@Composable
fun DrawerItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, null) },
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent,
            unselectedIconColor = Color.White.copy(0.6f),
            unselectedTextColor = Color.White.copy(0.8f)
        )
    )
}

@Composable
fun BottomNav(navController: NavHostController) {
    val current = navController.currentBackStackEntryAsState().value?.destination?.route
    NavigationBar(containerColor = Color(0xFF0F0F1A), tonalElevation = 0.dp) {
        listOf(
            Triple("discover", Icons.Default.Search,     "Discover"),
            Triple("chat",     Icons.Default.AutoAwesome,"AI Chat"),
            Triple("analyze",  Icons.Default.Analytics,  "Analyze")
        ).forEach { (dest, icon, label) ->
            NavigationBarItem(
                icon = { Icon(icon, null) },
                label = { Text(label, fontSize = 10.sp) },
                selected = current == dest,
                onClick = { if (current != dest) navController.navigate(dest) { launchSingleTop = true } },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFA78BFA),
                    selectedTextColor = Color(0xFFA78BFA),
                    indicatorColor = Color(0xFF7C3AED).copy(0.2f),
                    unselectedIconColor = Color.White.copy(0.35f),
                    unselectedTextColor = Color.White.copy(0.35f)
                )
            )
        }
    }
}
