package com.sndiy.chatfin.core.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// ChatFin Design System — Color Palette
// Primary: Blue (Material3 Dynamic Color compatible)
// ─────────────────────────────────────────────────────────────────────────────

// ── Brand Blue ───────────────────────────────────────────────────────────────
val Blue10  = Color(0xFF001D36)
val Blue20  = Color(0xFF003258)
val Blue30  = Color(0xFF00497D)
val Blue40  = Color(0xFF0061A4)   // Primary
val Blue80  = Color(0xFF9ECAFF)   // Primary (dark theme)
val Blue90  = Color(0xFFD1E4FF)
val Blue95  = Color(0xFFEAF2FF)
val Blue99  = Color(0xFFFCFCFF)

// ── Secondary (Blue-Grey) ─────────────────────────────────────────────────────
val BlueGrey10 = Color(0xFF0E1B2A)
val BlueGrey20 = Color(0xFF233140)
val BlueGrey30 = Color(0xFF394857)
val BlueGrey40 = Color(0xFF51606F)
val BlueGrey80 = Color(0xFFB7C8D9)
val BlueGrey90 = Color(0xFFD3E4F5)

// ── Tertiary (Cyan accent) ────────────────────────────────────────────────────
val Cyan10 = Color(0xFF001F27)
val Cyan20 = Color(0xFF00363F)
val Cyan30 = Color(0xFF004E59)
val Cyan40 = Color(0xFF006874)
val Cyan80 = Color(0xFF4FD8EB)
val Cyan90 = Color(0xFFB2EBEE)

// ── Error ─────────────────────────────────────────────────────────────────────
val Red10 = Color(0xFF410002)
val Red20 = Color(0xFF690005)
val Red30 = Color(0xFF93000A)
val Red40 = Color(0xFFBA1A1A)
val Red80 = Color(0xFFFFB4AB)
val Red90 = Color(0xFFFFDAD6)

// ── Neutral ───────────────────────────────────────────────────────────────────
val Grey10  = Color(0xFF191C1E)
val Grey20  = Color(0xFF2E3133)
val Grey90  = Color(0xFFE1E3E5)
val Grey95  = Color(0xFFEFF1F3)
val Grey99  = Color(0xFFFCFCFF)

// ── Finance Semantic Colors ───────────────────────────────────────────────────
// Dipakai untuk chart, transaksi, dan dashboard
val IncomeGreen     = Color(0xFF1B8A4C)   // Pemasukan
val IncomeGreenLight= Color(0xFFD4F2E3)
val ExpenseRed      = Color(0xFFBA1A1A)   // Pengeluaran
val ExpenseRedLight = Color(0xFFFFDAD6)
val TransferBlue    = Color(0xFF0061A4)   // Transfer
val TransferBlueLt  = Color(0xFFD1E4FF)
val SavingsGold     = Color(0xFFB08800)   // Savings Goal
val SavingsGoldLight= Color(0xFFFFF0B2)
val BudgetOrange    = Color(0xFFBF5B00)   // Budget warning
val BudgetOrangeLt  = Color(0xFFFFDCBC)

// ── Chart Color Palette (10 warna untuk kategori) ────────────────────────────
val ChartColors = listOf(
    Color(0xFF0061A4),   // Blue
    Color(0xFF006874),   // Cyan
    Color(0xFF1B8A4C),   // Green
    Color(0xFFBF5B00),   // Orange
    Color(0xFF7B3294),   // Purple
    Color(0xFFBA1A1A),   // Red
    Color(0xFF006D3B),   // Dark Green
    Color(0xFF9A4521),   // Brown
    Color(0xFF005FAD),   // Indigo
    Color(0xFF00696F),   // Teal
)

// ── Account Accent Colors (untuk Account Switcher) ────────────────────────────
val AccountColors = listOf(
    Color(0xFF0061A4),   // Blue - default
    Color(0xFF006874),   // Teal
    Color(0xFF1B8A4C),   // Green
    Color(0xFF7B3294),   // Purple
    Color(0xFFBF5B00),   // Orange
    Color(0xFFBA1A1A),   // Red
    Color(0xFF005FAD),   // Indigo
    Color(0xFF9A4521),   // Brown
)