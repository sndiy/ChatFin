package com.sndiy.chatfin.core.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.sndiy.chatfin.feature.chat.ui.ChatScreen
import com.sndiy.chatfin.feature.finance.account.ui.AccountFormScreen
import com.sndiy.chatfin.feature.finance.account.ui.AccountListScreen
import com.sndiy.chatfin.feature.finance.category.ui.CategoryScreen
import com.sndiy.chatfin.feature.finance.dashboard.ui.DashboardScreen
import com.sndiy.chatfin.feature.finance.transaction.ui.TransactionListScreen
import com.sndiy.chatfin.feature.finance.transaction.ui.WalletFormScreen
import com.sndiy.chatfin.feature.finance.transaction.ui.WalletListScreen
import com.sndiy.chatfin.feature.settings.ui.SettingsScreen

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard.route, "Beranda",  Icons.Default.Home),
    BottomNavItem(Screen.Chat.route,      "Chat Mai", Icons.Default.Chat),
    BottomNavItem(Screen.Settings.route,  "Setelan",  Icons.Default.Settings),
)

private val bottomNavRoutes = bottomNavItems.map { it.route }

@Composable
fun PlaceholderScreen(name: String) {
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Construction, null,
            modifier = Modifier.size(64.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = name, style = MaterialTheme.typography.titleMedium)
        Text(
            "Akan hadir di fase berikutnya",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ChatFinNavGraph(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute      = navBackStackEntry?.destination?.route
    val showBottomBar     = currentRoute in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick  = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon  = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController      = navController,
            startDestination   = Screen.Dashboard.route,
            modifier           = Modifier.padding(padding),
            enterTransition    = { slideInHorizontally(initialOffsetX = { it },      animationSpec = tween(300)) + fadeIn(animationSpec  = tween(300)) },
            exitTransition     = { slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec  = tween(300)) },
            popExitTransition  = { slideOutHorizontally(targetOffsetX = { it },      animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) }
        ) {
            // ── Bottom Nav ─────────────────────────────────────────────────────
            composable(Screen.Dashboard.route) {
                DashboardScreen(onNavigateToChat = { navController.navigate(Screen.Chat.route) })
            }
            composable(Screen.Chat.route) { ChatScreen() }
            composable(Screen.Settings.route) {
                SettingsScreen(navController = navController)
            }

            // ── Akun ───────────────────────────────────────────────────────────
            composable(Screen.AccountList.route) {
                AccountListScreen(
                    onNavigateBack          = { navController.popBackStack() },
                    onNavigateToAddAccount  = { navController.navigate(Screen.AccountForm.createRoute()) },
                    onNavigateToEditAccount = { id -> navController.navigate(Screen.AccountForm.createRoute(id)) }
                )
            }
            composable(
                route     = Screen.AccountForm.route,
                arguments = listOf(navArgument("accountId") {
                    type         = NavType.StringType
                    defaultValue = "new"
                })
            ) { back ->
                AccountFormScreen(
                    accountId      = back.arguments?.getString("accountId") ?: "new",
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // ── Transaksi ──────────────────────────────────────────────────────
            composable(Screen.TransactionList.route) {
                TransactionListScreen(
                    onNavigateBack  = { navController.popBackStack() },
                    onNavigateToAdd = { navController.navigate(Screen.Chat.route) }
                )
            }
            composable(
                route     = Screen.TransactionDetail.route,
                arguments = listOf(navArgument("transactionId") { type = NavType.StringType })
            ) { PlaceholderScreen("Detail Transaksi") }

            // ── Dompet ─────────────────────────────────────────────────────────
            composable(Screen.WalletList.route) {
                WalletListScreen(
                    onNavigateBack  = { navController.popBackStack() },
                    onNavigateToAdd = { navController.navigate(Screen.WalletForm.createRoute()) }
                )
            }
            composable(
                route     = Screen.WalletForm.route,
                arguments = listOf(navArgument("walletId") {
                    type         = NavType.StringType
                    defaultValue = "new"
                })
            ) {
                WalletFormScreen(onNavigateBack = { navController.popBackStack() })
            }

            // ── Kategori ───────────────────────────────────────────────────────
            composable(Screen.CategoryList.route) {
                CategoryScreen(onNavigateBack = { navController.popBackStack() })
            }

            // ── Setelan sub-halaman ────────────────────────────────────────────
            composable(Screen.SettingsTheme.route)   { PlaceholderScreen("Tema") }
            composable(Screen.SettingsBackup.route)  { PlaceholderScreen("Backup & Restore") }
            composable(Screen.SettingsAbout.route)   { PlaceholderScreen("Tentang ChatFin") }

            // ── Placeholder fitur mendatang ────────────────────────────────────
            composable(Screen.Analytics.route)       { PlaceholderScreen("Analitik") }
            composable(Screen.BudgetList.route)      { PlaceholderScreen("Budget") }
            composable(Screen.SavingsGoalList.route) { PlaceholderScreen("Tabungan") }
        }
    }
}