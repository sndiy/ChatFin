package com.sndiy.chatfin.feature.chat.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Picture
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ChartExportUtil {

    fun createBitmapFromPicture(picture: Picture): Bitmap {
        val width = picture.width.coerceAtLeast(1)
        val height = picture.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE) // Fill white background for clean visibility
        canvas.drawPicture(picture)
        return bitmap
    }

    suspend fun saveAndShareBitmap(context: Context, bitmap: Bitmap, title: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                val safeFileName = title.lowercase()
                    .replace(Regex("[^a-z0-9]"), "_")
                    .take(30) + "_${System.currentTimeMillis()}.png"

                // 1. Save PNG directly to Public Media Store Gallery (Pictures/ChatFin)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ChatFin")
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    }
                } else {
                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val chatFinDir = File(picturesDir, "ChatFin")
                    if (!chatFinDir.exists()) chatFinDir.mkdirs()
                    val file = File(chatFinDir, safeFileName)
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }

                // 2. Save backup file in cache for FileProvider Share Intent
                val cacheDir = File(context.cacheDir, "exported_charts")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val cacheFile = File(cacheDir, safeFileName)
                FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val shareUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "📈 Gambar tersimpan di Galeri Foto (Pictures/ChatFin)",
                        Toast.LENGTH_LONG
                    ).show()
                    shareImageUri(context, shareUri)
                }
                cacheFile
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal menyimpan gambar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
                null
            }
        }
    }

    private fun shareImageUri(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Bagikan Gambar Visualisasi"))
        } catch (e: Exception) {
            // Fallback
        }
    }
}
