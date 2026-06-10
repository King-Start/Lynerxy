package com.agon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    val navController   = rememberNavController()
    val vm: MusicViewModel = viewModel()
    val context         = LocalContext.current
    val drawerState     = rememberDrawerState(DrawerValue.Closed)
    val scope           = rememberCoroutineScope()
    val themeMode       by vm.themeMode.collectAsState()
    var showSleepTimer  by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.initPlayer(context) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Spacer(Modifier.height(24.dp))
                Text("LyronixAi", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                DrawerItem(Icons.Default.Download, "Downloads") { scope.launch { drawerState.close() }; navController.navigate("downloads") }
                DrawerItem(Icons.Default.Equalizer, "Equalizer") { scope.launch { drawerState.close() }; navController.navigate("equalizer") }
                DrawerItem(Icons.Default.Alarm, "Music Alarm") { scope.launch { drawerState.close() }; navController.navigate("alarm") }
                DrawerItem(Icons.Default.Bedtime, "Sleep Timer") { scope.launch { drawerState.close() }; showSleepTimer = true }
                DrawerItem(Icons.Default.Settings, "Settings") { scope.launch { drawerState.close() }; navController.navigate("settings") }
            }
        }
    ) {
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        val fullscreenRoutes = setOf("detail", "login", "lyrics", "equalizer", "alarm", "downloads")

        Scaffold(
            topBar = {
                if (currentRoute !in fullscreenRoutes) {
                    CenterAlignedTopAppBar(
                        title = { Text("LyronixAi") },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, null)
                            }
                        },
                        actions = {
                            IconButton(onClick = { showSleepTimer = true }) {
                                Icon(Icons.Default.Bedtime, null)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            },
            bottomBar = {
                Column {
                    if (currentRoute !in fullscreenRoutes) {
                        MiniPlayer(viewModel = vm, onExpand = { navController.navigate("detail") })
                        BottomNav(navController)
                    }
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { inner ->
            NavHost(
                navController = navController,
                startDestination = "login",
                modifier = Modifier.fillMaxSize().padding(inner)
            ) {
                composable("login") {
                    LoginScreen(onLoginSuccess = {
                        navController.navigate("chat") { popUpTo("login") { inclusive = true } }
                    })
                }
                composable("chat") {
                    ChatScreen(vm, onOpenDrawer = { scope.launch { drawerState.open() } })
                }
                composable("discover") {
                    DiscoverScreen(vm,
                        onTrackSelected = { track -> vm.selectTrack(track); navController.navigate("detail") },
                        onOpenDrawer = { scope.launch { drawerState.open() } }
                    )
                }
                composable("analyze") {
                    AnalyzeScreen(vm, onOpenDrawer = { scope.launch { drawerState.open() } })
                }
                composable("detail") {
                    DetailScreen(
                        viewModel = vm,
                        onBack = { navController.popBackStack() },
                        onLyrics = { navController.navigate("lyrics") }
                    )
                }
                composable("lyrics") {
                    LyricsScreen(vm, onBack = { navController.popBackStack() })
                }
                composable("settings") {
                    SettingsScreen(vm, onBack = { navController.popBackStack() })
                }
                composable("equalizer") {
                    EqualizerScreen(vm, onBack = { navController.popBackStack() })
                }
                composable("alarm") {
                    AlarmScreen(vm, onBack = { navController.popBackStack() })
                }
                composable("downloads") {
                    DownloadScreen(vm, onBack = { navController.popBackStack() })
                }
            }
        }
    }

    if (showSleepTimer) {
        SleepTimerDialog(vm, onDismiss = { showSleepTimer = false })
    }
}

@Composable
fun DrawerItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, null) },
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Composable
fun BottomNav(navController: NavHostController) {
    val current by navController.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val fullscreenRoutes = setOf("detail", "login", "lyrics", "equalizer", "alarm", "downloads", "settings")
    if (route in fullscreenRoutes) return

    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        listOf(
            Triple("chat",     Icons.Default.Chat,      "LyronixAi"),
            Triple("discover", Icons.Default.Search,    "Discover"),
            Triple("analyze",  Icons.Default.Analytics, "Analyze")
        ).forEach { (dest, icon, label) ->
            NavigationBarItem(
                icon = { Icon(icon, null) },
                label = { Text(label) },
                selected = route == dest,
                onClick = {
                    if (route != dest) navController.navigate(dest) { popUpTo("chat") }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
