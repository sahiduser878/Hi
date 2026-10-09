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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.components.GlassCard
import com.example.ui.components.LiquidAuroraBackground
import com.example.ui.theme.CatAppGreen
import com.example.ui.theme.CatDocBlue
import com.example.ui.theme.CatMusicOrange
import com.example.ui.theme.CatPhotoPink
import com.example.ui.theme.CatVideoPurple
import com.example.ui.theme.ElectricPillBlue
import com.example.ui.theme.ElectricPillCyan
import com.example.ui.theme.GlassDivider
import com.example.ui.theme.GlassTextMuted
import com.example.ui.theme.GlassTextPrimary
import com.example.ui.theme.GlassTextSecondary

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

    LiquidAuroraBackground(modifier = modifier.testTag("send_selector_screen")) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Frosted Glass Top Bar & Category Tabs
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 0.dp
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
                                        tint = GlassTextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Select Files",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = GlassTextPrimary
                                    )
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = GlassTextPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                IconButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Pick Files",
                                        tint = ElectricPillBlue,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        if (isSearchActive) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { Text("Search files on device...", color = GlassTextSecondary) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                singleLine = true,
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onSearchQueryChange("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = GlassTextSecondary)
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricPillBlue,
                                    unfocusedBorderColor = GlassDivider
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
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
                                color = CatPhotoPink,
                                onClick = { onSelectCategory(FileCategory.PHOTOS) }
                            )
                            CategoryTabItem(
                                label = "Videos",
                                icon = Icons.Default.Videocam,
                                isSelected = uiState.selectedCategory == FileCategory.VIDEOS,
                                color = CatVideoPurple,
                                onClick = { onSelectCategory(FileCategory.VIDEOS) }
                            )
                            CategoryTabItem(
                                label = "Music",
                                icon = Icons.Default.MusicNote,
                                isSelected = uiState.selectedCategory == FileCategory.MUSIC,
                                color = CatMusicOrange,
                                onClick = { onSelectCategory(FileCategory.MUSIC) }
                            )
                            CategoryTabItem(
                                label = "Documents",
                                icon = Icons.Default.Description,
                                isSelected = uiState.selectedCategory == FileCategory.DOCS,
                                color = CatDocBlue,
                                onClick = { onSelectCategory(FileCategory.DOCS) }
                            )
                            CategoryTabItem(
                                label = "Apps",
                                icon = Icons.Default.Android,
                                isSelected = uiState.selectedCategory == FileCategory.APPS,
                                color = CatAppGreen,
                                onClick = { onSelectCategory(FileCategory.APPS) }
                            )
                        }

                        HorizontalDivider(color = GlassDivider)

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
                                    colors = CheckboxDefaults.colors(checkedColor = ElectricPillBlue),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Select All",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = GlassTextPrimary
                                    )
                                )
                            }

                            Text(
                                text = "${uiState.selectedFiles.size} items • ${uiState.formattedSelectedBytes}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = GlassTextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }

                // File Items List
                if (uiState.isLoadingFiles) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ElectricPillBlue)
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
                                tint = GlassTextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No files found in this category.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = GlassTextSecondary)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricPillBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Browse from Storage", color = Color.White)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 95.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredFiles, key = { it.id }) { file ->
                            val isSelected = uiState.selectedFiles.any { it.id == file.id }
                            SelectableGlassFileRow(
                                file = file,
                                isSelected = isSelected,
                                onToggle = { onToggleSelectFile(file) }
                            )
                        }
                    }
                }
            }

            // Bottom Floating Glass Bar: "X files selected / X.X MB" + "Send"
            GlassCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                cornerRadius = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${uiState.selectedFiles.size} files selected",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = GlassTextPrimary
                            )
                        )
                        Text(
                            text = uiState.formattedSelectedBytes,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = GlassTextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Button(
                        onClick = onSend,
                        enabled = uiState.selectedFiles.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .testTag("btn_selector_send")
                            .height(44.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (uiState.selectedFiles.isNotEmpty()) {
                                        Brush.horizontalGradient(listOf(ElectricPillBlue, ElectricPillCyan))
                                    } else {
                                        Brush.horizontalGradient(listOf(Color(0xFFCBD5E1), Color(0xFFCBD5E1)))
                                    },
                                    RoundedCornerShape(24.dp)
                                )
                                .padding(horizontal = 22.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
            .padding(horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) ElectricPillBlue else Color.White.copy(alpha = 0.5f)),
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
                color = if (isSelected) ElectricPillBlue else GlassTextSecondary,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun SelectableGlassFileRow(
    file: ShareFileItem,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        cornerRadius = 14.dp
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
                        FileCategory.APPS -> Icons.Default.Android to CatAppGreen
                        FileCategory.VIDEOS -> Icons.Default.Videocam to CatVideoPurple
                        FileCategory.MUSIC -> Icons.Default.MusicNote to CatMusicOrange
                        FileCategory.DOCS -> Icons.Default.Description to CatDocBlue
                        else -> Icons.Default.Image to CatPhotoPink
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
                            color = GlassTextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = file.formattedSize,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = GlassTextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = ElectricPillBlue,
                    uncheckedColor = GlassTextMuted
                )
            )
        }
    }
}
