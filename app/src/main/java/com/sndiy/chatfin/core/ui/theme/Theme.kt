package com.sndiy.chatfin.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun ChatFinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentKey: String  = "Indigo",
    content: @Composable () -> Unit
) {
    val accent      = AppAccents.find { it.key == accentKey } ?: AppAccents.first()
    val colorScheme = if (darkTheme) accent.darkScheme else accent.lightScheme

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