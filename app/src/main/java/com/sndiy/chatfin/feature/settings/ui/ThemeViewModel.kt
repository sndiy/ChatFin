package com.sndiy.chatfin.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.ui.theme.ThemeConfig
import com.sndiy.chatfin.core.ui.theme.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themePrefs: ThemePreferences
) : ViewModel() {

    val themeConfig: StateFlow<ThemeConfig> = themePrefs.themeConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeConfig())

    fun setDark(isDark: Boolean) {
        viewModelScope.launch { themePrefs.setDark(isDark) }
    }

    fun setAccent(accentKey: String) {
        viewModelScope.launch { themePrefs.setAccent(accentKey) }
    }
}