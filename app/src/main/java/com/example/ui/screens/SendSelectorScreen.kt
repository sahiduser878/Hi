package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.FileCategory
import com.example.model.ShareFileItem
import com.example.ui.UiState
import com.example.ui.theme.BgLight
import com.example.ui.theme.BorderLight
import com.example.ui.theme.CardWhite
import com.example.ui.theme.CategoryAppGreen
import com.example.ui.theme.CategoryDocBlue
import com.example.ui.theme.CategoryMusicOrange
import com.example.ui.theme.CategoryPhotoPink
import com.example.ui.theme.CategoryVideoPurple
import com.example.ui.theme.DividerColor
import com.example.ui.theme.ShareItBlue
import com.example.ui.theme.ShareItBlueLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SendSelectorScreen(
    uiState: UiState,
    onBack: () -> Unit,
    onSelectCategory: (FileCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSelectFile: (ShareFileItem) -> Unit,
    onSelectAll: () -> Unit,
    onAddPickedFile: (Uri) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    var isSearchActive by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { onAddPickedFile(it) }
    }

    val filteredFiles = if (uiState.searchQuery.isBlank()) {
        uiState.availableFiles
    } else {
        val q = uiState.searchQuery.lowercase()
        uiState.availableFiles.filter { it.name.lowercase().contains(q) }
    }

    val allSelected = filteredFiles.isNotEmpty() && filteredFiles.all { f -> uiState.selectedFiles.any { it.id == f.id } }

    Box(
        modifier = modifier
            .testTag("send_selector_screen")
            .fillMaxSize()
            .background(BgLight)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardWhite,
                shadowElevation = 1.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("btn_back_selector")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Select Files",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { isSearchActive = !isSearchActive },
                                modifier = Modifier.testTag("btn_toggle_search")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(
                                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier.testTag("btn_browse_files")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Browse Any File",
                                    tint = ShareItBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // Optional Search Field
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("Filter files...", color = TextSecondary) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            singleLine = true,
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ShareItBlue,
                                unfocusedBorderColor = BorderLight
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Category Tabs Row: Photos, Videos, Music, Documents, Apps
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        CategoryTabItem(
                            label = "Photos",
                            icon = Icons.Default.Image,
                            isSelected = uiState.selectedCategory == FileCategory.PHOTOS,
                            color = CategoryPhotoPink,
                            onClick = { onSelectCategory(FileCategory.PHOTOS) }
                        )
                        CategoryTabItem(
                            label = "Videos",
                            icon = Icons.Default.Videocam,
                            isSelected = uiState.selectedCategory == FileCategory.VIDEOS,
                            color = CategoryVideoPurple,
                            onClick = { onSelectCategory(FileCategory.VIDEOS) }
                        )
                        CategoryTabItem(
                            label = "Music",
                            icon = Icons.Default.MusicNote,
                            isSelected = uiState.selectedCategory == FileCategory.MUSIC,
                            color = CategoryMusicOrange,
                            onClick = { onSelectCategory(FileCategory.MUSIC) }
                        )
                        CategoryTabItem(
                            label = "Documents",
                            icon = Icons.Default.Description,
                            isSelected = uiState.selectedCategory == FileCategory.DOCS,
                            color = CategoryDocBlue,
                            onClick = { onSelectCategory(FileCategory.DOCS) }
                        )
                        CategoryTabItem(
                            label = "Apps",
                            icon = Icons.Default.Android,
                            isSelected = uiState.selectedCategory == FileCategory.APPS,
                            color = CategoryAppGreen,
                            onClick = { onSelectCategory(FileCategory.APPS) }
                        )
                    }

                    HorizontalDivider(color = DividerColor)

                    // "Select All" Checkbox Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectAll() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { onSelectAll() },
                                colors = CheckboxDefaults.colors(checkedColor = ShareItBlue),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Select All",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            )
                        }

                        Text(
                            text = "${filteredFiles.size} items",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // Real Files List
            if (uiState.isLoadingFiles) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ShareItBlue)
                }
            } else if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "No Files",
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No files found in this category on device.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = ShareItBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Browse Files from Storage", color = Color.White)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredFiles, key = { it.id }) { file ->
                        val isSelected = uiState.selectedFiles.any { it.id == file.id }
                        SelectableFileRow(
                            file = file,
                            isSelected = isSelected,
                            onToggle = { onToggleSelectFile(file) }
                        )
                    }
                }
            }
        }

        // Bottom Sticky Bar: "X selected (X.X MB)" + "Send" Button
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = CardWhite,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.selectedFiles.size} selected (${uiState.formattedSelectedBytes})",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                )

                Button(
                    onClick = onSend,
                    enabled = uiState.selectedFiles.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ShareItBlue,
                        disabledContainerColor = ShareItBlue.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .testTag("btn_selector_send")
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NearMe,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Send",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTabItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) ShareItBlue else BgLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else color,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) ShareItBlue else TextSecondary,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun SelectableFileRow(
    file: ShareFileItem,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle),
        color = CardWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) ShareItBlueLight else BorderLight)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Thumbnail preview or category icon
                if (file.category == FileCategory.PHOTOS && file.uri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(file.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = file.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    val (icon, bgCol) = when (file.category) {
                        FileCategory.APPS -> Icons.Default.Android to CategoryAppGreen
                        FileCategory.VIDEOS -> Icons.Default.Videocam to CategoryVideoPurple
                        FileCategory.MUSIC -> Icons.Default.MusicNote to CategoryMusicOrange
                        FileCategory.DOCS -> Icons.Default.Description to CategoryDocBlue
                        else -> Icons.Default.Image to CategoryPhotoPink
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgCol.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = file.name,
                            tint = bgCol,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = file.formattedSize,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = ShareItBlue,
                    uncheckedColor = TextMuted
                )
            )
        }
    }
}
