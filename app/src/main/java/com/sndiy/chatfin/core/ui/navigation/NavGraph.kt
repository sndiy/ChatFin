// app/src/main/java/com/sndiy/chatfin/core/ui/navigation/NavGraph.kt
// ⚠️ TIMPA seluruh isi NavGraph.kt yang lama dengan file ini

package com.sndiy.chatfin.core.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sndiy.chatfin.feature.finance.account.ui.AccountFormScreen
import com.sndiy.chatfin.feature.finance.account.ui.AccountListScreen

// ── Placeholder untuk screen yang belum diimplementasi ───────────────────────
@Composable
fun PlaceholderScreen(name: String) {
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector        = Icons.Default.Construction,
            contentDescription = null,
            modifier           = Modifier.size(64.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = name, style = MaterialTheme.typography.titleMedium)
        Text(
            text  = "Akan hadir di fase berikutnya",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Navigation Graph Utama ────────────────────────────────────────────────────
@Composable
fun ChatFinNavGraph(navController: NavHostController) {
    NavHost(
        navController      = navController,
        startDestination   = Screen.Dashboard.route,
        enterTransition    = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) +
                    fadeIn(animationSpec = tween(300))
        },
        exitTransition     = {
            slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) +
                    fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) +
                    fadeIn(animationSpec = tween(300))
        },
        popExitTransition  = {
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) +
                    fadeOut(animationSpec = tween(300))
        }
    ) {
        // ── Bottom Nav (placeholder) ───────────────────────────────────────────
        composable(Screen.Chat.route)            { PlaceholderScreen("Chat") }
        composable(Screen.Dashboard.route)       { PlaceholderScreen("Dashboard") }
        composable(Screen.AddTransaction.route)  { PlaceholderScreen("Tambah Transaksi") }
        composable(Screen.Analytics.route)       { PlaceholderScreen("Analitik") }
        composable(Screen.Settings.route)        { PlaceholderScreen("Setelan") }

        // ── Account ────────────────────────────────────────────────────────────
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

        // ── Character ──────────────────────────────────────────────────────────
        composable(Screen.CharacterList.route) { PlaceholderScreen("Daftar Karakter") }
        composable(
            route     = Screen.CharacterBuilder.route,
            arguments = listOf(navArgument("characterId") {
                type = NavType.StringType; defaultValue = "new"
            })
        ) { PlaceholderScreen("Character Builder") }

        // ── Transaction ────────────────────────────────────────────────────────
        composable(Screen.TransactionList.route) { PlaceholderScreen("Transaksi") }
        composable(
            route     = Screen.TransactionForm.route,
            arguments = listOf(navArgument("transactionId") {
                type = NavType.StringType; defaultValue = "new"
            })
        ) { PlaceholderScreen("Form Transaksi") }
        composable(
            route     = Screen.TransactionDetail.route,
            arguments = listOf(navArgument("transactionId") {
                type = NavType.StringType
            })
        ) { PlaceholderScreen("Detail Transaksi") }

        // ── Wallet ─────────────────────────────────────────────────────────────
        composable(Screen.WalletList.route) { PlaceholderScreen("Dompet") }
        composable(
            route     = Screen.WalletForm.route,
            arguments = listOf(navArgument("walletId") {
                type = NavType.StringType; defaultValue = "new"
            })
        ) { PlaceholderScreen("Form Dompet") }

        // ── Category ───────────────────────────────────────────────────────────
        composable(Screen.CategoryList.route) { PlaceholderScreen("Kategori") }
        composable(
            route     = Screen.CategoryForm.route,
            arguments = listOf(navArgument("categoryId") {
                type = NavType.StringType; defaultValue = "new"
            })
        ) { PlaceholderScreen("Form Kategori") }

        // ── Budget ─────────────────────────────────────────────────────────────
        composable(Screen.BudgetList.route) { PlaceholderScreen("Budget") }
        composable(
            route     = Screen.BudgetForm.route,
            arguments = listOf(navArgument("budgetId") {
                type = NavType.StringType; defaultValue = "new"
            })
        ) { PlaceholderScreen("Form Budget") }

        // ── Savings Goal ───────────────────────────────────────────────────────
        composable(Screen.SavingsGoalList.route) { PlaceholderScreen("Tabungan") }
        composable(
            route     = Screen.SavingsGoalForm.route,
            arguments = listOf(navArgument("goalId") {
                type = NavType.StringType; defaultValue = "new"
            })
        ) { PlaceholderScreen("Form Tabungan") }

        // ── Overview & Settings ────────────────────────────────────────────────
        composable(Screen.OverviewAllAccounts.route) { PlaceholderScreen("Semua Akun") }
        composable(Screen.SettingsApiKey.route)      { PlaceholderScreen("API Key") }
        composable(Screen.SettingsTheme.route)       { PlaceholderScreen("Tema") }
        composable(Screen.SettingsBackup.route)      { PlaceholderScreen("Backup") }
        composable(Screen.SettingsSecurity.route)    { PlaceholderScreen("Keamanan") }
        composable(Screen.SettingsAbout.route)       { PlaceholderScreen("Tentang") }
    }
}