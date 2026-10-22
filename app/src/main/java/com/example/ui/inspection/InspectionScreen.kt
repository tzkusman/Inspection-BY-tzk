package com.example.ui.inspection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.export.PdfReportGenerator
import com.example.data.model.CategoryEntity
import com.example.data.model.PhotoEntity
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate700
import com.example.ui.viewmodel.InspectCamViewModel
import com.example.ui.viewmodel.Screen
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionScreen(
    inspectionId: Long,
    viewModel: InspectCamViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val inspectionFlow = remember(inspectionId) { viewModel.inspectionRepo.getInspection(inspectionId) }
    val inspection by inspectionFlow.collectAsState(initial = null)

    val categoriesFlow = remember(inspectionId) { viewModel.categoryRepo.getCategories(inspectionId) }
    val categories by categoriesFlow.collectAsState(initial = emptyList())

    val photosFlow = remember(inspectionId) { viewModel.photoRepo.getPhotosForInspection(inspectionId) }
    val allPhotos by photosFlow.collectAsState(initial = emptyList())

    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    val currentCategoryPhotos = remember(allPhotos, selectedCategoryId) {
        if (selectedCategoryId == null) {
            allPhotos
        } else {
            allPhotos.filter { it.categoryId == selectedCategoryId }
        }
    }

    val photoCountByCategoryId = remember(allPhotos) {
        allPhotos.groupingBy { it.categoryId }.eachCount()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = inspection?.styleNo ?: "Inspection",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (!inspection?.color.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = AmberPrimary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = inspection?.color.orEmpty(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = inspection?.reportTitle ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                        onClick = { viewModel.navigateTo(Screen.CategoryManager(inspectionId)) },
                        modifier = Modifier.testTag("manage_categories_button")
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = "Manage Categories")
                    }
                    IconButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.testTag("export_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export Report")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val defaultCatId = selectedCategoryId ?: categories.firstOrNull()?.id ?: 0L
                    viewModel.navigateTo(Screen.CameraView(inspectionId, defaultCatId))
                },
                containerColor = AmberPrimary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(64.dp)
                    .testTag("camera_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Take Photo",
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Horizontal Category Chips Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "All" chip
                item {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { viewModel.selectedCategoryId.value = null },
                        label = {
                            Text("All (${allPhotos.size})", fontWeight = FontWeight.SemiBold)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberPrimary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("category_chip_all")
                    )
                }

                // Category chips
                items(categories, key = { it.id }) { cat ->
                    val count = photoCountByCategoryId[cat.id] ?: 0
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = { viewModel.selectedCategoryId.value = cat.id },
                        label = {
                            Text("${cat.name} ($count)")
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberPrimary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("category_chip_${cat.id}")
                    )
                }

                // "+ Add Category" chip
                item {
                    FilterChip(
                        selected = false,
                        onClick = { showAddCategoryDialog = true },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Category") },
                        modifier = Modifier.testTag("add_category_chip")
                    )
                }
            }

            // 3-Column Grid View
            if (currentCategoryPhotos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Slate700,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        val currentCatName = categories.firstOrNull { it.id == selectedCategoryId }?.name
                        Text(
                            text = if (currentCatName != null) "No photos in $currentCatName" else "No photos taken yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the camera button below to take a photo. InspectCam will auto-suggest the filename.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                val targetCatId = selectedCategoryId ?: categories.firstOrNull()?.id ?: 0L
                                viewModel.navigateTo(Screen.CameraView(inspectionId, targetCatId))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Camera", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Photo Grid Layout (3 Columns)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (selectedCategoryId == null) {
                        // Grouped by Category headings when "All" is active
                        for (cat in categories) {
                            val catPhotos = allPhotos.filter { it.categoryId == cat.id }
                            if (catPhotos.isNotEmpty()) {
                                item(key = "header_${cat.id}") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = cat.name.uppercase(),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            ),
                                            color = AmberPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = "${catPhotos.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // 3 Columns in chunks of 3
                                val rows = catPhotos.chunked(3)
                                items(rows, key = { row -> "row_${cat.id}_${row.first().id}" }) { rowPhotos ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (photo in rowPhotos) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                PhotoGridThumbnailItem(
                                                    photo = photo,
                                                    onClick = {
                                                        viewModel.navigateTo(Screen.PhotoDetail(inspectionId, photo.id))
                                                    }
                                                )
                                            }
                                        }
                                        // Fill empty columns if last row has less than 3
                                        val remainder = 3 - rowPhotos.size
                                        for (i in 0 until remainder) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Single Category Selected: chunk into 3 columns
                        val rows = currentCategoryPhotos.chunked(3)
                        items(rows, key = { row -> "row_${row.first().id}" }) { rowPhotos ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (photo in rowPhotos) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        PhotoGridThumbnailItem(
                                            photo = photo,
                                            onClick = {
                                                viewModel.navigateTo(Screen.PhotoDetail(inspectionId, photo.id))
                                            }
                                        )
                                    }
                                }
                                val remainder = 3 - rowPhotos.size
                                for (i in 0 until remainder) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Category Dialog
    if (showAddCategoryDialog) {
        var newCatName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Add Category", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newCatName,
                    onValueChange = { newCatName = it },
                    label = { Text("Category Name") },
                    placeholder = { Text("e.g. Pocket Stitching") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_category_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newCatName.trim().isNotBlank()) {
                            viewModel.addCategory(inspectionId, newCatName)
                            showAddCategoryDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_category_button")
                ) {
                    Text("Add", fontWeight = FontWeight.Bold, color = AmberPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export PDF & ZIP Modal
    if (showExportDialog && inspection != null) {
        ExportReportDialog(
            inspection = inspection!!,
            categories = categories,
            allPhotos = allPhotos,
            isExporting = isExporting,
            onDismiss = { showExportDialog = false },
            onExportPdf = { selectedCatIds, includeComments ->
                viewModel.exportPdf(
                    inspection = inspection!!,
                    categories = categories,
                    photos = allPhotos,
                    options = PdfReportGenerator.ExportOptions(
                        selectedCategoryIds = selectedCatIds,
                        includeComments = includeComments
                    )
                )
            },
            onExportZip = {
                viewModel.exportZip(
                    inspection = inspection!!,
                    categories = categories,
                    photos = allPhotos
                )
            }
        )
    }
}

@Composable
fun PhotoGridThumbnailItem(
    photo: PhotoEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("photo_thumb_${photo.id}"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val thumbFile = File(photo.thumbnailPath).takeIf { it.exists() }
                    ?: File(photo.imagePath).takeIf { it.exists() }

                if (thumbFile != null) {
                    AsyncImage(
                        model = thumbFile,
                        contentDescription = photo.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // If barcode attached, show badge
                if (!photo.barcodeValue.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(AmberPrimary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "BC",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(6.dp)) {
                Text(
                    text = photo.fileName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!photo.comment.isNullOrBlank()) {
                    Text(
                        text = photo.comment,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ExportReportDialog(
    inspection: com.example.data.model.InspectionEntity,
    categories: List<CategoryEntity>,
    allPhotos: List<PhotoEntity>,
    isExporting: Boolean,
    onDismiss: () -> Unit,
    onExportPdf: (selectedCategoryIds: Set<Long>?, includeComments: Boolean) -> Unit,
    onExportZip: () -> Unit
) {
    var includeAllCategories by remember { mutableStateOf(true) }
    var selectedCategoryIds by remember { mutableStateOf<Set<Long>>(categories.map { it.id }.toSet()) }
    var includeComments by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AmberPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Inspection Report", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (isExporting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AmberPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Generating document...", fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "STYLE: ${inspection.styleNo} • ${allPhotos.size} photos",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeAllCategories = !includeAllCategories },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeAllCategories,
                            onCheckedChange = { includeAllCategories = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Include all categories", fontWeight = FontWeight.Medium)
                    }

                    if (!includeAllCategories) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        ) {
                            items(categories) { cat ->
                                val isChecked = cat.id in selectedCategoryIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCategoryIds = if (isChecked) {
                                                selectedCategoryIds - cat.id
                                            } else {
                                                selectedCategoryIds + cat.id
                                            }
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedCategoryIds = if (checked) {
                                                selectedCategoryIds + cat.id
                                            } else {
                                                selectedCategoryIds - cat.id
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(cat.name, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Include comments & barcodes", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = includeComments,
                            onCheckedChange = { includeComments = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onExportZip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("download_zip_button")
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download ZIP of All Photos")
                    }
                }
            }
        },
        confirmButton = {
            if (!isExporting) {
                Button(
                    onClick = {
                        val chosenCats = if (includeAllCategories) null else selectedCategoryIds
                        onExportPdf(chosenCats, includeComments)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                    modifier = Modifier.testTag("confirm_export_pdf_button")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export A4 PDF", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!isExporting) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
