package com.example.ui.camera

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.CategoryEntity
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate900
import com.example.ui.viewmodel.InspectCamViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    inspectionId: Long,
    initialCategoryId: Long,
    viewModel: InspectCamViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val categoriesFlow = remember(inspectionId) { viewModel.categoryRepo.getCategories(inspectionId) }
    val categories by categoriesFlow.collectAsState(initial = emptyList())

    var activeCategoryId by remember { mutableStateOf(initialCategoryId) }
    LaunchedEffect(categories) {
        if (activeCategoryId == 0L && categories.isNotEmpty()) {
            activeCategoryId = categories.first().id
        }
    }

    // Camera states
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        cameraPermissionGranted = isGranted
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    var isTorchOn by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    // Capture & Review state
    var capturedTempFile by remember { mutableStateOf<File?>(null) }
    var showPreviewReview by remember { mutableStateOf(false) }
    var showMetadataSheet by remember { mutableStateOf(false) }
    var isProcessingCapture by remember { mutableStateOf(false) }

    // Form inputs in bottom sheet
    var suggestedFileName by remember { mutableStateOf("") }
    var userFileName by remember { mutableStateOf("") }
    var commentText by remember { mutableStateOf("") }
    var barcodeText by remember { mutableStateOf("") }
    var isScanningBarcode by remember { mutableStateOf(false) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }

    // Gallery Picker Fallback
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessingCapture = true
                val tempFile = File(context.cacheDir, "gallery_${UUID.randomUUID()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    tempFile.outputStream().use { outStream -> inStream.copyTo(outStream) }
                }
                capturedTempFile = tempFile
                isProcessingCapture = false
                showPreviewReview = true
            }
        }
    }

    // Auto-fill smart suggestion name whenever active category changes or preview opens
    LaunchedEffect(activeCategoryId, showMetadataSheet) {
        if (showMetadataSheet) {
            val catName = categories.firstOrNull { it.id == activeCategoryId }?.name ?: "Photo"
            val nextName = viewModel.getSuggestedFileName(activeCategoryId, catName)
            suggestedFileName = nextName
            if (userFileName.isBlank() || userFileName.startsWith(catName)) {
                userFileName = nextName
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (cameraPermissionGranted) {
            // CameraX Live Preview Viewfinder
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            imageCapture = capture

                            cameraProvider.unbindAll()
                            val cam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                capture
                            )
                            boundCamera = cam
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                update = {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(it.surfaceProvider)
                            }
                            cameraProvider.unbindAll()
                            val cam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                            boundCamera = cam
                            cam.cameraControl.enableTorch(isTorchOn)
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(context))
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Permission Denied fallback screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "InspectCam needs camera access to photograph garment samples and scan barcodes. You can also pick photos directly from your gallery.",
                    color = Color.LightGray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    Text("Grant Permission", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") }
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick from Gallery", color = Color.White)
                }
            }
        }

        // Top Camera HUD Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateBack() },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .testTag("close_camera_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            // Active category indicator pill
            val currentCatName = categories.firstOrNull { it.id == activeCategoryId }?.name ?: "Front View"
            Surface(
                color = AmberPrimary,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentCatName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Torch Toggle
            IconButton(
                onClick = {
                    isTorchOn = !isTorchOn
                    boundCamera?.cameraControl?.enableTorch(isTorchOn)
                },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .testTag("torch_toggle_button")
            ) {
                Icon(
                    imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Torch",
                    tint = if (isTorchOn) AmberPrimary else Color.White
                )
            }
        }

        // Bottom Controls HUD Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gallery Picker
            IconButton(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .testTag("gallery_picker_button")
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White, modifier = Modifier.size(26.dp))
            }

            // Big Shutter Button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f))
                    .clickable {
                        if (isProcessingCapture) return@clickable
                        val capture = imageCapture
                        if (capture != null) {
                            isProcessingCapture = true
                            val tempFile = File(context.cacheDir, "capture_${UUID.randomUUID()}.jpg")
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

                            capture.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        capturedTempFile = tempFile
                                        isProcessingCapture = false
                                        showPreviewReview = true
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isProcessingCapture = false
                                    }
                                }
                            )
                        } else {
                            // Fallback if camera provider unavailable
                            galleryLauncher.launch("image/*")
                        }
                    }
                    .testTag("shutter_button"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(AmberPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessingCapture) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }

            // Flip Camera (Front / Rear)
            IconButton(
                onClick = {
                    cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .testTag("flip_camera_button")
            ) {
                Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }

        // Fullscreen Review Overlay (Retake vs Use Photo)
        if (showPreviewReview && capturedTempFile != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(
                    model = capturedTempFile,
                    contentDescription = "Review",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            capturedTempFile?.delete()
                            capturedTempFile = null
                            showPreviewReview = false
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("retake_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retake", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            showPreviewReview = false
                            showMetadataSheet = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("use_photo_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use Photo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Bottom Sheet: Annotation, File Name, Category & Barcode
        if (showMetadataSheet && capturedTempFile != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var categoryDropdownExpanded by remember { mutableStateOf(false) }

            ModalBottomSheet(
                onDismissRequest = {
                    showMetadataSheet = false
                    capturedTempFile?.delete()
                    capturedTempFile = null
                },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Photo Information",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    // File Name Field (Required)
                    OutlinedTextField(
                        value = userFileName,
                        onValueChange = { userFileName = it },
                        label = { Text("File Name *") },
                        placeholder = { Text("e.g. Front View 01") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("photo_file_name_input")
                    )

                    // Category Selector Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val currentName = categories.firstOrNull { it.id == activeCategoryId }?.name ?: "Select Category"
                        OutlinedTextField(
                            value = currentName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category *") },
                            trailingIcon = {
                                IconButton(onClick = { categoryDropdownExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { categoryDropdownExpanded = true }
                                .testTag("category_selector_dropdown")
                        )

                        DropdownMenu(
                            expanded = categoryDropdownExpanded,
                            onDismissRequest = { categoryDropdownExpanded = false }
                        ) {
                            for (cat in categories) {
                                DropdownMenuItem(
                                    text = { Text(cat.name, fontWeight = if (cat.id == activeCategoryId) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        activeCategoryId = cat.id
                                        categoryDropdownExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("+ New Category", color = AmberPrimary, fontWeight = FontWeight.Bold) },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = AmberPrimary) },
                                onClick = {
                                    categoryDropdownExpanded = false
                                    showNewCategoryDialog = true
                                }
                            )
                        }
                    }

                    // Barcode Value Field with Scan Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = barcodeText,
                            onValueChange = { barcodeText = it },
                            label = { Text("Barcode Value (Optional)") },
                            placeholder = { Text("Scanned code") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("barcode_value_input")
                        )

                        Button(
                            onClick = {
                                val file = capturedTempFile ?: return@Button
                                scope.launch {
                                    isScanningBarcode = true
                                    try {
                                        val bmp = BitmapFactory.decodeFile(file.absolutePath)
                                        if (bmp != null) {
                                            val detected = viewModel.scanBarcodeFromImage(bmp)
                                            if (detected != null) {
                                                barcodeText = detected
                                                // Haptic feedback
                                                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                                }
                                            }
                                            bmp.recycle()
                                        }
                                    } catch (_: Exception) {}
                                    isScanningBarcode = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                            modifier = Modifier
                                .height(56.dp)
                                .testTag("scan_barcode_button")
                        ) {
                            if (isScanningBarcode) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan")
                            }
                        }
                    }

                    // Comment Box (Multi-line)
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        label = { Text("Comment (Optional)") },
                        placeholder = { Text("Add comment about this picture...") },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("photo_comment_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action Buttons: "Save" and "Save & Take Another"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val file = capturedTempFile ?: return@OutlinedButton
                                val finalFileName = userFileName.trim().ifEmpty { suggestedFileName }
                                viewModel.saveCapturedPhoto(
                                    inspectionId = inspectionId,
                                    categoryId = activeCategoryId,
                                    desiredFileName = finalFileName,
                                    rawImageFile = file,
                                    comment = commentText,
                                    barcodeValue = barcodeText
                                ) {
                                    // Reset inputs for next picture
                                    capturedTempFile = null
                                    showMetadataSheet = false
                                    commentText = ""
                                    barcodeText = ""
                                    userFileName = ""
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("save_and_another_button")
                        ) {
                            Text("Save & Next", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val file = capturedTempFile ?: return@Button
                                val finalFileName = userFileName.trim().ifEmpty { suggestedFileName }
                                viewModel.saveCapturedPhoto(
                                    inspectionId = inspectionId,
                                    categoryId = activeCategoryId,
                                    desiredFileName = finalFileName,
                                    rawImageFile = file,
                                    comment = commentText,
                                    barcodeValue = barcodeText
                                ) {
                                    showMetadataSheet = false
                                    viewModel.navigateBack()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("save_photo_button")
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Inline Add Category Dialog
        if (showNewCategoryDialog) {
            var newCatName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showNewCategoryDialog = false },
                title = { Text("Add New Category") },
                text = {
                    OutlinedTextField(
                        value = newCatName,
                        onValueChange = { newCatName = it },
                        label = { Text("Category Name") },
                        placeholder = { Text("e.g. Care Label Barcode") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_category_input")
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newCatName.trim().isNotBlank()) {
                                viewModel.addCategory(inspectionId, newCatName) { newId ->
                                    activeCategoryId = newId
                                    showNewCategoryDialog = false
                                }
                            }
                        }
                    ) {
                        Text("Add", fontWeight = FontWeight.Bold, color = AmberPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewCategoryDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
