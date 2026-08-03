package com.sndiy.chatfin.core.ocr

import android.graphics.RectF

/**
 * Representasi bounding box teks untuk overlay AR real-time kamera.
 */
data class TextBoundingBox(
    val text: String,
    val boundingBox: RectF
)

/**
 * Representasi item individu pada struk belanja.
 */
data class ParsedReceiptItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val price: Long,
    val isLowConfidence: Boolean = false
)

/**
 * Data terstruktur hasil ekstraksi & parsing struk belanja.
 */
data class ParsedReceipt(
    val merchant: String? = null,
    val date: String? = null,       // Format yyyy-MM-dd
    val time: String? = null,       // Format HH:mm
    val items: List<ParsedReceiptItem> = emptyList(),
    val totalAmount: Long? = null,
    val rawText: String = "",
    val isMerchantLowConfidence: Boolean = false,
    val isDateLowConfidence: Boolean = false,
    val isTotalLowConfidence: Boolean = false
) {
    /**
     * Menandai apakah struk memiliki setidaknya satu field yang diragukan/tidak terbaca.
     */
    val hasLowConfidenceField: Boolean
        get() = isMerchantLowConfidence || isDateLowConfidence || isTotalLowConfidence || items.any { it.isLowConfidence }
}
