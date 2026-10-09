package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FileCategory
import com.example.model.ShareFileItem
import com.example.ui.HistoryFilter
import com.example.ui.UiState
import com.example.ui.components.GlassCard
import com.example.ui.components.LiquidAuroraBackground
import com.example.ui.theme.CatAppGreen
import com.example.ui.theme.CatDocBlue
import com.example.ui.theme.CatMusicOrange
import com.example.ui.theme.CatPhotoPink
import com.example.ui.theme.CatVideoPurple
import com.example.ui.theme.ElectricPillBlue
import com.example.ui.theme.EmeraldPillGreen
import com.example.ui.theme.GlassTextMuted
import com.example.ui.theme.GlassTextPrimary
import com.example.ui.theme.GlassTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    uiState: UiState,
    onBack: () -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
    onOpenFile: (ShareFileItem) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val combinedItems = remember(uiState.historyFilter, uiState.receivedFiles, uiState.sentFiles) {
        val list = mutableListOf<HistoryItemWrapper>()
        if (uiState.historyFilter == HistoryFilter.ALL || uiState.historyFilter == HistoryFilter.SEND) {
            uiState.sentFiles.forEach { sent ->
                list.add(
                    HistoryItemWrapper(
                        id = sent.id,
                        name = sent.fileName,
                        size = sent.fileSize,
                        isSent = true,
                        category = getCategoryForName(sent.fileName),
                        timestamp = sent.timestamp,
                        rawShareItem = ShareFileItem(
                            id = sent.id,
                            name = sent.fileName,
                            size = sent.fileSize,
                            filePath = sent.localFilePath
                        )
                    )
                )
            }
        }
        if (uiState.historyFilter == HistoryFilter.ALL || uiState.historyFilter == HistoryFilter.RECEIVE) {
            uiState.receivedFiles.forEach { rx ->
                list.add(
                    HistoryItemWrapper(
                        id = rx.id,
                        name = rx.name,
                        size = rx.size,
                        isSent = false,
                        category = rx.category,
                        timestamp = rx.dateModified,
                        rawShareItem = rx
                    )
                )
            }
        }
        list.sortedByDescending { it.timestamp }
    }

    LiquidAuroraBackground(modifier = modifier.testTag("history_screen")) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
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
                        modifier = Modifier.testTag("btn_back_history")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = GlassTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Transfer History",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = GlassTextPrimary
                        )
                    )
                }

                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.testTag("btn_clear_history")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = GlassTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Segmented Frosted Glass Filter Pill Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HistoryPill(
                            text = "All",
                            isSelected = uiState.historyFilter == HistoryFilter.ALL,
                            modifier = Modifier.weight(1f),
                            onClick = { onFilterChange(HistoryFilter.ALL) }
                        )
                        HistoryPill(
                            text = "Send",
                            isSelected = uiState.historyFilter == HistoryFilter.SEND,
                            modifier = Modifier.weight(1f),
                            onClick = { onFilterChange(HistoryFilter.SEND) }
                        )
                        HistoryPill(
                            text = "Receive",
                            isSelected = uiState.historyFilter == HistoryFilter.RECEIVE,
                            modifier = Modifier.weight(1f),
                            onClick = { onFilterChange(HistoryFilter.RECEIVE) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // History List
            if (combinedItems.isEmpty()) {
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
                            contentDescription = "Empty",
                            tint = GlassTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No transfer history yet",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = GlassTextSecondary
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 85.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(combinedItems, key = { it.id }) { item ->
                        GlassHistoryRow(
                            item = item,
                            onClick = { onOpenFile(item.rawShareItem) }
                        )
                    }
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Clear History", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to clear your transfer history?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            onClearAll()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Clear All", color = Color.White)
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
}

private data class HistoryItemWrapper(
    val id: String,
    val name: String,
    val size: Long,
    val isSent: Boolean,
    val category: FileCategory,
    val timestamp: Long,
    val rawShareItem: ShareFileItem
)

private fun getCategoryForName(name: String): FileCategory {
    val ext = name.substringAfterLast(".", "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "webp", "gif" -> FileCategory.PHOTOS
        "mp4", "mkv", "mov", "avi" -> FileCategory.VIDEOS
        "mp3", "wav", "m4a", "flac" -> FileCategory.MUSIC
        "pdf", "doc", "docx", "txt", "xlsx" -> FileCategory.DOCS
        "apk" -> FileCategory.APPS
        else -> FileCategory.FILES
    }
}

@Composable
private fun HistoryPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) ElectricPillBlue else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else GlassTextSecondary,
                fontSize = 13.sp
            )
        )
    }
}

@Composable
private fun GlassHistoryRow(
    item: HistoryItemWrapper,
    onClick: () -> Unit
) {
    val (icon, bgCol) = when (item.category) {
        FileCategory.PHOTOS -> Icons.Default.Image to CatPhotoPink
        FileCategory.VIDEOS -> Icons.Default.Videocam to CatVideoPurple
        FileCategory.MUSIC -> Icons.Default.MusicNote to CatMusicOrange
        FileCategory.DOCS -> Icons.Default.Description to Color(0xFFEF4444)
        FileCategory.APPS -> Icons.Default.Android to CatAppGreen
        else -> Icons.Default.Image to CatPhotoPink
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 14.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgCol),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = item.name,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = GlassTextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val actionText = if (item.isSent) "Sent" else "Received"
                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                    Text(
                        text = "${ShareFileItem.formatBytes(item.size)}  •  $actionText at $timeStr",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = GlassTextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(EmeraldPillGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = GlassTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
