package com.sndiy.chatfin.core.ocr

import java.util.UUID

/**
 * Representasi item individu pada struk belanja (Multiplatform model).
 */
data class ParsedReceiptItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val price: Long,
    val isLowConfidence: Boolean = false
)
