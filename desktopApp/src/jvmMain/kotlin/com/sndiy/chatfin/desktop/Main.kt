package com.sndiy.chatfin.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sndiy.chatfin.core.di.initKoin
import org.koin.core.context.GlobalContext

fun main() {
    // Initialize Koin DI if not already started
    if (GlobalContext.getOrNull() == null) {
        initKoin()
    }

    application {
        val windowState = rememberWindowState(width = 1100.dp, height = 750.dp)
        Window(
            onCloseRequest = ::exitApplication,
            title = "ChatFin Desktop — Asisten Keuangan Personal (Sakurajima Mai)",
            state = windowState
        ) {
            DesktopApp()
        }
    }
}
