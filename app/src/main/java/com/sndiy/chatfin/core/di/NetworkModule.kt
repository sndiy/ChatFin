// app/src/main/java/com/sndiy/chatfin/core/di/NetworkModule.kt

package com.sndiy.chatfin.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

// Satu-satunya konsumen OkHttpClient saat ini: GeminiClient (panggilan REST langsung ke
// Gemini API — SDK com.google.ai.client.generativeai sudah deprecated Google, lihat komentar
// migrasi di GeminiClient.kt). Timeout di sini adalah pengaman level jaringan yang SEBELUMNYA
// tidak ada sama sekali di jalur chat (request bisa hang tanpa batas kalau server tidak
// merespons) — jalur scan struk tetap punya lapisan timeout tambahan level coroutine
// (ReceiptAiEnhancer, 15 detik) di atas ini.
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
}
