// app/src/main/java/com/sndiy/chatfin/feature/splash/ui/SplashViewModel.kt
//
// PERUBAHAN: Cek onboardingDone dari AppPreferences
// - Jika belum onboarding → Onboarding
// - Jika sudah onboarding tapi belum ada akun → Onboarding (fallback)
// - Jika sudah ada akun → Dashboard

package com.sndiy.chatfin.feature.splash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.data.local.AppPreferences
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SplashDestination {
    object Loading    : SplashDestination()
    object Dashboard  : SplashDestination()
    object Onboarding : SplashDestination()
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _destination = MutableStateFlow<SplashDestination>(SplashDestination.Loading)
    val destination: StateFlow<SplashDestination> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            val minSplashJob = launch { delay(1500) }

            val onboardingDone = appPreferences.onboardingDone.first()
            val account        = accountRepo.getActiveAccount().first()

            minSplashJob.join()

            _destination.value = when {
                !onboardingDone       -> SplashDestination.Onboarding
                account != null       -> SplashDestination.Dashboard
                else                  -> SplashDestination.Onboarding
            }
        }
    }
}
