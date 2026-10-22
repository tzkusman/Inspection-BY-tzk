package com.example.data.repository

import com.example.data.db.PhotoDao
import com.example.data.model.PhotoEntity
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.Locale

class PhotoRepository(
    private val photoDao: PhotoDao
) {
    fun getPhotosForInspection(inspectionId: Long): Flow<List<PhotoEntity>> =
        photoDao.getPhotosForInspection(inspectionId)

    suspend fun getPhotosForInspectionSync(inspectionId: Long): List<PhotoEntity> =
        photoDao.getPhotosForInspectionSync(inspectionId)

    fun getPhotosForCategory(categoryId: Long): Flow<List<PhotoEntity>> =
        photoDao.getPhotosForCategory(categoryId)

    suspend fun getPhotosForCategorySync(categoryId: Long): List<PhotoEntity> =
        photoDao.getPhotosForCategorySync(categoryId)

    fun getPhoto(id: Long): Flow<PhotoEntity?> = photoDao.getPhotoById(id)

    suspend fun getPhotoSync(id: Long): PhotoEntity? = photoDao.getPhotoByIdSync(id)

    fun getPhotoCountForCategory(categoryId: Long): Flow<Int> =
        photoDao.getPhotoCountForCategory(categoryId)

    suspend fun savePhoto(photo: PhotoEntity): Long = photoDao.insertPhoto(photo)

    suspend fun updatePhoto(photo: PhotoEntity) {
        photoDao.updatePhoto(photo.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePhoto(photo: PhotoEntity) {
        try {
            val f = File(photo.imagePath)
            if (f.exists()) f.delete()
            val thumb = File(photo.thumbnailPath)
            if (thumb.exists()) thumb.delete()
        } catch (_: Exception) {}
        photoDao.deletePhoto(photo)
    }

    /**
     * Generates the next smart sequence file name formatted as "<Category> 01", "<Category> 02", etc.
     */
    suspend fun generateNextFileName(categoryId: Long, categoryName: String): String {
        val existingPhotos = photoDao.getPhotosForCategorySync(categoryId)
        val prefix = categoryName.trim()
        val regex = Regex("""^${Regex.escape(prefix)}\s+(\d+)$""", RegexOption.IGNORE_CASE)

        var highestNum = 0
        for (photo in existingPhotos) {
            val match = regex.find(photo.fileName.trim())
            if (match != null) {
                val num = match.groupValues[1].toIntOrNull() ?: 0
                if (num > highestNum) {
                    highestNum = num
                }
            }
        }

        val nextNum = highestNum + 1
        return String.format(Locale.US, "%s %02d", prefix, nextNum)
    }

    /**
     * Ensures file name is unique within the category; auto-appends number if duplicate.
     */
    suspend fun ensureUniqueFileName(
        categoryId: Long,
        desiredName: String,
        excludePhotoId: Long = 0
    ): String {
        val existingPhotos = photoDao.getPhotosForCategorySync(categoryId)
            .filter { it.id != excludePhotoId }
        val names = existingPhotos.map { it.fileName.trim().lowercase(Locale.US) }.toSet()

        val trimmed = desiredName.trim().ifEmpty { "Photo" }
        if (!names.contains(trimmed.lowercase(Locale.US))) {
            return trimmed
        }

        var counter = 2
        while (names.contains(String.format(Locale.US, "%s (%d)", trimmed, counter).lowercase(Locale.US))) {
            counter++
        }
        return String.format(Locale.US, "%s (%d)", trimmed, counter)
    }
}
