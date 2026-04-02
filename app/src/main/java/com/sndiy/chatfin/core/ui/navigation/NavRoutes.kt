package com.sndiy.chatfin.core.ui.navigation

sealed class Screen(val route: String) {

    data object Splash : Screen("splash")
    data object Auth   : Screen("auth")

    data object Dashboard      : Screen("dashboard")
    data object Chat           : Screen("chat")
    data object TransactionList: Screen("transaction_list")
    data object Settings       : Screen("settings")

    data object AccountList : Screen("account_list")
    data object AccountForm : Screen("account_form/{accountId}") {
        fun createRoute(accountId: String = "new") = "account_form/$accountId"
    }

    data object WalletList : Screen("wallet_list")
    data object WalletForm : Screen("wallet_form/{walletId}") {
        fun createRoute(walletId: String = "new") = "wallet_form/$walletId"
    }

    data object CategoryList : Screen("category_list")
    data object TransactionForm : Screen("transaction_form")

    data object SettingsTheme  : Screen("settings_theme")
    data object SettingsBackup : Screen("settings_backup")
    data object SettingsAbout  : Screen("settings_about")
    data object SyncSettings   : Screen("sync_settings")
}