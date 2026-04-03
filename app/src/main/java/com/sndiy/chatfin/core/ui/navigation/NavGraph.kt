// app/src/main/java/com/sndiy/chatfin/core/ui/navigation/NavGraph.kt
//
// UX FIXES:
// BUG 1: FAB hanya di Dashboard + Riwayat (bukan Chat/Settings)
// BUG 2: ChatScreen punya Scaffold sendiri → outer padding harus tetap
//         tapi ChatInputBar navigationBarsPadding dihapus (fix di ChatComponents)

package com.sndiy.chatfin.core.ui.navigation

import androidx.activity.compose.BackHandler
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sndiy.chatfin.feature.chat.ui.ChatViewModel
import com.sndiy.chatfin.feature.chat.ui.ChatScreen
import com.sndiy.chatfin.feature.auth.ui.AuthScreen
import com.sndiy.chatfin.feature.finance.account.ui.AccountFormScreen
import com.sndiy.chatfin.feature.finance.account.ui.AccountListScreen
import com.sndiy.chatfin.feature.finance.budget.ui.BudgetScreen
import com.sndiy.chatfin.feature.export.ui.ExportScreen
import com.sndiy.chatfin.feature.finance.category.ui.CategoryScreen
import com.sndiy.chatfin.feature.finance.dashboard.ui.DashboardScreen
import com.sndiy.chatfin.feature.finance.transaction.ui.QuickAddSheet
import com.sndiy.chatfin.feature.finance.transaction.ui.QuickAddResult
import com.sndiy.chatfin.feature.finance.transaction.ui.TransactionFormScreen
import com.sndiy.chatfin.feature.finance.transaction.ui.TransactionListScreen
import com.sndiy.chatfin.feature.finance.transaction.ui.TransactionViewModel
import com.sndiy.chatfin.feature.finance.transaction.ui.WalletFormScreen
import com.sndiy.chatfin.feature.finance.transaction.ui.WalletListScreen
import com.sndiy.chatfin.feature.onboarding.ui.OnboardingScreen
import com.sndiy.chatfin.feature.onboarding.ui.OnboardingViewModel
import com.sndiy.chatfin.feature.settings.backup.DataBackupScreen
import com.sndiy.chatfin.feature.settings.ui.AboutScreen
import com.sndiy.chatfin.feature.settings.ui.SettingsScreen
import com.sndiy.chatfin.feature.settings.ui.SettingsThemeScreen
import com.sndiy.chatfin.feature.splash.ui.SplashScreen

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard.route,       "Beranda",  Icons.Default.Home),
    BottomNavItem(Screen.TransactionList.route,  "Riwayat",  Icons.Default.Receipt),
    BottomNavItem(Screen.Chat.route,            "Mai",      Icons.Default.AutoAwesome),
    BottomNavItem(Screen.Settings.route,        "Setelan",  Icons.Default.Settings),
)

private val bottomNavRoutes = bottomNavItems.map { it.route }.toSet()

// FIX BUG 1: FAB hanya di route yang butuh quick-add
private val fabVisibleRoutes = setOf(
    Screen.Dashboard.route,
    Screen.TransactionList.route
)

@Composable
fun PlaceholderScreen(name: String) {
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Construction, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = name, style = MaterialTheme.typography.titleMedium)
        Text("Akan hadir di fase berikutnya", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ChatFinNavGraph(
    navController: NavHostController,
    onFinish: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute      = navBackStackEntry?.destination?.route
    val showBottomBar     = currentRoute in bottomNavRoutes
    val showFab           = currentRoute in fabVisibleRoutes  // FIX BUG 1
    val isOnBottomNav     = currentRoute in bottomNavRoutes
    val chatViewModel: ChatViewModel = hiltViewModel()

    var showQuickAdd by remember { mutableStateOf(false) }
    val transactionViewModel: TransactionViewModel = hiltViewModel()
    val txListState by transactionViewModel.listState.collectAsStateWithLifecycle()

    BackHandler(enabled = isOnBottomNav) {
        when (currentRoute) {
            Screen.Dashboard.route -> onFinish()
            else -> navController.navigate(Screen.Dashboard.route) {
                popUpTo(Screen.Dashboard.route) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (showFab) {  // FIX BUG 1: bukan showBottomBar
                FloatingActionButton(
                    onClick = { showQuickAdd = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah Transaksi")
                }
            }
        },
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
            navController    = navController,
            startDestination = Screen.Splash.route,
            modifier         = Modifier.padding(padding),
            enterTransition  = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
            exitTransition   = { slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
            popExitTransition  = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) }
        ) {
            composable(route = Screen.Splash.route, exitTransition = { fadeOut(animationSpec = tween(400)) }) {
                SplashScreen(
                    onNavigateToDashboard  = { navController.navigate(Screen.Dashboard.route) { popUpTo(Screen.Splash.route) { inclusive = true } } },
                    onNavigateToOnboarding = { navController.navigate(Screen.Onboarding.route) { popUpTo(Screen.Splash.route) { inclusive = true } } }
                )
            }

            composable(Screen.Onboarding.route) {
                val onboardingVM: OnboardingViewModel = hiltViewModel()
                val isComplete by onboardingVM.isComplete.collectAsStateWithLifecycle()
                LaunchedEffect(isComplete) {
                    if (isComplete) navController.navigate(Screen.Dashboard.route) { popUpTo(Screen.Onboarding.route) { inclusive = true } }
                }
                OnboardingScreen(onComplete = { name, balance -> onboardingVM.setupAccount(name, balance) })
            }

            // ── Bottom Nav tabs ───────────────────────────────────────────────
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToChat   = { navController.navigate(Screen.Chat.route) },
                    onNavigateToBudget = { navController.navigate(Screen.BudgetList.route) }
                )
            }
            composable(Screen.Chat.route)           { ChatScreen(viewModel = chatViewModel) }
            composable(Screen.Settings.route)        { SettingsScreen(navController = navController) }
            composable(Screen.TransactionList.route) {
                TransactionListScreen(
                    onNavigateBack  = { navController.popBackStack() },
                    onNavigateToAdd = { navController.navigate(Screen.TransactionForm.route) }
                )
            }

            // ── Standalone screens ────────────────────────────────────────────
            composable(Screen.TransactionForm.route) { TransactionFormScreen(onNavigateBack = { navController.popBackStack() }) }
            composable(Screen.BudgetList.route)      { BudgetScreen(onNavigateBack = { navController.popBackStack() }) }
            composable(Screen.Export.route)           { ExportScreen(onNavigateBack = { navController.popBackStack() }) }

            composable(Screen.AccountList.route) {
                AccountListScreen(
                    onNavigateBack          = { navController.popBackStack() },
                    onNavigateToAddAccount  = { navController.navigate(Screen.AccountForm.createRoute()) },
                    onNavigateToEditAccount = { id -> navController.navigate(Screen.AccountForm.createRoute(id)) }
                )
            }
            composable(route = Screen.AccountForm.route, arguments = listOf(navArgument("accountId") { type = NavType.StringType; defaultValue = "new" })) { backStackEntry ->
                AccountFormScreen(accountId = backStackEntry.arguments?.getString("accountId") ?: "new", onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.WalletList.route) {
                WalletListScreen(onNavigateBack = { navController.popBackStack() }, onNavigateToAdd = { navController.navigate(Screen.WalletForm.createRoute()) })
            }
            composable(route = Screen.WalletForm.route, arguments = listOf(navArgument("walletId") { type = NavType.StringType; defaultValue = "new" })) {
                WalletFormScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.CategoryList.route)   { com.sndiy.chatfin.feature.finance.category.ui.CategoryScreen(onNavigateBack = { navController.popBackStack() }) }
            composable(Screen.SettingsTheme.route)   { SettingsThemeScreen(onNavigateBack = { navController.popBackStack() }) }
            composable(Screen.SettingsAbout.route)   { AboutScreen(onNavigateBack = { navController.popBackStack() }) }
            composable(Screen.SettingsBackup.route)  { DataBackupScreen(onNavigateBack = { navController.popBackStack() }, onNavigateToAuth = { navController.navigate(Screen.Auth.route) }, onLoggedOut = { navController.popBackStack() }) }
            composable(Screen.Auth.route)            { AuthScreen(onAuthSuccess = { navController.popBackStack() }, onSkip = { navController.popBackStack() }) }
        }
    }

    if (showQuickAdd) {
        QuickAddSheet(
            expenseCategories = txListState.expenseCategories,
            incomeCategories  = txListState.incomeCategories,
            wallets           = txListState.wallets,
            onSave            = { result ->
                transactionViewModel.quickAdd(type = result.type, amount = result.amount, categoryId = result.categoryId, walletId = result.walletId, note = result.note)
                showQuickAdd = false
            },
            onDismiss  = { showQuickAdd = false },
            onFullForm = { navController.navigate(Screen.TransactionForm.route) }
        )
    }
}
