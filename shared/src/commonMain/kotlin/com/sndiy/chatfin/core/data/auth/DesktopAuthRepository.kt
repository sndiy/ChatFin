package com.sndiy.chatfin.core.data.auth

import kotlinx.coroutines.flow.Flow

/**
 * Kontrak layanan autentikasi untuk platform Desktop (JVM).
 */
interface DesktopAuthRepository {
    val currentUser: AuthUser?
    val isLoggedIn: Boolean
    val authState: Flow<AuthUser?>

    suspend fun loginWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun registerWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun logout()
}
