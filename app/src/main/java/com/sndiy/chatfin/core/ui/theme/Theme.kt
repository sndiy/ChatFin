package com.sndiy.chatfin.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary            = Primary,
    onPrimary          = OnPrimary,
    primaryContainer   = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary          = Secondary,
    onSecondary        = OnSecondary,
    background         = Background,
    onBackground       = Color(0xFF1C1C1E),
    surface            = Surface,
    onSurface          = Color(0xFF1C1C1E),
    surfaceVariant     = SurfaceVariant,
    onSurfaceVariant   = Color(0xFF44444F),
)

private val DarkColors = darkColorScheme(
    primary            = PrimaryDark,
    onPrimary          = Color(0xFF1A1A2E),
    primaryContainer   = PrimaryContainerDark,
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary          = Color(0xFFB39DDB),
    background         = BackgroundDark,
    onBackground       = Color(0xFFE4E4F0),
    surface            = SurfaceDark,
    onSurface          = Color(0xFFE4E4F0),
    surfaceVariant     = SurfaceVariantDark,
    onSurfaceVariant   = Color(0xFFB0B0C0),
)

@Composable
fun ChatFinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = ChatFinTypography,
        shapes      = ChatFinShapes,
        content     = content
    )
}