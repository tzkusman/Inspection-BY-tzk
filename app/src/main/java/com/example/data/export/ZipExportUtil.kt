package com.example.data.export

import android.content.Context
import com.example.data.model.CategoryEntity
import com.example.data.model.InspectionEntity
import com.example.data.model.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipExportUtil {

    suspend fun createPhotosZip(
        context: Context,
        inspection: InspectionEntity,
        categories: List<CategoryEntity>,
        photos: List<PhotoEntity>
    ): File = withContext(Dispatchers.IO) {
        val categoryMap = categories.associateBy { it.id }
        val cleanStyle = inspection.styleNo.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val zipFile = File(context.cacheDir, "Photos_${cleanStyle}_${System.currentTimeMillis()}.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            val usedNames = mutableSetOf<String>()

            for (photo in photos) {
                val imgFile = File(photo.imagePath).takeIf { it.exists() } ?: continue
                val catName = categoryMap[photo.categoryId]?.name ?: "General"
                val sanitizedCat = catName.replace(Regex("[^a-zA-Z0-9 _-]"), "_")
                val sanitizedFileName = photo.fileName.replace(Regex("[^a-zA-Z0-9 _-]"), "_")

                var entryName = "$sanitizedCat/$sanitizedFileName.jpg"
                var counter = 2
                while (usedNames.contains(entryName)) {
                    entryName = "$sanitizedCat/${sanitizedFileName}_$counter.jpg"
                    counter++
                }
                usedNames.add(entryName)

                val zipEntry = ZipEntry(entryName)
                zipOut.putNextEntry(zipEntry)

                FileInputStream(imgFile).use { inStream ->
                    inStream.copyTo(zipOut)
                }
                zipOut.closeEntry()
            }
        }

        zipFile
    }
}
