package com.sndiy.chatfin.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageUtils {

    /**
     * Memuat gambar struk (dari file:// Uri hasil [saveImageToInternalStorage]/[saveBitmapToInternalStorage])
     * diturunkan resolusinya sebelum dikirim ke API AI — menekan latensi & ukuran payload,
     * struk masih terbaca jelas di batas [maxDimension].
     */
    suspend fun loadScaledBitmap(
        context: Context,
        uriString: String,
        maxDimension: Int = 1536
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // Dengan inJustDecodeBounds=true, decodeStream SELALU mengembalikan null by design
            // (cuma mengisi bounds.outWidth/outHeight, tidak membuat Bitmap) — jangan pakai nilai
            // baliknya untuk deteksi gagal-buka-stream, itu bikin fungsi ini selalu return null.
            val boundsStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            boundsStream.use { input -> BitmapFactory.decodeStream(input, null, bounds) }

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Menyimpan Uri gambar dari galeri/kamera ke penyimpanan internal lokal aplikasi secara aman (I/O di Dispatchers.IO).
     */
    suspend fun saveImageToInternalStorage(context: Context, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val directory = File(context.filesDir, "receipts")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val destinationFile = File(directory, "receipt_${UUID.randomUUID()}.jpg")

            val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
            val outputStream = FileOutputStream(destinationFile)

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(destinationFile).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Menyimpan Bitmap ke penyimpanan internal lokal aplikasi.
     */
    suspend fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            val directory = File(context.filesDir, "receipts")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val destinationFile = File(directory, "receipt_${UUID.randomUUID()}.jpg")
            val outputStream = FileOutputStream(destinationFile)

            outputStream.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            Uri.fromFile(destinationFile).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
