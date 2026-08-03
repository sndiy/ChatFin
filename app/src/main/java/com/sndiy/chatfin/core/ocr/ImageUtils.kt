package com.sndiy.chatfin.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageUtils {

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
