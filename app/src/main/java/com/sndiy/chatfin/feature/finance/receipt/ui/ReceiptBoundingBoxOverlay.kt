package com.sndiy.chatfin.feature.finance.receipt.ui

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sndiy.chatfin.core.ocr.TextBoundingBox

/**
 * Overlay AR real-time yang menggambar kotak sorotan (bounding box) di atas preview kamera
 * menandai teks struk yang berhasil dibaca oleh ML Kit OCR.
 */
@Composable
fun ReceiptBoundingBoxOverlay(
    boundingBoxes: List<TextBoundingBox>,
    sourceImageWidth: Int,
    sourceImageHeight: Int,
    modifier: Modifier = Modifier,
    boxColor: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (sourceImageWidth <= 0 || sourceImageHeight <= 0 || boundingBoxes.isEmpty()) return@Canvas

        val scaleX = size.width / sourceImageWidth.toFloat()
        val scaleY = size.height / sourceImageHeight.toFloat()

        for (box in boundingBoxes) {
            val rect = box.boundingBox
            val left = rect.left * scaleX
            val top = rect.top * scaleY
            val right = rect.right * scaleX
            val bottom = rect.bottom * scaleY

            // Gambar latar belakang transparan di dalam kotak teks
            drawRoundRect(
                color = boxColor.copy(alpha = 0.15f),
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )

            // Gambar garis tepi glowing overlay AR
            drawRoundRect(
                color = boxColor.copy(alpha = 0.85f),
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )
            )
        }
    }
}
