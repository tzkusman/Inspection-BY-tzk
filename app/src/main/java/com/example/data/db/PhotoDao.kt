package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE inspectionId = :inspectionId ORDER BY createdAt ASC")
    fun getPhotosForInspection(inspectionId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE inspectionId = :inspectionId ORDER BY createdAt ASC")
    suspend fun getPhotosForInspectionSync(inspectionId: Long): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE categoryId = :categoryId ORDER BY createdAt ASC")
    fun getPhotosForCategory(categoryId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE categoryId = :categoryId ORDER BY createdAt ASC")
    suspend fun getPhotosForCategorySync(categoryId: Long): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id = :id")
    fun getPhotoById(id: Long): Flow<PhotoEntity?>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getPhotoByIdSync(id: Long): PhotoEntity?

    @Query("SELECT COUNT(*) FROM photos WHERE categoryId = :categoryId")
    fun getPhotoCountForCategory(categoryId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM photos WHERE inspectionId = :inspectionId")
    fun getPhotoCountForInspection(inspectionId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM photos WHERE inspectionId = :inspectionId")
    suspend fun getPhotoCountForInspectionSync(inspectionId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity): Long

    @Update
    suspend fun updatePhoto(photo: PhotoEntity)

    @Delete
    suspend fun deletePhoto(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhotoById(id: Long)

    @Query("UPDATE photos SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun movePhotosToCategory(oldCategoryId: Long, newCategoryId: Long)

    @Query("DELETE FROM photos WHERE categoryId = :categoryId")
    suspend fun deletePhotosByCategoryId(categoryId: Long)
}
