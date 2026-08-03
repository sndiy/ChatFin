package com.sndiy.chatfin.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.notification.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    val backgroundTaskNotifEnabled: StateFlow<Boolean> = notificationRepository.backgroundTaskNotifEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val dailyReminderNotifEnabled: StateFlow<Boolean> = notificationRepository.dailyReminderNotifEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    fun toggleBackgroundTaskNotif(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            notificationRepository.setBackgroundTaskNotifEnabled(enabled)
        }
    }

    fun toggleDailyReminderNotif(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            notificationRepository.setDailyReminderNotifEnabled(enabled)
        }
    }
}