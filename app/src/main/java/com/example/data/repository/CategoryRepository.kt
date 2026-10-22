package com.example.data.repository

import com.example.data.db.CategoryDao
import com.example.data.db.PhotoDao
import com.example.data.model.CategoryEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

class CategoryRepository(
    private val categoryDao: CategoryDao,
    private val photoDao: PhotoDao
) {
    fun getCategories(inspectionId: Long): Flow<List<CategoryEntity>> =
        categoryDao.getCategoriesForInspection(inspectionId)

    suspend fun getCategoriesSync(inspectionId: Long): List<CategoryEntity> =
        categoryDao.getCategoriesForInspectionSync(inspectionId)

    suspend fun addCategory(inspectionId: Long, name: String): Long {
        val existing = categoryDao.getCategoriesForInspectionSync(inspectionId)
        val maxOrder = existing.maxOfOrNull { it.sortOrder } ?: -1
        val newCategory = CategoryEntity(
            inspectionId = inspectionId,
            name = name.trim(),
            sortOrder = maxOrder + 1
        )
        return categoryDao.insertCategory(newCategory)
    }

    suspend fun renameCategory(categoryId: Long, newName: String) {
        val cat = categoryDao.getCategoryById(categoryId) ?: return
        categoryDao.updateCategory(cat.copy(name = newName.trim()))
    }

    suspend fun reorderCategories(categories: List<CategoryEntity>) {
        val updated = categories.mapIndexed { index, category ->
            category.copy(sortOrder = index)
        }
        categoryDao.updateCategories(updated)
    }

    suspend fun deleteCategory(category: CategoryEntity, moveToCategoryId: Long?) {
        if (moveToCategoryId != null) {
            photoDao.movePhotosToCategory(category.id, moveToCategoryId)
        } else {
            val photos = photoDao.getPhotosForCategorySync(category.id)
            for (p in photos) {
                try {
                    val f = File(p.imagePath)
                    if (f.exists()) f.delete()
                    val thumb = File(p.thumbnailPath)
                    if (thumb.exists()) thumb.delete()
                } catch (_: Exception) {}
            }
            photoDao.deletePhotosByCategoryId(category.id)
        }
        categoryDao.deleteCategory(category)
    }
}
