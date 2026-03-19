package com.sndiy.chatfin.core.ui.navigation

sealed class Screen(val route: String) {

    data object Splash : Screen("splash")
    data object Auth   : Screen("auth")

    data object Dashboard      : Screen("dashboard")
    data object Chat           : Screen("chat")
    data object TransactionList: Screen("transaction_list")
    data object Settings       : Screen("settings")
    data object Analytics      : Screen("analytics")

    data object AccountList : Screen("account_list")
    data object AccountForm : Screen("account_form/{accountId}") {
        fun createRoute(accountId: String = "new") = "account_form/$accountId"
    }

    data object TransactionForm : Screen("transaction_form/{transactionId}") {
        fun createRoute(transactionId: String = "new") = "transaction_form/$transactionId"
    }
    data object TransactionDetail : Screen("transaction_detail/{transactionId}") {
        fun createRoute(transactionId: String) = "transaction_detail/$transactionId"
    }

    data object WalletList : Screen("wallet_list")
    data object WalletForm : Screen("wallet_form/{walletId}") {
        fun createRoute(walletId: String = "new") = "wallet_form/$walletId"
    }

    data object CategoryList : Screen("category_list")

    data object SettingsTheme  : Screen("settings_theme")
    data object SettingsBackup : Screen("settings_backup")
    data object SettingsAbout  : Screen("settings_about")
    data object SyncSettings   : Screen("sync_settings")
}