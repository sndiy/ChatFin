package com.sndiy.chatfin.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// ChatFin Design System — Color Palette
// ─────────────────────────────────────────────────────────────────────────────

// ── Brand Blue ───────────────────────────────────────────────────────────────
val Blue10  = Color(0xFF001D36)
val Blue20  = Color(0xFF003258)
val Blue30  = Color(0xFF00497D)
val Blue40  = Color(0xFF0061A4)
val Blue80  = Color(0xFF9ECAFF)
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
val IncomeGreen      = Color(0xFF1B8A4C)
val IncomeGreenLight = Color(0xFFD4F2E3)
val ExpenseRed       = Color(0xFFBA1A1A)
val ExpenseRedLight  = Color(0xFFFFDAD6)
val TransferBlue     = Color(0xFF0061A4)
val TransferBlueLt   = Color(0xFFD1E4FF)
val SavingsGold      = Color(0xFFB08800)
val SavingsGoldLight = Color(0xFFFFF0B2)
val BudgetOrange     = Color(0xFFBF5B00)
val BudgetOrangeLt   = Color(0xFFFFDCBC)

// ── Chart Color Palette ───────────────────────────────────────────────────────
val ChartColors = listOf(
    Color(0xFF0061A4), Color(0xFF006874), Color(0xFF1B8A4C),
    Color(0xFFBF5B00), Color(0xFF7B3294), Color(0xFFBA1A1A),
    Color(0xFF006D3B), Color(0xFF9A4521), Color(0xFF005FAD), Color(0xFF00696F),
)

// ── Account Accent Colors ─────────────────────────────────────────────────────
val AccountColors = listOf(
    Color(0xFF0061A4), Color(0xFF006874), Color(0xFF1B8A4C),
    Color(0xFF7B3294), Color(0xFFBF5B00), Color(0xFFBA1A1A),
    Color(0xFF005FAD), Color(0xFF9A4521),
)

// ── Tema warna yang tersedia ──────────────────────────────────────────────────
data class AppAccent(
    val key: String,
    val label: String,
    val previewColor: Color,
    val lightScheme: androidx.compose.material3.ColorScheme,
    val darkScheme: androidx.compose.material3.ColorScheme
)

val AppAccents = listOf(
    AppAccent(
        key          = "Indigo",
        label        = "Indigo",
        previewColor = Color(0xFF5B6EF5),
        lightScheme  = lightColorScheme(
            primary = Color(0xFF5B6EF5), onPrimary = Color.White,
            primaryContainer = Color(0xFFE8EAFF), onPrimaryContainer = Color(0xFF0010A0),
            secondary = Color(0xFF7E57C2), onSecondary = Color.White,
            background = Color(0xFFF5F5F5), onBackground = Color(0xFF1C1C1E),
            surface = Color(0xFFFAFAFA), onSurface = Color(0xFF1C1C1E),
            surfaceVariant = Color(0xFFE8E8F0), onSurfaceVariant = Color(0xFF44444F),
        ),
        darkScheme = darkColorScheme(
            primary = Color(0xFF9BA8FF), onPrimary = Color(0xFF1A1A2E),
            primaryContainer = Color(0xFF3D4ACF), onPrimaryContainer = Color(0xFFDDE1FF),
            secondary = Color(0xFFB39DDB),
            background = Color(0xFF12121C), onBackground = Color(0xFFE4E4F0),
            surface = Color(0xFF1E1E2E), onSurface = Color(0xFFE4E4F0),
            surfaceVariant = Color(0xFF2A2A3C), onSurfaceVariant = Color(0xFFB0B0C0),
        )
    ),
    AppAccent(
        key          = "Hijau",
        label        = "Hijau",
        previewColor = Color(0xFF1B8A4C),
        lightScheme  = lightColorScheme(
            primary = Color(0xFF1B8A4C), onPrimary = Color.White,
            primaryContainer = Color(0xFFB7F0D2), onPrimaryContainer = Color(0xFF002113),
            secondary = Color(0xFF26A69A), onSecondary = Color.White,
            background = Color(0xFFF4FAF6), onBackground = Color(0xFF1C1C1E),
            surface = Color(0xFFF8FCF9), onSurface = Color(0xFF1C1C1E),
            surfaceVariant = Color(0xFFDFF0E8), onSurfaceVariant = Color(0xFF3A4A3F),
        ),
        darkScheme = darkColorScheme(
            primary = Color(0xFF6DDFA0), onPrimary = Color(0xFF003822),
            primaryContainer = Color(0xFF005234), onPrimaryContainer = Color(0xFFB7F0D2),
            secondary = Color(0xFF80CBC4),
            background = Color(0xFF0F1A13), onBackground = Color(0xFFDEF0E6),
            surface = Color(0xFF161E19), onSurface = Color(0xFFDEF0E6),
            surfaceVariant = Color(0xFF1F2E24), onSurfaceVariant = Color(0xFFA0B8A8),
        )
    ),
    AppAccent(
        key          = "Ungu",
        label        = "Ungu",
        previewColor = Color(0xFF7E57C2),
        lightScheme  = lightColorScheme(
            primary = Color(0xFF7E57C2), onPrimary = Color.White,
            primaryContainer = Color(0xFFEDE7F6), onPrimaryContainer = Color(0xFF2A0080),
            secondary = Color(0xFFAB47BC), onSecondary = Color.White,
            background = Color(0xFFF7F5FA), onBackground = Color(0xFF1C1C1E),
            surface = Color(0xFFFBF9FF), onSurface = Color(0xFF1C1C1E),
            surfaceVariant = Color(0xFFECE8F5), onSurfaceVariant = Color(0xFF48404F),
        ),
        darkScheme = darkColorScheme(
            primary = Color(0xFFCFB8FF), onPrimary = Color(0xFF20005A),
            primaryContainer = Color(0xFF5C3DA8), onPrimaryContainer = Color(0xFFEDE7F6),
            secondary = Color(0xFFE0A8EA),
            background = Color(0xFF161218), onBackground = Color(0xFFEDE8F5),
            surface = Color(0xFF1E1A22), onSurface = Color(0xFFEDE8F5),
            surfaceVariant = Color(0xFF2A2430), onSurfaceVariant = Color(0xFFB8AECB),
        )
    ),
    AppAccent(
        key          = "Biru",
        label        = "Biru",
        previewColor = Color(0xFF0061A4),
        lightScheme  = lightColorScheme(
            primary = Color(0xFF0061A4), onPrimary = Color.White,
            primaryContainer = Color(0xFFD1E4FF), onPrimaryContainer = Color(0xFF001D36),
            secondary = Color(0xFF0288D1), onSecondary = Color.White,
            background = Color(0xFFF4F7FA), onBackground = Color(0xFF1C1C1E),
            surface = Color(0xFFF8FAFE), onSurface = Color(0xFF1C1C1E),
            surfaceVariant = Color(0xFFDFE8F0), onSurfaceVariant = Color(0xFF3C4450),
        ),
        darkScheme = darkColorScheme(
            primary = Color(0xFF9ECAFF), onPrimary = Color(0xFF003258),
            primaryContainer = Color(0xFF00497D), onPrimaryContainer = Color(0xFFD1E4FF),
            secondary = Color(0xFF80D0FF),
            background = Color(0xFF0F1318), onBackground = Color(0xFFE0E8F0),
            surface = Color(0xFF161A20), onSurface = Color(0xFFE0E8F0),
            surfaceVariant = Color(0xFF1E252E), onSurfaceVariant = Color(0xFFA0B4C8),
        )
    ),
    AppAccent(
        key          = "Oranye",
        label        = "Oranye",
        previewColor = Color(0xFFF4511E),
        lightScheme  = lightColorScheme(
            primary = Color(0xFFF4511E), onPrimary = Color.White,
            primaryContainer = Color(0xFFFFDDD6), onPrimaryContainer = Color(0xFF3A0A00),
            secondary = Color(0xFFFF8A65), onSecondary = Color.White,
            background = Color(0xFFFAF5F4), onBackground = Color(0xFF1C1C1E),
            surface = Color(0xFFFFFAF9), onSurface = Color(0xFF1C1C1E),
            surfaceVariant = Color(0xFFF5E8E4), onSurfaceVariant = Color(0xFF50403C),
        ),
        darkScheme = darkColorScheme(
            primary = Color(0xFFFFB5A0), onPrimary = Color(0xFF5A1500),
            primaryContainer = Color(0xFFB83200), onPrimaryContainer = Color(0xFFFFDDD6),
            secondary = Color(0xFFFFB08A),
            background = Color(0xFF1A100E), onBackground = Color(0xFFF0E4E0),
            surface = Color(0xFF221614), onSurface = Color(0xFFF0E4E0),
            surfaceVariant = Color(0xFF2E1E1A), onSurfaceVariant = Color(0xFFC8A8A0),
        )
    ),
    AppAccent(
        key          = "Abu",
        label        = "Abu-abu",
        previewColor = Color(0xFF546E7A),
        lightScheme  = lightColorScheme(
            primary = Color(0xFF546E7A), onPrimary = Color.White,
            primaryContainer = Color(0xFFCFE8F0), onPrimaryContainer = Color(0xFF0A1F28),
            secondary = Color(0xFF78909C), onSecondary = Color.White,
            background = Color(0xFFF4F6F7), onBackground = Color(0xFF1C1C1E),
            surface = Color(0xFFF8FAFB), onSurface = Color(0xFF1C1C1E),
            surfaceVariant = Color(0xFFE0E8EC), onSurfaceVariant = Color(0xFF3C484E),
        ),
        darkScheme = darkColorScheme(
            primary = Color(0xFFB0CDD8), onPrimary = Color(0xFF1A2E36),
            primaryContainer = Color(0xFF3A5560), onPrimaryContainer = Color(0xFFCFE8F0),
            secondary = Color(0xFFA8C0CC),
            background = Color(0xFF101518), onBackground = Color(0xFFDEE8EC),
            surface = Color(0xFF181E22), onSurface = Color(0xFFDEE8EC),
            surfaceVariant = Color(0xFF20282C), onSurfaceVariant = Color(0xFFA0B4BC),
        )
    ),
)