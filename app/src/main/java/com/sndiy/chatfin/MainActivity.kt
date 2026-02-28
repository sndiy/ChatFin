package com.sndiy.chatfin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sndiy.chatfin.core.ui.navigation.ChatFinNavGraph
import com.sndiy.chatfin.core.ui.navigation.Screen
import com.sndiy.chatfin.core.ui.theme.ChatFinTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatFinTheme {
                ChatFinMainScreen()
            }
        }
    }
}

@Composable
fun ChatFinMainScreen() {
    val navController = rememberNavController()

    Scaffold(
        modifier        = Modifier.fillMaxSize(),
        bottomBar       = { ChatFinBottomBar(navController) },
        contentWindowInsets = WindowInsets.navigationBars
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ChatFinNavGraph(navController)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Navigation Bar
// ─────────────────────────────────────────────────────────────────────────────

private data class NavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val defaultIcon: ImageVector,
    val isCenter: Boolean = false
)

private val bottomNavItems = listOf(
    NavItem(
        screen       = Screen.Chat,
        label        = "Chat",
        selectedIcon = Icons.Filled.Chat,
        defaultIcon  = Icons.Outlined.Chat
    ),
    NavItem(
        screen       = Screen.Dashboard,
        label        = "Dashboard",
        selectedIcon = Icons.Filled.Dashboard,
        defaultIcon  = Icons.Outlined.Dashboard
    ),
    NavItem(
        screen       = Screen.AddTransaction,
        label        = "Tambah",
        selectedIcon = Icons.Filled.Add,
        defaultIcon  = Icons.Filled.Add,
        isCenter     = true   // FAB di tengah
    ),
    NavItem(
        screen       = Screen.Analytics,
        label        = "Analitik",
        selectedIcon = Icons.Filled.BarChart,
        defaultIcon  = Icons.Outlined.BarChart
    ),
    NavItem(
        screen       = Screen.Settings,
        label        = "Setelan",
        selectedIcon = Icons.Filled.Settings,
        defaultIcon  = Icons.Outlined.Settings
    ),
)

@Composable
fun ChatFinBottomBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Screen yang menyembunyikan bottom bar (full screen)
    val hideBottomBarRoutes = setOf(
        Screen.CharacterBuilder.route,
        Screen.TransactionForm.route,
        Screen.AccountForm.route,
    )
    if (hideBottomBarRoutes.any { currentRoute?.startsWith(it.substringBefore("{")) == true }) return

    NavigationBar(
        tonalElevation = 3.dp
    ) {
        bottomNavItems.forEach { item ->
            if (item.isCenter) {
                // Tombol tengah: FAB menonjol
                NavigationBarItem(
                    selected = false,
                    onClick  = {
                        navController.navigate(item.screen.route) {
                            launchSingleTop = true
                        }
                    },
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            FloatingActionButton(
                                onClick         = {
                                    navController.navigate(item.screen.route) {
                                        launchSingleTop = true
                                    }
                                },
                                modifier        = Modifier.size(48.dp),
                                containerColor  = MaterialTheme.colorScheme.primary,
                                contentColor    = MaterialTheme.colorScheme.onPrimary,
                                elevation       = FloatingActionButtonDefaults.elevation(4.dp)
                            ) {
                                Icon(
                                    imageVector  = item.selectedIcon,
                                    contentDescription = item.label,
                                    modifier     = Modifier.size(24.dp)
                                )
                            }
                        }
                    },
                    label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                    alwaysShowLabel = true
                )
            } else {
                val isSelected = currentRoute == item.screen.route
                NavigationBarItem(
                    selected = isSelected,
                    onClick  = {
                        navController.navigate(item.screen.route) {
                            // Hindari stack menumpuk saat klik bottom nav
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    },
                    icon     = {
                        Icon(
                            imageVector        = if (isSelected) item.selectedIcon else item.defaultIcon,
                            contentDescription = item.label
                        )
                    },
                    label    = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                    alwaysShowLabel = true
                )
            }
        }
    }
}