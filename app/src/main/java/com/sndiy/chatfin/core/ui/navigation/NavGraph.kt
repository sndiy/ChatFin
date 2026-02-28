package com.sndiy.chatfin.core.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

// ─────────────────────────────────────────────────────────────────────────────
// NavRoutes — semua route dalam satu tempat, hindari string literal tersebar
// ─────────────────────────────────────────────────────────────────────────────
sealed class Screen(val route: String) {

    // ── Bottom Nav (5 tab utama) ──────────────────────────────────────────────
    data object Chat            : Screen("chat")
    data object Dashboard       : Screen("dashboard")
    data object AddTransaction  : Screen("add_transaction")
    data object Analytics       : Screen("analytics")
    data object Settings        : Screen("settings")

    // ── Chat Feature ─────────────────────────────────────────────────────────
    data object CharacterList   : Screen("character_list")
    data object CharacterBuilder: Screen("character_builder/{characterId}") {
        fun createRoute(characterId: String = "new") =
            "character_builder/$characterId"
    }

    // ── Finance: Account ─────────────────────────────────────────────────────
    data object AccountList     : Screen("account_list")
    data object AccountForm     : Screen("account_form/{accountId}") {
        fun createRoute(accountId: String = "new") =
            "account_form/$accountId"
    }

    // ── Finance: Transaction ──────────────────────────────────────────────────
    data object TransactionList : Screen("transaction_list")
    data object TransactionForm : Screen("transaction_form/{transactionId}") {
        fun createRoute(transactionId: String = "new") =
            "transaction_form/$transactionId"
    }
    data object TransactionDetail: Screen("transaction_detail/{transactionId}") {
        fun createRoute(transactionId: String) =
            "transaction_detail/$transactionId"
    }

    // ── Finance: Wallet ────────────────────────────────────────────────────────
    data object WalletList      : Screen("wallet_list")
    data object WalletForm      : Screen("wallet_form/{walletId}") {
        fun createRoute(walletId: String = "new") =
            "wallet_form/$walletId"
    }

    // ── Finance: Category ─────────────────────────────────────────────────────
    data object CategoryList    : Screen("category_list")
    data object CategoryForm    : Screen("category_form/{categoryId}") {
        fun createRoute(categoryId: String = "new") =
            "category_form/$categoryId"
    }

    // ── Finance: Budget ────────────────────────────────────────────────────────
    data object BudgetList      : Screen("budget_list")
    data object BudgetForm      : Screen("budget_form/{budgetId}") {
        fun createRoute(budgetId: String = "new") =
            "budget_form/$budgetId"
    }

    // ── Finance: Savings Goal ──────────────────────────────────────────────────
    data object SavingsGoalList : Screen("savings_goal_list")
    data object SavingsGoalForm : Screen("savings_goal_form/{goalId}") {
        fun createRoute(goalId: String = "new") =
            "savings_goal_form/$goalId"
    }

    // ── Overview All Accounts ──────────────────────────────────────────────────
    data object OverviewAllAccounts : Screen("overview_all_accounts")

    // ── Settings Sub-screens ───────────────────────────────────────────────────
    data object SettingsApiKey      : Screen("settings_api_key")
    data object SettingsTheme       : Screen("settings_theme")
    data object SettingsBackup      : Screen("settings_backup")
    data object SettingsSecurity    : Screen("settings_security")
    data object SettingsAbout       : Screen("settings_about")
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Navigation Items
// ─────────────────────────────────────────────────────────────────────────────
data class BottomNavItem(
    val screen: Screen,
    val labelRes: Int,
    val iconSelected: androidx.compose.ui.graphics.vector.ImageVector,
    val iconDefault: androidx.compose.ui.graphics.vector.ImageVector,
    val isCenter: Boolean = false   // tombol tengah ➕ menonjol
)

// ─────────────────────────────────────────────────────────────────────────────
// NavGraph — graph navigasi utama aplikasi
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ChatFinNavGraph(navController: NavHostController) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Dashboard.route,
        enterTransition  = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec  = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition   = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 3 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 3 },
                animationSpec  = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        // ── Bottom Nav Screens ─────────────────────────────────────────────
        composable(Screen.Chat.route) {
            // ChatScreen(navController)
        }
        composable(Screen.Dashboard.route) {
            // DashboardScreen(navController)
        }
        composable(Screen.AddTransaction.route) {
            // TransactionFormScreen(navController)
        }
        composable(Screen.Analytics.route) {
            // AnalyticsScreen(navController)
        }
        composable(Screen.Settings.route) {
            // SettingsScreen(navController)
        }

        // ── Character ──────────────────────────────────────────────────────
        composable(Screen.CharacterList.route) {
            // CharacterListScreen(navController)
        }
        composable(
            route     = Screen.CharacterBuilder.route,
            arguments = listOf(navArgument("characterId") {
                type         = NavType.StringType
                defaultValue = "new"
            })
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getString("characterId") ?: "new"
            // CharacterBuilderScreen(navController, characterId)
        }

        // ── Account ────────────────────────────────────────────────────────
        composable(Screen.AccountList.route) {
            // AccountListScreen(navController)
        }
        composable(
            route     = Screen.AccountForm.route,
            arguments = listOf(navArgument("accountId") {
                type         = NavType.StringType
                defaultValue = "new"
            })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId") ?: "new"
            // AccountFormScreen(navController, accountId)
        }

        // ── Transaction ────────────────────────────────────────────────────
        composable(Screen.TransactionList.route) {
            // TransactionListScreen(navController)
        }
        composable(
            route     = Screen.TransactionForm.route,
            arguments = listOf(navArgument("transactionId") {
                type         = NavType.StringType
                defaultValue = "new"
            })
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getString("transactionId") ?: "new"
            // TransactionFormScreen(navController, transactionId)
        }
        composable(
            route     = Screen.TransactionDetail.route,
            arguments = listOf(navArgument("transactionId") {
                type = NavType.StringType
            })
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
            // TransactionDetailScreen(navController, transactionId)
        }

        // ── Wallet ─────────────────────────────────────────────────────────
        composable(Screen.WalletList.route) { }
        composable(Screen.WalletForm.route,
            arguments = listOf(navArgument("walletId") {
                type = NavType.StringType; defaultValue = "new"
            })
        ) { }

        // ── Category ───────────────────────────────────────────────────────
        composable(Screen.CategoryList.route) { }
        composable(Screen.CategoryForm.route,
            arguments = listOf(navArgument("categoryId") {
                type = NavType.StringType; defaultValue = "new"
            })
        ) { }

        // ── Budget ─────────────────────────────────────────────────────────
        composable(Screen.BudgetList.route) { }
        composable(Screen.BudgetForm.route,
            arguments = listOf(navArgument("budgetId") {
                type = NavType.StringType; defaultValue = "new"
            })
        ) { }

        // ── Savings Goal ───────────────────────────────────────────────────
        composable(Screen.SavingsGoalList.route) { }
        composable(Screen.SavingsGoalForm.route,
            arguments = listOf(navArgument("goalId") {
                type = NavType.StringType; defaultValue = "new"
            })
        ) { }

        // ── Overview All Accounts ──────────────────────────────────────────
        composable(Screen.OverviewAllAccounts.route) {
            // OverviewAllAccountsScreen(navController)
        }

        // ── Settings Sub-screens ───────────────────────────────────────────
        composable(Screen.SettingsApiKey.route) { }
        composable(Screen.SettingsTheme.route) { }
        composable(Screen.SettingsBackup.route) { }
        composable(Screen.SettingsSecurity.route) { }
        composable(Screen.SettingsAbout.route) { }
    }
}