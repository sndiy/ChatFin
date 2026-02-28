// app/src/main/java/com/sndiy/chatfin/core/di/NetworkModule.kt

package com.sndiy.chatfin.core.di

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.sndiy.chatfin.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Gemini 2.5 Flash — model utama untuk chat berkarakter & analisis keuangan
    // Free tier: 10 RPM (request per menit), 250 RPD (request per hari)
    @Provides
    @Singleton
    @Named("flash")
    fun provideGeminiFlash(): GenerativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey    = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            temperature     = 0.9f      // agak kreatif untuk kepribadian karakter
            topK            = 40
            topP            = 0.95f
            maxOutputTokens = 1024
        }
    )

    // Gemini 2.5 Flash-Lite — untuk task ringan seperti klasifikasi transaksi otomatis
    // Free tier: 15 RPM, 1000 RPD (lebih besar dari Flash)
    @Provides
    @Singleton
    @Named("flash_lite")
    fun provideGeminiFlashLite(): GenerativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
        apiKey    = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            temperature     = 0.3f      // lebih deterministik untuk klasifikasi
            topK            = 20
            topP            = 0.9f
            maxOutputTokens = 256
        }
    )
}