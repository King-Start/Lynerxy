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
            AgonAppTheme(darkTheme = true) { MainApp() }
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
    val fullscreen = setOf("detail", "login", "lyrics", "equalizer", "alarm", "downloads", "settings")

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color(0xFF0D0D1A)) {
                Spacer(Modifier.statusBarsPadding().height(16.dp))
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, null, tint = Color(0xFFA78BFA), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("LyronixAi", style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
                HorizontalDivider(color = Color.White.copy(0.07f), modifier = Modifier.padding(vertical = 10.dp))
                DrawerItem(Icons.Default.Download,  "Downloads")  { scope.launch { drawerState.close() }; navController.navigate("downloads") }
                DrawerItem(Icons.Default.Equalizer, "Equalizer")  { scope.launch { drawerState.close() }; navController.navigate("equalizer") }
                DrawerItem(Icons.Default.Alarm,     "Music Alarm") { scope.launch { drawerState.close() }; navController.navigate("alarm") }
                DrawerItem(Icons.Default.Bedtime,   "Sleep Timer") { scope.launch { drawerState.close() }; showSleepTimer = true }
                DrawerItem(Icons.Default.Lyrics,    "Lyrics")      { scope.launch { drawerState.close() }; navController.navigate("lyrics") }
                DrawerItem(Icons.Default.Settings,  "Settings")    { scope.launch { drawerState.close() }; navController.navigate("settings") }
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
            containerColor = Color(0xFF0A0A0F),
            contentWindowInsets = WindowInsets(0)
        ) { inner ->
            NavHost(
                navController,
                startDestination = "discover",
                modifier = Modifier.fillMaxSize().padding(inner)
            ) {
                composable("login") {
                    LoginScreen { navController.navigate("discover") { popUpTo("login") { inclusive = true } } }
                }
                composable("discover") {
                    DiscoverScreen(vm, { t -> vm.selectTrack(t); navController.navigate("detail") }) { scope.launch { drawerState.open() } }
                }
                composable("chat")    { ChatScreen(vm) { scope.launch { drawerState.open() } } }
                composable("analyze") { AnalyzeScreen(vm) { scope.launch { drawerState.open() } } }
                composable("detail")  { DetailScreen(vm, { navController.popBackStack() }, { navController.navigate("lyrics") }) }
                composable("lyrics")  { LyricsScreen(vm) { navController.popBackStack() } }
                composable("settings")  { SettingsScreen(vm) { navController.popBackStack() } }
                composable("equalizer") { EqualizerScreen(vm) { navController.popBackStack() } }
                composable("alarm")     { AlarmScreen(vm) { navController.popBackStack() } }
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
            unselectedTextColor = Color.White.copy(0.85f)
        )
    )
}

@Composable
fun BottomNav(navController: NavHostController) {
    val current = navController.currentBackStackEntryAsState().value?.destination?.route
    NavigationBar(
        containerColor = Color(0xFF0D0D1A),
        tonalElevation = 0.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        listOf(
            Triple("discover", Icons.Default.Search,      "Discover"),
            Triple("chat",     Icons.Default.AutoAwesome, "AI Chat"),
            Triple("analyze",  Icons.Default.Analytics,   "Analyze")
        ).forEach { (dest, icon, label) ->
            NavigationBarItem(
                icon  = { Icon(icon, null) },
                label = { Text(label, fontSize = 10.sp) },
                selected = current == dest,
                onClick = { if (current != dest) navController.navigate(dest) { launchSingleTop = true } },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = Color(0xFFA78BFA),
                    selectedTextColor   = Color(0xFFA78BFA),
                    indicatorColor      = Color(0xFF7C3AED).copy(0.2f),
                    unselectedIconColor = Color.White.copy(0.35f),
                    unselectedTextColor = Color.White.copy(0.35f)
                )
            )
        }
    }
}
