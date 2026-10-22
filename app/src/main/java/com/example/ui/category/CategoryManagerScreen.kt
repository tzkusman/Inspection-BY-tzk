package com.example.ui.category

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CategoryEntity
import com.example.ui.theme.AmberPrimary
import com.example.ui.viewmodel.InspectCamViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerScreen(
    inspectionId: Long,
    viewModel: InspectCamViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val categoriesFlow = remember(inspectionId) { viewModel.categoryRepo.getCategories(inspectionId) }
    val categories by categoriesFlow.collectAsState(initial = emptyList())

    val photosFlow = remember(inspectionId) { viewModel.photoRepo.getPhotosForInspection(inspectionId) }
    val allPhotos by photosFlow.collectAsState(initial = emptyList())

    val photoCountByCatId = remember(allPhotos) {
        allPhotos.groupingBy { it.categoryId }.eachCount()
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var categoryToRename by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Manage Categories", fontWeight = FontWeight.Bold)
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
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("add_category_header_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Category", tint = AmberPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Add, rename, or reorder categories. Reordering will change their sequence in the inspection overview and PDF reports.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_new_category_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add New Category", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(categories, key = { _, cat -> cat.id }) { index, cat ->
                    val photoCount = photoCountByCatId[cat.id] ?: 0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("category_item_${cat.id}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = cat.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "$photoCount photos",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Up Button
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val mutable = categories.toMutableList()
                                            val temp = mutable[index]
                                            mutable[index] = mutable[index - 1]
                                            mutable[index - 1] = temp
                                            viewModel.reorderCategories(mutable)
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                                }

                                // Down Button
                                IconButton(
                                    onClick = {
                                        if (index < categories.size - 1) {
                                            val mutable = categories.toMutableList()
                                            val temp = mutable[index]
                                            mutable[index] = mutable[index + 1]
                                            mutable[index + 1] = temp
                                            viewModel.reorderCategories(mutable)
                                        }
                                    },
                                    enabled = index < categories.size - 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                                }

                                // Rename Button
                                IconButton(
                                    onClick = { categoryToRename = cat },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename", tint = AmberPrimary)
                                }

                                // Delete Button
                                IconButton(
                                    onClick = { categoryToDelete = cat },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Category Dialog
    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Category") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Category Name") },
                    placeholder = { Text("e.g. Care Label Barcode") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_category_dialog_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.trim().isNotBlank()) {
                            viewModel.addCategory(inspectionId, newName)
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Add", fontWeight = FontWeight.Bold, color = AmberPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Rename Category Dialog
    categoryToRename?.let { cat ->
        var renameText by remember { mutableStateOf(cat.name) }
        AlertDialog(
            onDismissRequest = { categoryToRename = null },
            title = { Text("Rename Category") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_category_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.trim().isNotBlank()) {
                            viewModel.renameCategory(cat.id, renameText)
                            categoryToRename = null
                        }
                    }
                ) {
                    Text("Rename", fontWeight = FontWeight.Bold, color = AmberPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToRename = null }) { Text("Cancel") }
            }
        )
    }

    // Delete Category Dialog with Move vs Delete Photos options
    categoryToDelete?.let { cat ->
        val photoCount = photoCountByCatId[cat.id] ?: 0
        var deleteMode by remember { mutableStateOf("move") } // "move" or "delete"
        val otherCategories = categories.filter { it.id != cat.id }
        var targetMoveCatId by remember { mutableStateOf(otherCategories.firstOrNull()?.id ?: 0L) }
        var dropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category '${cat.name}'?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (photoCount > 0) {
                        Text(
                            text = "This category contains $photoCount photos. What would you like to do with them?",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (otherCategories.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { deleteMode = "move" },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = deleteMode == "move",
                                    onClick = { deleteMode = "move" }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Move photos to another category")
                            }

                            if (deleteMode == "move") {
                                Box(modifier = Modifier.fillMaxWidth().padding(start = 32.dp)) {
                                    val targetName = otherCategories.firstOrNull { it.id == targetMoveCatId }?.name ?: "Select Category"
                                    OutlinedTextField(
                                        value = targetName,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Target Category") },
                                        trailingIcon = {
                                            IconButton(onClick = { dropdownExpanded = true }) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().clickable { dropdownExpanded = true }
                                    )
                                    DropdownMenu(
                                        expanded = dropdownExpanded,
                                        onDismissRequest = { dropdownExpanded = false }
                                    ) {
                                        for (other in otherCategories) {
                                            DropdownMenuItem(
                                                text = { Text(other.name) },
                                                onClick = {
                                                    targetMoveCatId = other.id
                                                    dropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { deleteMode = "delete" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = deleteMode == "delete" || otherCategories.isEmpty(),
                                onClick = { deleteMode = "delete" }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Permanently delete photos", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Text("Are you sure you want to delete this category?")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val moveId = if (photoCount > 0 && deleteMode == "move" && otherCategories.isNotEmpty()) {
                            targetMoveCatId
                        } else {
                            null
                        }
                        viewModel.deleteCategory(cat, moveId)
                        categoryToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_category_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
