package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageProcessor {

    data class ProcessedImage(
        val imagePath: String,
        val thumbnailPath: String,
        val width: Int,
        val height: Int
    )

    suspend fun processAndSaveImage(
        context: Context,
        rawFile: File,
        inspectionId: Long,
        maxLongDimension: Int = 1600,
        quality: Float = 0.8f
    ): ProcessedImage = withContext(Dispatchers.IO) {
        val inspectionDir = File(context.filesDir, "inspections/$inspectionId").apply {
            if (!exists()) mkdirs()
        }
        val thumbsDir = File(context.filesDir, "inspections/$inspectionId/thumbs").apply {
            if (!exists()) mkdirs()
        }

        val baseId = UUID.randomUUID().toString()
        val destImageFile = File(inspectionDir, "img_$baseId.jpg")
        val destThumbFile = File(thumbsDir, "thumb_$baseId.jpg")

        // Load bitmap respecting EXIF rotation
        val originalBitmap = decodeFileWithExif(rawFile)

        // Resize image so long dimension does not exceed maxLongDimension
        val resizedBitmap = resizeBitmap(originalBitmap, maxLongDimension)
        val jpegQuality = (quality * 100).toInt().coerceIn(10, 100)

        FileOutputStream(destImageFile).use { out ->
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
        }

        // Generate thumbnail (max 256px)
        val thumbBitmap = resizeBitmap(resizedBitmap, 256)
        FileOutputStream(destThumbFile).use { out ->
            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }

        val width = resizedBitmap.width
        val height = resizedBitmap.height

        if (originalBitmap != resizedBitmap) originalBitmap.recycle()
        resizedBitmap.recycle()
        thumbBitmap.recycle()

        // Clean up raw temp file if different
        if (rawFile.absolutePath != destImageFile.absolutePath && rawFile.exists()) {
            rawFile.delete()
        }

        ProcessedImage(
            imagePath = destImageFile.absolutePath,
            thumbnailPath = destThumbFile.absolutePath,
            width = width,
            height = height
        )
    }

    suspend fun processUriImage(
        context: Context,
        uri: Uri,
        inspectionId: Long,
        maxLongDimension: Int = 1600,
        quality: Float = 0.8f
    ): ProcessedImage = withContext(Dispatchers.IO) {
        val tempRawFile = File(context.cacheDir, "temp_picker_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempRawFile).use { output ->
                input.copyTo(output)
            }
        }
        processAndSaveImage(context, tempRawFile, inspectionId, maxLongDimension, quality)
    }

    private fun decodeFileWithExif(file: File): Bitmap {
        var degree = 0
        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            degree = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {}

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IllegalStateException("Failed to decode image at ${file.absolutePath}")

        return if (degree != 0) {
            val matrix = Matrix().apply { postRotate(degree.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            rotated
        } else {
            bitmap
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxSide = maxOf(width, height)
        if (maxSide <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / maxSide.toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
