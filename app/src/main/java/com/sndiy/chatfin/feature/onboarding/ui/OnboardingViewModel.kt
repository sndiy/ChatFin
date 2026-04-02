// app/src/main/java/com/sndiy/chatfin/feature/onboarding/ui/OnboardingViewModel.kt

package com.sndiy.chatfin.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.data.local.AppPreferences
import com.sndiy.chatfin.core.data.local.dao.WalletDao
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
    private val walletDao: WalletDao,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _isComplete = MutableStateFlow(false)
    val isComplete: StateFlow<Boolean> = _isComplete.asStateFlow()

    fun setupAccount(accountName: String, initialBalance: Long) {
        viewModelScope.launch {
            // 1. Buat akun + wallet default "Kas"
            val accountId = accountRepo.createAccount(name = accountName)

            // 2. Set saldo awal ke wallet Kas
            if (initialBalance > 0) {
                val wallets = walletDao.getWalletsByAccount(accountId).first()
                val kasWallet = wallets.firstOrNull()
                kasWallet?.let {
                    walletDao.addToBalance(it.id, initialBalance)
                }
            }

            // 3. Aktifkan akun
            accountRepo.switchActiveAccount(accountId)

            // 4. Tandai onboarding selesai
            appPreferences.setOnboardingDone(true)

            _isComplete.value = true
        }
    }
}
