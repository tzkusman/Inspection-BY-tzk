package com.example.data.export

import android.content.Context
import com.example.data.db.InspectCamDatabase
import com.example.data.model.CategoryEntity
import com.example.data.model.InspectionEntity
import com.example.data.model.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupRestoreUtil {

    suspend fun createFullBackup(context: Context, database: InspectCamDatabase): File =
        withContext(Dispatchers.IO) {
            val backupFile = File(context.cacheDir, "InspectCam_Backup_${System.currentTimeMillis()}.zip")
            val inspections = database.inspectionDao().getAllInspections().first()

            val rootJson = JSONObject()
            val inspArray = JSONArray()

            ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
                for (insp in inspections) {
                    val inspObj = JSONObject().apply {
                        put("id", insp.id)
                        put("companyName", insp.companyName)
                        put("reportTitle", insp.reportTitle)
                        put("styleNo", insp.styleNo)
                        put("color", insp.color)
                        put("buyer", insp.buyer)
                        put("inspectorName", insp.inspectorName)
                        put("createdAt", insp.createdAt)
                        put("updatedAt", insp.updatedAt)
                    }

                    // Categories
                    val categories = database.categoryDao().getCategoriesForInspectionSync(insp.id)
                    val catArray = JSONArray()
                    for (cat in categories) {
                        catArray.put(JSONObject().apply {
                            put("id", cat.id)
                            put("inspectionId", cat.inspectionId)
                            put("name", cat.name)
                            put("sortOrder", cat.sortOrder)
                        })
                    }
                    inspObj.put("categories", catArray)

                    // Photos
                    val photos = database.photoDao().getPhotosForInspectionSync(insp.id)
                    val photoArray = JSONArray()
                    for (photo in photos) {
                        val pObj = JSONObject().apply {
                            put("id", photo.id)
                            put("inspectionId", photo.inspectionId)
                            put("categoryId", photo.categoryId)
                            put("fileName", photo.fileName)
                            put("barcodeValue", photo.barcodeValue ?: "")
                            put("comment", photo.comment ?: "")
                            put("createdAt", photo.createdAt)
                            put("updatedAt", photo.updatedAt)
                        }

                        // Add original image to ZIP
                        val imgFile = File(photo.imagePath)
                        if (imgFile.exists()) {
                            val zipEntryName = "images/img_${photo.id}.jpg"
                            pObj.put("zipEntryName", zipEntryName)
                            zipOut.putNextEntry(ZipEntry(zipEntryName))
                            FileInputStream(imgFile).use { it.copyTo(zipOut) }
                            zipOut.closeEntry()
                        }
                        photoArray.put(pObj)
                    }
                    inspObj.put("photos", photoArray)
                    inspArray.put(inspObj)
                }

                rootJson.put("version", 1)
                rootJson.put("exportedAt", System.currentTimeMillis())
                rootJson.put("inspections", inspArray)

                // Write metadata.json entry
                zipOut.putNextEntry(ZipEntry("backup_metadata.json"))
                zipOut.write(rootJson.toString(2).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }

            backupFile
        }

    suspend fun restoreFullBackup(context: Context, database: InspectCamDatabase, zipFile: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val tempDir = File(context.cacheDir, "restore_temp_${System.currentTimeMillis()}").apply { mkdirs() }

                // Extract ZIP
                ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val outFile = File(tempDir, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { zipIn.copyTo(it) }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }

                val metadataFile = File(tempDir, "backup_metadata.json")
                if (!metadataFile.exists()) return@withContext false

                val jsonContent = metadataFile.readText(Charsets.UTF_8)
                val rootJson = JSONObject(jsonContent)
                val inspArray = rootJson.getJSONArray("inspections")

                for (i in 0 until inspArray.length()) {
                    val inspObj = inspArray.getJSONObject(i)
                    val newInspection = InspectionEntity(
                        companyName = inspObj.getString("companyName"),
                        reportTitle = inspObj.getString("reportTitle"),
                        styleNo = inspObj.getString("styleNo"),
                        color = inspObj.getString("color"),
                        buyer = inspObj.optString("buyer", ""),
                        inspectorName = inspObj.getString("inspectorName"),
                        createdAt = inspObj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = inspObj.optLong("updatedAt", System.currentTimeMillis())
                    )
                    val newInspId = database.inspectionDao().insertInspection(newInspection)

                    val oldToNewCatMap = mutableMapOf<Long, Long>()
                    val catArray = inspObj.getJSONArray("categories")
                    for (c in 0 until catArray.length()) {
                        val catObj = catArray.getJSONObject(c)
                        val oldCatId = catObj.getLong("id")
                        val newCat = CategoryEntity(
                            inspectionId = newInspId,
                            name = catObj.getString("name"),
                            sortOrder = catObj.getInt("sortOrder")
                        )
                        val newCatId = database.categoryDao().insertCategory(newCat)
                        oldToNewCatMap[oldCatId] = newCatId
                    }

                    val photoArray = inspObj.getJSONArray("photos")
                    val inspectionDir = File(context.filesDir, "inspections/$newInspId").apply { mkdirs() }
                    val thumbsDir = File(context.filesDir, "inspections/$newInspId/thumbs").apply { mkdirs() }

                    for (p in 0 until photoArray.length()) {
                        val pObj = photoArray.getJSONObject(p)
                        val oldCatId = pObj.getLong("categoryId")
                        val targetCatId = oldToNewCatMap[oldCatId] ?: continue
                        val zipEntryName = pObj.optString("zipEntryName", "")

                        var imagePath = ""
                        var thumbPath = ""

                        if (zipEntryName.isNotEmpty()) {
                            val extractedImg = File(tempDir, zipEntryName)
                            if (extractedImg.exists()) {
                                val destImg = File(inspectionDir, "img_${System.currentTimeMillis()}_$p.jpg")
                                val destThumb = File(thumbsDir, "thumb_${System.currentTimeMillis()}_$p.jpg")
                                extractedImg.copyTo(destImg, overwrite = true)
                                extractedImg.copyTo(destThumb, overwrite = true)
                                imagePath = destImg.absolutePath
                                thumbPath = destThumb.absolutePath
                            }
                        }

                        if (imagePath.isNotEmpty()) {
                            val newPhoto = PhotoEntity(
                                inspectionId = newInspId,
                                categoryId = targetCatId,
                                fileName = pObj.getString("fileName"),
                                imagePath = imagePath,
                                thumbnailPath = thumbPath,
                                barcodeValue = pObj.optString("barcodeValue").takeIf { it.isNotBlank() },
                                comment = pObj.optString("comment").takeIf { it.isNotBlank() },
                                createdAt = pObj.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = pObj.optLong("updatedAt", System.currentTimeMillis())
                            )
                            database.photoDao().insertPhoto(newPhoto)
                        }
                    }
                }

                tempDir.deleteRecursively()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
}
