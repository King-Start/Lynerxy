package com.agon.app

import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.agon.app.ui.screens.LoginScreen
import com.agon.app.ui.screens.MiniPlayer
import com.agon.app.utils.MusicPlayerManager
import com.agon.app.ui.screens.SettingsScreen
import com.agon.app.ui.screens.AnalyzeScreen
import com.agon.app.ui.screens.ChatScreen
import com.agon.app.ui.screens.DetailScreen
import com.agon.app.ui.screens.DiscoverScreen
import com.agon.app.ui.theme.AgonAppTheme
import com.agon.app.viewmodel.MusicViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MusicPlayerManager.init(this)
        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }
            AgonAppTheme(darkTheme = isDarkTheme) {
                MainApp(
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(isDarkTheme: Boolean, onThemeToggle: () -> Unit) {
    val navController = rememberNavController()
    val musicViewModel: MusicViewModel = viewModel()
    val context = LocalContext.current
    LaunchedEffect(Unit) { musicViewModel.initPlayer(context) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Hidden YouTube Engine for Background Audio

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Lyronix Menu",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Settings (API Key)") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("settings")
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("LyronixAi") },
                    actions = {
                        IconButton(onClick = onThemeToggle) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                Column {
                    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                    if (currentRoute != "detail" && currentRoute != "login" && currentRoute != "settings") {
                        MiniPlayer(
                            viewModel = musicViewModel,
                            onExpand = { navController.navigate("detail") }
                        )
                    }
                    BottomNav(navController)
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "login",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                composable("login") {
                    LoginScreen(onLoginSuccess = {
                        navController.navigate("chat") {
                            popUpTo("login") { inclusive = true }
                        }
                    })
                }
                composable("chat") { 
                    ChatScreen(
                        viewModel = musicViewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } }
                    ) 
                }
                composable("discover") { 
                    DiscoverScreen(
                        viewModel = musicViewModel,
                        onTrackSelected = { track ->
                            musicViewModel.selectTrack(track)
                            navController.navigate("detail")
                        },
                        onOpenDrawer = { scope.launch { drawerState.open() } }
                    ) 
                }
                composable("analyze") { 
                    AnalyzeScreen(
                        viewModel = musicViewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } }
                    ) 
                }
                composable("detail") { 
                    DetailScreen(
                        viewModel = musicViewModel,
                        onBack = { navController.popBackStack() }
                    ) 
                }
                composable("settings") {
                    SettingsScreen(
                        viewModel = musicViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}



@Composable
fun BottomNav(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    if (currentRoute == "detail" || currentRoute == "login" || currentRoute == "settings") return

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
            label = { Text("LyronixAi") },
            selected = currentRoute == "chat",
            onClick = {
                if (currentRoute != "chat") {
                    navController.navigate("chat") { popUpTo("chat") { inclusive = true } }
                }
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Search, contentDescription = "Discover") },
            label = { Text("Discover") },
            selected = currentRoute == "discover",
            onClick = {
                if (currentRoute != "discover") {
                    navController.navigate("discover") { popUpTo("chat") }
                }
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Analytics, contentDescription = "Analyze") },
            label = { Text("Analyze") },
            selected = currentRoute == "analyze",
            onClick = {
                if (currentRoute != "analyze") {
                    navController.navigate("analyze") { popUpTo("chat") }
                }
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
