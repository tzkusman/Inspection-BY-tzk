package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.category.CategoryManagerScreen
import com.example.ui.camera.CameraScreen
import com.example.ui.detail.PhotoDetailScreen
import com.example.ui.home.HomeScreen
import com.example.ui.inspection.InspectionScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.InspectCamTheme
import com.example.ui.viewmodel.InspectCamViewModel
import com.example.ui.viewmodel.Screen
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InspectCamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InspectCamApp()
                }
            }
        }
    }
}

@Composable
fun InspectCamApp(
    viewModel: InspectCamViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsState()

    // Observe Toast Messages
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collectLatest { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    when (val screen = currentScreen) {
        is Screen.Home -> {
            HomeScreen(viewModel = viewModel, modifier = Modifier.imePadding())
        }
        is Screen.InspectionView -> {
            InspectionScreen(
                inspectionId = screen.inspectionId,
                viewModel = viewModel,
                modifier = Modifier.imePadding()
            )
        }
        is Screen.CameraView -> {
            CameraScreen(
                inspectionId = screen.inspectionId,
                initialCategoryId = screen.initialCategoryId,
                viewModel = viewModel
            )
        }
        is Screen.PhotoDetail -> {
            PhotoDetailScreen(
                inspectionId = screen.inspectionId,
                photoId = screen.photoId,
                viewModel = viewModel,
                modifier = Modifier.imePadding()
            )
        }
        is Screen.CategoryManager -> {
            CategoryManagerScreen(
                inspectionId = screen.inspectionId,
                viewModel = viewModel,
                modifier = Modifier.imePadding()
            )
        }
        is Screen.Settings -> {
            SettingsScreen(viewModel = viewModel, modifier = Modifier.imePadding())
        }
    }
}
