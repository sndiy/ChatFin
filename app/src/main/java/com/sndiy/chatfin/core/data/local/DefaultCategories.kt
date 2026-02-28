package com.sndiy.chatfin.core.data.local

import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import java.util.UUID

/**
 * Kategori default yang di-seed ke database saat pertama kali install.
 * accountId = null artinya kategori global (tersedia untuk semua akun).
 */
object DefaultCategories {

    val expenseCategories = listOf(
        CategoryEntity(id = "exp_food",        accountId = null, name = "Makanan & Minuman", type = "EXPENSE", iconName = "restaurant",          colorHex = "#E53935", isCustom = false, sortOrder = 0),
        CategoryEntity(id = "exp_transport",   accountId = null, name = "Transportasi",      type = "EXPENSE", iconName = "directions_car",      colorHex = "#1E88E5", isCustom = false, sortOrder = 1),
        CategoryEntity(id = "exp_shopping",    accountId = null, name = "Belanja",           type = "EXPENSE", iconName = "shopping_bag",        colorHex = "#8E24AA", isCustom = false, sortOrder = 2),
        CategoryEntity(id = "exp_entertain",   accountId = null, name = "Hiburan",           type = "EXPENSE", iconName = "movie",               colorHex = "#F4511E", isCustom = false, sortOrder = 3),
        CategoryEntity(id = "exp_health",      accountId = null, name = "Kesehatan",         type = "EXPENSE", iconName = "local_hospital",      colorHex = "#00ACC1", isCustom = false, sortOrder = 4),
        CategoryEntity(id = "exp_education",   accountId = null, name = "Pendidikan",        type = "EXPENSE", iconName = "school",              colorHex = "#3949AB", isCustom = false, sortOrder = 5),
        CategoryEntity(id = "exp_bills",       accountId = null, name = "Tagihan & Utilitas",type = "EXPENSE", iconName = "receipt_long",        colorHex = "#F9A825", isCustom = false, sortOrder = 6),
        CategoryEntity(id = "exp_home",        accountId = null, name = "Rumah & Properti",  type = "EXPENSE", iconName = "home",                colorHex = "#558B2F", isCustom = false, sortOrder = 7),
        CategoryEntity(id = "exp_investment",  accountId = null, name = "Investasi",         type = "EXPENSE", iconName = "trending_up",         colorHex = "#0061A4", isCustom = false, sortOrder = 8),
        CategoryEntity(id = "exp_other",       accountId = null, name = "Lainnya",           type = "EXPENSE", iconName = "more_horiz",          colorHex = "#757575", isCustom = false, sortOrder = 9),
    )

    val incomeCategories = listOf(
        CategoryEntity(id = "inc_salary",      accountId = null, name = "Gaji",              type = "INCOME",  iconName = "payments",            colorHex = "#1B8A4C", isCustom = false, sortOrder = 0),
        CategoryEntity(id = "inc_freelance",   accountId = null, name = "Freelance",         type = "INCOME",  iconName = "work",                colorHex = "#2E7D32", isCustom = false, sortOrder = 1),
        CategoryEntity(id = "inc_bonus",       accountId = null, name = "Bonus",             type = "INCOME",  iconName = "card_giftcard",       colorHex = "#B08800", isCustom = false, sortOrder = 2),
        CategoryEntity(id = "inc_gift",        accountId = null, name = "Hadiah",            type = "INCOME",  iconName = "redeem",              colorHex = "#C2185B", isCustom = false, sortOrder = 3),
        CategoryEntity(id = "inc_invest",      accountId = null, name = "Return Investasi",  type = "INCOME",  iconName = "account_balance",     colorHex = "#0061A4", isCustom = false, sortOrder = 4),
        CategoryEntity(id = "inc_business",    accountId = null, name = "Bisnis",            type = "INCOME",  iconName = "storefront",          colorHex = "#00695C", isCustom = false, sortOrder = 5),
        CategoryEntity(id = "inc_other",       accountId = null, name = "Lainnya",           type = "INCOME",  iconName = "more_horiz",          colorHex = "#757575", isCustom = false, sortOrder = 6),
    )

    val all = expenseCategories + incomeCategories
}