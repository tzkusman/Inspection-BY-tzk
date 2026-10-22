package com.example.data.repository

import com.example.data.db.CategoryDao
import com.example.data.db.InspectCamDatabase
import com.example.data.db.InspectionDao
import com.example.data.db.PhotoDao
import com.example.data.model.CategoryEntity
import com.example.data.model.InspectionEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

class InspectionRepository(
    private val inspectionDao: InspectionDao,
    private val categoryDao: CategoryDao,
    private val photoDao: PhotoDao
) {
    val allInspections: Flow<List<InspectionEntity>> = inspectionDao.getAllInspections()

    fun getInspection(id: Long): Flow<InspectionEntity?> = inspectionDao.getInspectionById(id)

    suspend fun getInspectionSync(id: Long): InspectionEntity? = inspectionDao.getInspectionByIdSync(id)

    suspend fun createInspection(
        companyName: String,
        reportTitle: String,
        styleNo: String,
        color: String,
        buyer: String,
        inspectorName: String
    ): Long {
        val inspection = InspectionEntity(
            companyName = companyName.trim(),
            reportTitle = reportTitle.trim().ifEmpty { "Washing & Styling Comparison" },
            styleNo = styleNo.trim(),
            color = color.trim(),
            buyer = buyer.trim(),
            inspectorName = inspectorName.trim(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val inspectionId = inspectionDao.insertInspection(inspection)

        // Seed default 10 garment categories
        val defaultCategories = InspectCamDatabase.DEFAULT_CATEGORIES.mapIndexed { index, name ->
            CategoryEntity(
                inspectionId = inspectionId,
                name = name,
                sortOrder = index
            )
        }
        categoryDao.insertCategories(defaultCategories)

        return inspectionId
    }

    suspend fun updateInspection(inspection: InspectionEntity) {
        inspectionDao.updateInspection(inspection.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteInspection(inspectionId: Long) {
        // Delete all photo files from disk
        val photos = photoDao.getPhotosForInspectionSync(inspectionId)
        for (photo in photos) {
            try {
                val imgFile = File(photo.imagePath)
                if (imgFile.exists()) imgFile.delete()
                val thumbFile = File(photo.thumbnailPath)
                if (thumbFile.exists()) thumbFile.delete()
            } catch (_: Exception) {}
        }
        inspectionDao.deleteInspectionById(inspectionId)
    }

    suspend fun getPhotoCount(inspectionId: Long): Int {
        return photoDao.getPhotoCountForInspectionSync(inspectionId)
    }
}
