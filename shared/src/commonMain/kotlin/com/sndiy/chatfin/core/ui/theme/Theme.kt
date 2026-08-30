package com.sndiy.chatfin.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
expect fun PlatformThemeEffect(colorScheme: ColorScheme, darkTheme: Boolean)

@Composable
fun ChatFinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentKey: String  = "Indigo",
    content: @Composable () -> Unit
) {
    val accent      = AppAccents.find { it.key == accentKey } ?: AppAccents.first()
    val colorScheme = if (darkTheme) accent.darkScheme else accent.lightScheme

    PlatformThemeEffect(colorScheme = colorScheme, darkTheme = darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = ChatFinTypography,
        shapes      = ChatFinShapes,
        content     = content
    )
}
