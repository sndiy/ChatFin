package com.sndiy.chatfin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.sndiy.chatfin.core.ui.navigation.ChatFinNavGraph
import com.sndiy.chatfin.core.ui.theme.ChatFinTheme
import com.sndiy.chatfin.feature.settings.ui.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeConfig by themeViewModel.themeConfig.collectAsStateWithLifecycle()
            ChatFinTheme(
                darkTheme = themeConfig.isDark,
                accentKey = themeConfig.accentKey
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    ChatFinNavGraph(
                        navController = navController,
                        onFinish      = { finishAndRemoveTask() }
                    )
                }
            }
        }
    }
}