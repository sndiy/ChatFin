package com.sndiy.chatfin.core.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

// ─────────────────────────────────────────────────────────────────────────────
// Typography
// Gunakan font dari Google Fonts via downloadable fonts
// Display: Nunito (friendly, bulat, cocok untuk angka keuangan)
// Body: DM Sans (bersih, modern, readability tinggi)
// ─────────────────────────────────────────────────────────────────────────────

// Catatan: Tambahkan font files ke res/font/ atau gunakan
// Google Fonts Compose library (tambah di build.gradle):
// implementation("androidx.compose.ui:ui-text-google-fonts:1.7.8")

val ChatFinTypography = Typography(
    // Display — judul besar (total saldo, angka utama)
    displayLarge = TextStyle(
        fontWeight  = FontWeight.Bold,
        fontSize    = 57.sp,
        lineHeight  = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontWeight  = FontWeight.Bold,
        fontSize    = 45.sp,
        lineHeight  = 52.sp,
    ),
    displaySmall = TextStyle(
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 36.sp,
        lineHeight  = 44.sp,
    ),

    // Headline — section header, nama akun
    headlineLarge = TextStyle(
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 32.sp,
        lineHeight  = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 28.sp,
        lineHeight  = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 24.sp,
        lineHeight  = 32.sp,
    ),

    // Title — card titles, list items
    titleLarge = TextStyle(
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 22.sp,
        lineHeight  = 28.sp,
    ),
    titleMedium = TextStyle(
        fontWeight  = FontWeight.Medium,
        fontSize    = 16.sp,
        lineHeight  = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight  = FontWeight.Medium,
        fontSize    = 14.sp,
        lineHeight  = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // Body — konten utama, chat messages
    bodyLarge = TextStyle(
        fontWeight  = FontWeight.Normal,
        fontSize    = 16.sp,
        lineHeight  = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight  = FontWeight.Normal,
        fontSize    = 14.sp,
        lineHeight  = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontWeight  = FontWeight.Normal,
        fontSize    = 12.sp,
        lineHeight  = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // Label — badge, chip, bottom nav
    labelLarge = TextStyle(
        fontWeight  = FontWeight.Medium,
        fontSize    = 14.sp,
        lineHeight  = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight  = FontWeight.Medium,
        fontSize    = 12.sp,
        lineHeight  = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight  = FontWeight.Medium,
        fontSize    = 11.sp,
        lineHeight  = 16.sp,
        letterSpacing = 0.5.sp
    ),
)

// ─────────────────────────────────────────────────────────────────────────────
// Shapes — konsisten, sedikit rounded untuk kesan profesional tapi friendly
// ─────────────────────────────────────────────────────────────────────────────
val ChatFinShapes = Shapes(
    extraSmall  = RoundedCornerShape(4.dp),
    small       = RoundedCornerShape(8.dp),
    medium      = RoundedCornerShape(12.dp),
    large       = RoundedCornerShape(16.dp),
    extraLarge  = RoundedCornerShape(28.dp),
)

// ─────────────────────────────────────────────────────────────────────────────
// Spacing constants — gunakan ini agar konsisten di seluruh app
// ─────────────────────────────────────────────────────────────────────────────
object ChatFinSpacing {
    val xs   = 4.dp
    val sm   = 8.dp
    val md   = 16.dp
    val lg   = 24.dp
    val xl   = 32.dp
    val xxl  = 48.dp
    val xxxl = 64.dp
}