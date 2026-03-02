// app/src/main/java/com/sndiy/chatfin/core/di/NetworkModule.kt

package com.sndiy.chatfin.core.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Firebase AI SDK membuat model on-demand di GeminiRepository,
// tidak perlu inject GenerativeModel lewat Hilt lagi.
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule