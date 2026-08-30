package com.sndiy.chatfin.core.data.auth

/**
 * Model data representasi pengguna terautentikasi (KMP-ready).
 */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String? = null,
    val isAnonymous: Boolean = false
)
