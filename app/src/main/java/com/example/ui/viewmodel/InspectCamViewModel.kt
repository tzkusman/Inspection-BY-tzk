package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.InspectCamDatabase
import com.example.data.export.BackupRestoreUtil
import com.example.data.export.PdfReportGenerator
import com.example.data.export.ZipExportUtil
import com.example.data.model.CategoryEntity
import com.example.data.model.InspectionEntity
import com.example.data.model.PhotoEntity
import com.example.data.repository.CategoryRepository
import com.example.data.repository.InspectionRepository
import com.example.data.repository.PhotoRepository
import com.example.data.repository.SettingsRepository
import com.example.util.BarcodeScannerHelper
import com.example.util.FileProviderHelper
import com.example.util.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class Screen {
    object Home : Screen()
    data class InspectionView(val inspectionId: Long) : Screen()
    data class CameraView(val inspectionId: Long, val initialCategoryId: Long) : Screen()
    data class PhotoDetail(val inspectionId: Long, val photoId: Long) : Screen()
    data class CategoryManager(val inspectionId: Long) : Screen()
    object Settings : Screen()
}

data class InspectionWithCount(
    val inspection: InspectionEntity,
    val photoCount: Int
)

class InspectCamViewModel(application: Application) : AndroidViewModel(application) {

    private val db = InspectCamDatabase.getInstance(application)
    val inspectionRepo = InspectionRepository(db.inspectionDao(), db.categoryDao(), db.photoDao())
    val categoryRepo = CategoryRepository(db.categoryDao(), db.photoDao())
    val photoRepo = PhotoRepository(db.photoDao())
    val settingsRepo = SettingsRepository(application)

    // Navigation Backstack
    private val screenStack = mutableListOf<Screen>(Screen.Home)
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Search query on Home screen
    val homeSearchQuery = MutableStateFlow("")

    // Selected Category in Inspection View (null = "All")
    val selectedCategoryId = MutableStateFlow<Long?>(null)

    // UI Feedback Toast
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    // Export progress/loading state
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    // Reactive inspections list with photo counts
    val allInspectionsWithCount: StateFlow<List<InspectionWithCount>> = combine(
        inspectionRepo.allInspections,
        homeSearchQuery
    ) { inspections, query ->
        val filtered = if (query.isBlank()) {
            inspections
        } else {
            val q = query.trim().lowercase()
            inspections.filter {
                it.styleNo.lowercase().contains(q) ||
                it.color.lowercase().contains(q) ||
                it.companyName.lowercase().contains(q) ||
                it.buyer.lowercase().contains(q) ||
                it.reportTitle.lowercase().contains(q)
            }
        }
        filtered.map { insp ->
            val count = db.photoDao().getPhotoCountForInspectionSync(insp.id)
            InspectionWithCount(insp, count)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun navigateTo(screen: Screen) {
        screenStack.add(screen)
        _currentScreen.value = screen
    }

    fun navigateBack(): Boolean {
        if (screenStack.size > 1) {
            screenStack.removeAt(screenStack.lastIndex)
            _currentScreen.value = screenStack.last()
            return true
        }
        return false
    }

    // CRUD - Inspection
    fun createInspection(
        companyName: String,
        reportTitle: String,
        styleNo: String,
        color: String,
        buyer: String,
        inspectorName: String,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch {
            // Save defaults
            settingsRepo.defaultCompanyName = companyName
            settingsRepo.defaultInspectorName = inspectorName

            val id = inspectionRepo.createInspection(
                companyName = companyName,
                reportTitle = reportTitle,
                styleNo = styleNo,
                color = color,
                buyer = buyer,
                inspectorName = inspectorName
            )
            _toastMessage.emit("Inspection created successfully")
            onCreated(id)
        }
    }

    fun updateInspection(inspection: InspectionEntity) {
        viewModelScope.launch {
            inspectionRepo.updateInspection(inspection)
            _toastMessage.emit("Inspection updated")
        }
    }

    fun deleteInspection(id: Long) {
        viewModelScope.launch {
            inspectionRepo.deleteInspection(id)
            _toastMessage.emit("Inspection deleted")
            if (_currentScreen.value is Screen.InspectionView) {
                navigateBack()
            }
        }
    }

    // Category Operations
    fun addCategory(inspectionId: Long, name: String, onAdded: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = categoryRepo.addCategory(inspectionId, name)
            _toastMessage.emit("Category '$name' added")
            onAdded?.invoke(id)
        }
    }

    fun renameCategory(id: Long, newName: String) {
        viewModelScope.launch {
            categoryRepo.renameCategory(id, newName)
            _toastMessage.emit("Category renamed")
        }
    }

    fun reorderCategories(categories: List<CategoryEntity>) {
        viewModelScope.launch {
            categoryRepo.reorderCategories(categories)
        }
    }

    fun deleteCategory(category: CategoryEntity, moveToCategoryId: Long?) {
        viewModelScope.launch {
            categoryRepo.deleteCategory(category, moveToCategoryId)
            _toastMessage.emit("Category deleted")
            if (selectedCategoryId.value == category.id) {
                selectedCategoryId.value = null
            }
        }
    }

    // Photo Capture & Storage
    suspend fun getSuggestedFileName(categoryId: Long, categoryName: String): String {
        return photoRepo.generateNextFileName(categoryId, categoryName)
    }

    fun saveCapturedPhoto(
        inspectionId: Long,
        categoryId: Long,
        desiredFileName: String,
        rawImageFile: File,
        comment: String?,
        barcodeValue: String?,
        onSuccess: (PhotoEntity) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val (maxDim, quality) = settingsRepo.getImageQualityConfig()
                val processed = ImageProcessor.processAndSaveImage(
                    context = getApplication(),
                    rawFile = rawImageFile,
                    inspectionId = inspectionId,
                    maxLongDimension = maxDim,
                    quality = quality
                )

                val uniqueName = photoRepo.ensureUniqueFileName(categoryId, desiredFileName)

                val photo = PhotoEntity(
                    inspectionId = inspectionId,
                    categoryId = categoryId,
                    fileName = uniqueName,
                    imagePath = processed.imagePath,
                    thumbnailPath = processed.thumbnailPath,
                    barcodeValue = barcodeValue?.takeIf { it.isNotBlank() },
                    comment = comment?.takeIf { it.isNotBlank() },
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val photoId = photoRepo.savePhoto(photo)
                val savedPhoto = photo.copy(id = photoId)
                _toastMessage.emit("Photo saved as $uniqueName")
                onSuccess(savedPhoto)
            } catch (e: Exception) {
                e.printStackTrace()
                _toastMessage.emit("Failed to save photo: ${e.message}")
            }
        }
    }

    fun saveGalleryPhoto(
        inspectionId: Long,
        categoryId: Long,
        desiredFileName: String,
        uri: Uri,
        comment: String?,
        barcodeValue: String?,
        onSuccess: (PhotoEntity) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val (maxDim, quality) = settingsRepo.getImageQualityConfig()
                val processed = ImageProcessor.processUriImage(
                    context = getApplication(),
                    uri = uri,
                    inspectionId = inspectionId,
                    maxLongDimension = maxDim,
                    quality = quality
                )

                val uniqueName = photoRepo.ensureUniqueFileName(categoryId, desiredFileName)

                val photo = PhotoEntity(
                    inspectionId = inspectionId,
                    categoryId = categoryId,
                    fileName = uniqueName,
                    imagePath = processed.imagePath,
                    thumbnailPath = processed.thumbnailPath,
                    barcodeValue = barcodeValue?.takeIf { it.isNotBlank() },
                    comment = comment?.takeIf { it.isNotBlank() },
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val photoId = photoRepo.savePhoto(photo)
                val savedPhoto = photo.copy(id = photoId)
                _toastMessage.emit("Photo saved as $uniqueName")
                onSuccess(savedPhoto)
            } catch (e: Exception) {
                e.printStackTrace()
                _toastMessage.emit("Failed to import photo: ${e.message}")
            }
        }
    }

    fun updatePhotoDetails(photo: PhotoEntity) {
        viewModelScope.launch {
            val uniqueName = photoRepo.ensureUniqueFileName(
                categoryId = photo.categoryId,
                desiredName = photo.fileName,
                excludePhotoId = photo.id
            )
            photoRepo.updatePhoto(photo.copy(fileName = uniqueName))
            _toastMessage.emit("Photo updated")
        }
    }

    fun replacePhotoImage(photo: PhotoEntity, newRawFile: File) {
        viewModelScope.launch {
            try {
                val (maxDim, quality) = settingsRepo.getImageQualityConfig()
                val processed = ImageProcessor.processAndSaveImage(
                    context = getApplication(),
                    rawFile = newRawFile,
                    inspectionId = photo.inspectionId,
                    maxLongDimension = maxDim,
                    quality = quality
                )
                // Delete old files
                try {
                    File(photo.imagePath).delete()
                    File(photo.thumbnailPath).delete()
                } catch (_: Exception) {}

                val updated = photo.copy(
                    imagePath = processed.imagePath,
                    thumbnailPath = processed.thumbnailPath,
                    updatedAt = System.currentTimeMillis()
                )
                photoRepo.updatePhoto(updated)
                _toastMessage.emit("Photo replaced successfully")
            } catch (e: Exception) {
                _toastMessage.emit("Failed to replace image: ${e.message}")
            }
        }
    }

    fun deletePhoto(photo: PhotoEntity) {
        viewModelScope.launch {
            photoRepo.deletePhoto(photo)
            _toastMessage.emit("Photo deleted")
            if (_currentScreen.value is Screen.PhotoDetail) {
                navigateBack()
            }
        }
    }

    suspend fun scanBarcodeFromImage(bitmap: Bitmap): String? {
        return BarcodeScannerHelper.scanBarcodeFromBitmap(bitmap)
    }

    // Export PDF
    fun exportPdf(
        inspection: InspectionEntity,
        categories: List<CategoryEntity>,
        photos: List<PhotoEntity>,
        options: PdfReportGenerator.ExportOptions = PdfReportGenerator.ExportOptions()
    ) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val pdfFile = PdfReportGenerator.generateInspectionPdf(
                    context = getApplication(),
                    inspection = inspection,
                    categories = categories,
                    photos = photos,
                    options = options
                )
                FileProviderHelper.shareFile(
                    context = getApplication(),
                    file = pdfFile,
                    mimeType = "application/pdf",
                    chooserTitle = "Export & Share Inspection PDF"
                )
                _toastMessage.emit("PDF report generated successfully")
            } catch (e: Exception) {
                e.printStackTrace()
                _toastMessage.emit("PDF generation failed: ${e.message}")
            } finally {
                _isExporting.value = false
            }
        }
    }

    // Export ZIP
    fun exportZip(
        inspection: InspectionEntity,
        categories: List<CategoryEntity>,
        photos: List<PhotoEntity>
    ) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val zipFile = ZipExportUtil.createPhotosZip(
                    context = getApplication(),
                    inspection = inspection,
                    categories = categories,
                    photos = photos
                )
                FileProviderHelper.shareFile(
                    context = getApplication(),
                    file = zipFile,
                    mimeType = "application/zip",
                    chooserTitle = "Share Photos ZIP Archive"
                )
                _toastMessage.emit("Photos ZIP generated successfully")
            } catch (e: Exception) {
                e.printStackTrace()
                _toastMessage.emit("ZIP creation failed: ${e.message}")
            } finally {
                _isExporting.value = false
            }
        }
    }

    // Settings Backup & Restore
    fun createFullBackup() {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val backupFile = BackupRestoreUtil.createFullBackup(getApplication(), db)
                FileProviderHelper.shareFile(
                    context = getApplication(),
                    file = backupFile,
                    mimeType = "application/zip",
                    chooserTitle = "Export InspectCam Backup"
                )
                _toastMessage.emit("Backup created successfully")
            } catch (e: Exception) {
                e.printStackTrace()
                _toastMessage.emit("Backup failed: ${e.message}")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun restoreFullBackup(uri: Uri) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val tempZip = File(getApplication<Application>().cacheDir, "restore_${System.currentTimeMillis()}.zip")
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { inStream ->
                    tempZip.outputStream().use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                val success = BackupRestoreUtil.restoreFullBackup(getApplication(), db, tempZip)
                tempZip.delete()
                if (success) {
                    _toastMessage.emit("Data restored successfully")
                } else {
                    _toastMessage.emit("Invalid backup file")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _toastMessage.emit("Restore failed: ${e.message}")
            } finally {
                _isExporting.value = false
            }
        }
    }
}
