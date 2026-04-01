// app/src/main/java/com/sndiy/chatfin/core/ui/theme/Theme.kt

package com.sndiy.chatfin.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─────────────────────────────────────────────────────────────────────────────
// ChatFin Theme — Material3 dengan Dynamic Color (Android 12+)
// Fallback ke Blue palette untuk Android < 12
// ─────────────────────────────────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary              = Blue40,
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = Blue90,
    onPrimaryContainer   = Blue10,
    secondary            = BlueGrey40,
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = BlueGrey90,
    onSecondaryContainer = BlueGrey10,
    tertiary             = Cyan40,
    onTertiary           = Color(0xFFFFFFFF),
    tertiaryContainer    = Cyan90,
    onTertiaryContainer  = Cyan10,
    error                = Red40,
    onError              = Color(0xFFFFFFFF),
    errorContainer       = Red90,
    onErrorContainer     = Red10,
    background           = Grey99,
    onBackground         = Grey10,
    surface              = Grey99,
    onSurface            = Grey10,
    surfaceVariant       = BlueGrey90,
    onSurfaceVariant     = BlueGrey30,
    outline              = BlueGrey40,
)

private val DarkColorScheme = darkColorScheme(
    primary              = Blue80,
    onPrimary            = Blue20,
    primaryContainer     = Blue30,
    onPrimaryContainer   = Blue90,
    secondary            = BlueGrey80,
    onSecondary          = BlueGrey20,
    secondaryContainer   = BlueGrey30,
    onSecondaryContainer = BlueGrey90,
    tertiary             = Cyan80,
    onTertiary           = Cyan20,
    tertiaryContainer    = Cyan30,
    onTertiaryContainer  = Cyan90,
    error                = Red80,
    onError              = Red20,
    errorContainer       = Red30,
    onErrorContainer     = Red90,
    background           = Grey10,
    onBackground         = Grey90,
    surface              = Grey10,
    onSurface            = Grey90,
    surfaceVariant       = BlueGrey20,
    onSurfaceVariant     = BlueGrey80,
    outline              = BlueGrey80,
)

@Composable
fun ChatFinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic Color: pakai warna wallpaper user (Android 12+)
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    // Set status bar color
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = ChatFinTypography,
        shapes      = ChatFinShapes,
        content     = content
    )
}