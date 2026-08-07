package com.sndiy.chatfin.ai

import kotlinx.serialization.Serializable

/**
 * DTO request/response REST `generateContent` Gemini API (dipakai satu-satunya dari
 * [GeminiClient] — lihat komentar migrasi di sana). Nama field JSON Gemini memang camelCase,
 * cocok 1:1 dengan konvensi penamaan Kotlin, jadi tidak perlu anotasi `@SerialName` di mana pun
 * di file ini.
 */
@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@Serializable
data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null,
    val responseMimeType: String? = null
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val promptFeedback: GeminiPromptFeedback? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

@Serializable
data class GeminiPromptFeedback(
    val blockReason: String? = null
)

/** Bentuk body error standar Gemini REST API, mis. `{"error":{"code":429,"message":"...","status":"RESOURCE_EXHAUSTED"}}`. */
@Serializable
data class GeminiErrorEnvelope(
    val error: GeminiErrorDetail? = null
)

@Serializable
data class GeminiErrorDetail(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)
