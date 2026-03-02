// app/src/main/java/com/sndiy/chatfin/MainActivity.kt
// ⚠️ TIMPA seluruh isi MainActivity.kt yang lama

package com.sndiy.chatfin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sndiy.chatfin.core.ui.navigation.ChatFinNavGraph
import com.sndiy.chatfin.core.ui.navigation.Screen
import com.sndiy.chatfin.core.ui.theme.ChatFinTheme
import com.sndiy.chatfin.feature.finance.account.ui.AccountSwitcherSheet
import com.sndiy.chatfin.feature.finance.account.ui.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatFinTheme {
                ChatFinAppContent()
            }
        }
    }
}

// ── Screen yang TIDAK menampilkan bottom nav ───────────────────────────────────
private val fullScreenRoutes = setOf(
    Screen.AccountForm.route,
    Screen.TransactionForm.route,
    Screen.CharacterBuilder.route,
    Screen.WalletForm.route,
    Screen.CategoryForm.route,
    Screen.BudgetForm.route,
    Screen.SavingsGoalForm.route
)

@Composable
fun ChatFinAppContent() {
    val navController    = rememberNavController()
    val accountViewModel = hiltViewModel<AccountViewModel>()
    val uiState          by accountViewModel.uiState.collectAsStateWithLifecycle()

    var showAccountSwitcher by remember { mutableStateOf(false) }

    val navBackStackEntry  by navController.currentBackStackEntryAsState()
    val currentRoute       = navBackStackEntry?.destination?.route
    val showBottomBar      = fullScreenRoutes.none { currentRoute?.startsWith(it.substringBefore("{")) == true }

    Scaffold(
        modifier  = Modifier.fillMaxSize(),
        topBar    = {
            // Sembunyikan TopBar saat di halaman full-screen
            if (showBottomBar) {
                ChatFinTopBar(
                    activeAccountName  = uiState.activeAccount?.name ?: "ChatFin",
                    activeAccountColor = uiState.activeAccount?.colorHex ?: "#0061A4",
                    onAccountClick     = { showAccountSwitcher = true }
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                ChatFinBottomBar(navController)
            }
        },
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

    if (showAccountSwitcher) {
        AccountSwitcherSheet(
            onDismiss               = { showAccountSwitcher = false },
            onNavigateToAllAccounts = {
                showAccountSwitcher = false
                navController.navigate(Screen.AccountList.route)
            },
            onNavigateToAddAccount  = {
                showAccountSwitcher = false
                navController.navigate(Screen.AccountForm.createRoute())
            },
            viewModel = accountViewModel
        )
    }
}

// ── Top App Bar ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatFinTopBar(
    activeAccountName: String,
    activeAccountColor: String,
    onAccountClick: () -> Unit
) {
    val accentColor = runCatching {
        Color(android.graphics.Color.parseColor(activeAccountColor))
    }.getOrElse { MaterialTheme.colorScheme.primary }

    TopAppBar(
        title = {
            Row(
                modifier              = Modifier.clickable(onClick = onAccountClick),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier         = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = activeAccountName.take(1).uppercase(),
                        style      = MaterialTheme.typography.labelMedium,
                        color      = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text       = activeAccountName,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector        = Icons.Default.ArrowDropDown,
                    contentDescription = "Ganti akun",
                    modifier           = Modifier.size(20.dp)
                )
            }
        },
        actions = {
            IconButton(onClick = { }) {
                Icon(Icons.Default.Notifications, contentDescription = "Notifikasi")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// ── Bottom Nav — 4 tab, tanpa Tambah ──────────────────────────────────────────
private data class NavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val defaultIcon: ImageVector
)

private val bottomNavItems = listOf(
    NavItem(Screen.Chat,      "Chat",      Icons.Filled.Chat,      Icons.Outlined.Chat),
    NavItem(Screen.Dashboard, "Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    NavItem(Screen.Analytics, "Analitik",  Icons.Filled.BarChart,  Icons.Outlined.BarChart),
    NavItem(Screen.Settings,  "Setelan",   Icons.Filled.Settings,  Icons.Outlined.Settings),
)

@Composable
fun ChatFinBottomBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute      = navBackStackEntry?.destination?.route

    NavigationBar(tonalElevation = 3.dp) {
        bottomNavItems.forEach { item ->
            val isSelected = currentRoute == item.screen.route
            NavigationBarItem(
                selected = isSelected,
                onClick  = {
                    navController.navigate(item.screen.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState    = true
                    }
                },
                icon = {
                    Icon(
                        imageVector        = if (isSelected) item.selectedIcon else item.defaultIcon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text     = item.label,
                        style    = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp
                    )
                }
            )
        }
    }
}