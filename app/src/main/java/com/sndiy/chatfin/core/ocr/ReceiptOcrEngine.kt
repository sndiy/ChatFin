package com.sndiy.chatfin.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrAnalysisResult(
    val parsedReceipt: ParsedReceipt,
    val boundingBoxes: List<TextBoundingBox>
)

@Singleton
class ReceiptOcrEngine @Inject constructor() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Memproses gambar statis dari Galeri (Bitmap atau Uri) secara offline menggunakan ML Kit di background thread IO.
     */
    suspend fun processImage(context: Context, imageUri: Uri): OcrAnalysisResult = withContext(Dispatchers.IO) {
        val inputImage = InputImage.fromFilePath(context, imageUri)
        processInputImage(inputImage)
    }

    /**
     * Memproses Bitmap statis secara offline di background thread Default.
     */
    suspend fun processBitmap(bitmap: Bitmap, rotationDegrees: Int = 0): OcrAnalysisResult = withContext(Dispatchers.Default) {
        val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
        processInputImage(inputImage)
    }

    /**
     * Memproses InputImage ML Kit dan memparsing text menggunakan Coroutine Background Dispatcher
     * agar UI thread tidak mengalami stutter / jank saat loading.
     */
    suspend fun processInputImage(inputImage: InputImage): OcrAnalysisResult = withContext(Dispatchers.Default) {
        suspendCancellableCoroutine { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val rawText = visionText.text
                    val parsed = ReceiptParser.parse(rawText)

                    val boxes = mutableListOf<TextBoundingBox>()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val rect = line.boundingBox
                            if (rect != null) {
                                boxes.add(
                                    TextBoundingBox(
                                        text = line.text,
                                        boundingBox = RectF(rect)
                                    )
                                )
                            }
                        }
                    }

                    continuation.resume(OcrAnalysisResult(parsedReceipt = parsed, boundingBoxes = boxes))
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }
    }
}
