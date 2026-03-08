package com.sndiy.chatfin.core.ui.navigation

sealed class Screen(val route: String) {

    // ── Splash ────────────────────────────────────────────────────────────────
    data object Splash : Screen("splash")

    // ── Bottom Nav ────────────────────────────────────────────────────────────
    data object Chat            : Screen("chat")
    data object Dashboard       : Screen("dashboard")
    data object AddTransaction  : Screen("add_transaction")
    data object Analytics       : Screen("analytics")
    data object Settings        : Screen("settings")

    // ── Character ─────────────────────────────────────────────────────────────
    data object CharacterList    : Screen("character_list")
    data object CharacterBuilder : Screen("character_builder/{characterId}") {
        fun createRoute(characterId: String = "new") = "character_builder/$characterId"
    }

    // ── Account ───────────────────────────────────────────────────────────────
    data object AccountList : Screen("account_list")
    data object AccountForm : Screen("account_form/{accountId}") {
        fun createRoute(accountId: String = "new") = "account_form/$accountId"
    }

    // ── Transaction ───────────────────────────────────────────────────────────
    data object TransactionList   : Screen("transaction_list")
    data object TransactionForm   : Screen("transaction_form/{transactionId}") {
        fun createRoute(transactionId: String = "new") = "transaction_form/$transactionId"
    }
    data object TransactionDetail : Screen("transaction_detail/{transactionId}") {
        fun createRoute(transactionId: String) = "transaction_detail/$transactionId"
    }

    // ── Wallet ────────────────────────────────────────────────────────────────
    data object WalletList : Screen("wallet_list")
    data object WalletForm : Screen("wallet_form/{walletId}") {
        fun createRoute(walletId: String = "new") = "wallet_form/$walletId"
    }

    // ── Category ──────────────────────────────────────────────────────────────
    data object CategoryList : Screen("category_list")
    data object CategoryForm : Screen("category_form/{categoryId}") {
        fun createRoute(categoryId: String = "new") = "category_form/$categoryId"
    }

    // ── Budget ────────────────────────────────────────────────────────────────
    data object BudgetList : Screen("budget_list")
    data object BudgetForm : Screen("budget_form/{budgetId}") {
        fun createRoute(budgetId: String = "new") = "budget_form/$budgetId"
    }

    // ── Savings Goal ──────────────────────────────────────────────────────────
    data object SavingsGoalList : Screen("savings_goal_list")
    data object SavingsGoalForm : Screen("savings_goal_form/{goalId}") {
        fun createRoute(goalId: String = "new") = "savings_goal_form/$goalId"
    }

    // ── Overview ──────────────────────────────────────────────────────────────
    data object OverviewAllAccounts : Screen("overview_all_accounts")

    // ── Settings Sub-screens ──────────────────────────────────────────────────
    data object SettingsApiKey   : Screen("settings_api_key")
    data object SettingsTheme    : Screen("settings_theme")
    data object SettingsBackup   : Screen("settings_backup")
    data object SettingsSecurity : Screen("settings_security")
    data object SettingsAbout    : Screen("settings_about")
}