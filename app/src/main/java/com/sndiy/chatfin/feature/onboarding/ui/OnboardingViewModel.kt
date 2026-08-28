// app/src/main/java/com/sndiy/chatfin/feature/onboarding/ui/OnboardingViewModel.kt

package com.sndiy.chatfin.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.data.local.AppPreferences
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _isComplete = MutableStateFlow(false)
    val isComplete: StateFlow<Boolean> = _isComplete.asStateFlow()

    fun startOffline() {
        viewModelScope.launch {
            val existing = accountRepo.getAllAccounts().first()
            if (existing.isEmpty()) {
                val accountId = accountRepo.createAccount(name = "Utama")
                accountRepo.switchActiveAccount(accountId)
            } else {
                val active = accountRepo.getActiveAccount().first()
                if (active == null) {
                    accountRepo.switchActiveAccount(existing.first().id)
                }
            }
            appPreferences.setOnboardingDone(true)
            _isComplete.value = true
        }
    }
}
