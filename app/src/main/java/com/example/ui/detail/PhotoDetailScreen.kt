package com.example.ui.detail

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CategoryEntity
import com.example.data.model.PhotoEntity
import com.example.ui.theme.AmberPrimary
import com.example.ui.viewmodel.InspectCamViewModel
import com.example.util.FileProviderHelper
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    inspectionId: Long,
    photoId: Long,
    viewModel: InspectCamViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentPhotoId by remember { mutableStateOf(photoId) }

    val photosFlow = remember(inspectionId) { viewModel.photoRepo.getPhotosForInspection(inspectionId) }
    val allPhotos by photosFlow.collectAsState(initial = emptyList())

    val currentPhoto = allPhotos.firstOrNull { it.id == currentPhotoId }

    val categoriesFlow = remember(inspectionId) { viewModel.categoryRepo.getCategories(inspectionId) }
    val categories by categoriesFlow.collectAsState(initial = emptyList())

    // Photos in same category for navigation
    val categoryPhotos = remember(allPhotos, currentPhoto) {
        if (currentPhoto != null) {
            allPhotos.filter { it.categoryId == currentPhoto.categoryId }
        } else {
            emptyList()
        }
    }
    val currentIndex = categoryPhotos.indexOfFirst { it.id == currentPhotoId }

    // Editable form state
    var editFileName by remember { mutableStateOf("") }
    var editCategoryId by remember { mutableStateOf(0L) }
    var editComment by remember { mutableStateOf("") }
    var editBarcode by remember { mutableStateOf("") }

    LaunchedEffect(currentPhoto) {
        currentPhoto?.let {
            editFileName = it.fileName
            editCategoryId = it.categoryId
            editComment = it.comment.orEmpty()
            editBarcode = it.barcodeValue.orEmpty()
        }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var categoryDropdownOpen by remember { mutableStateOf(false) }

    // Pinch-to-zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Reset zoom when switching photos
    LaunchedEffect(currentPhotoId) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    // Retake / Replace image via Gallery Picker
    val replaceImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && currentPhoto != null) {
            scope.launch {
                val tempFile = File(context.cacheDir, "replace_${UUID.randomUUID()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    tempFile.outputStream().use { outStream -> inStream.copyTo(outStream) }
                }
                viewModel.replacePhotoImage(currentPhoto, tempFile)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = editFileName.ifEmpty { "Photo Detail" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (categoryPhotos.isNotEmpty() && currentIndex >= 0) {
                            Text(
                                text = "${currentIndex + 1} of ${categoryPhotos.size} in category",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            currentPhoto?.let { photo ->
                                val file = File(photo.imagePath).takeIf { it.exists() }
                                    ?: File(photo.thumbnailPath)
                                if (file.exists()) {
                                    val caption = "${photo.fileName}\n${photo.comment.orEmpty()}"
                                    FileProviderHelper.shareImage(context, file, caption)
                                }
                            }
                        },
                        modifier = Modifier.testTag("share_photo_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }

                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("delete_photo_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (currentPhoto == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Photo not found")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Interactive Zoomable Image Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .background(Color.Black)
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val imgFile = File(currentPhoto.imagePath).takeIf { it.exists() }
                        ?: File(currentPhoto.thumbnailPath)

                    AsyncImage(
                        model = imgFile,
                        contentDescription = currentPhoto.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = maxOf(1f, minOf(scale, 4f)),
                                scaleY = maxOf(1f, minOf(scale, 4f)),
                                translationX = offsetX,
                                translationY = offsetY
                            )
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 4f)
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            }
                    )

                    // Navigation Arrows Overlay (Left / Right within category)
                    if (currentIndex > 0) {
                        IconButton(
                            onClick = {
                                currentPhotoId = categoryPhotos[currentIndex - 1].id
                            },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous", tint = Color.White)
                        }
                    }

                    if (currentIndex < categoryPhotos.size - 1) {
                        IconButton(
                            onClick = {
                                currentPhotoId = categoryPhotos[currentIndex + 1].id
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", tint = Color.White)
                        }
                    }

                    // Pinch Hint Pill
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Pinch to zoom",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Metadata Form & Actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // File Name
                    OutlinedTextField(
                        value = editFileName,
                        onValueChange = { editFileName = it },
                        label = { Text("File Name *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_file_name_input")
                    )

                    // Category Dropdown (Move to another category)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val selectedCatName = categories.firstOrNull { it.id == editCategoryId }?.name ?: "Category"
                        OutlinedTextField(
                            value = selectedCatName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category (Move to another)") },
                            trailingIcon = {
                                IconButton(onClick = { categoryDropdownOpen = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { categoryDropdownOpen = true }
                                .testTag("edit_category_selector")
                        )

                        DropdownMenu(
                            expanded = categoryDropdownOpen,
                            onDismissRequest = { categoryDropdownOpen = false }
                        ) {
                            for (cat in categories) {
                                DropdownMenuItem(
                                    text = { Text(cat.name, fontWeight = if (cat.id == editCategoryId) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        editCategoryId = cat.id
                                        categoryDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }

                    // Barcode Value
                    OutlinedTextField(
                        value = editBarcode,
                        onValueChange = { editBarcode = it },
                        label = { Text("Barcode Value") },
                        placeholder = { Text("No barcode attached") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_barcode_input")
                    )

                    // Comment (Multi-line)
                    OutlinedTextField(
                        value = editComment,
                        onValueChange = { editComment = it },
                        label = { Text("Comment") },
                        placeholder = { Text("Observations, fabric flaw, stitching notes...") },
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_comment_input")
                    )

                    // Timestamp info
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    Text(
                        text = "Captured: ${dateFormat.format(Date(currentPhoto.createdAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action Buttons: Save Changes & Retake / Replace
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                replaceImageLauncher.launch("image/*")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("retake_replace_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retake Photo")
                        }

                        Button(
                            onClick = {
                                val updated = currentPhoto.copy(
                                    fileName = editFileName.trim(),
                                    categoryId = editCategoryId,
                                    comment = editComment.trim().takeIf { it.isNotBlank() },
                                    barcodeValue = editBarcode.trim().takeIf { it.isNotBlank() }
                                )
                                viewModel.updatePhotoDetails(updated)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("save_changes_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Changes", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Delete Confirmation
    if (showDeleteConfirm && currentPhoto != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Photo?") },
            text = { Text("Are you sure you want to permanently delete '${currentPhoto.fileName}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deletePhoto(currentPhoto)
                    },
                    modifier = Modifier.testTag("confirm_delete_photo_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
