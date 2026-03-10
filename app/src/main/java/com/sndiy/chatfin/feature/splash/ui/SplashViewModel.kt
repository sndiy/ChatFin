package com.sndiy.chatfin.feature.splash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val accountRepo: AccountRepository
) : ViewModel() {

    private val _destination = MutableStateFlow<SplashDestination>(SplashDestination.Loading)
    val destination: StateFlow<SplashDestination> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            // Minimum splash 1.5 detik agar animasi terlihat
            val minSplashJob = launch { delay(1500) }

            val account = accountRepo.getActiveAccount().first()

            // Tunggu minimum splash selesai
            minSplashJob.join()

            _destination.value = if (account != null) {
                SplashDestination.Dashboard
            } else {
                SplashDestination.Onboarding
            }
        }
    }
}