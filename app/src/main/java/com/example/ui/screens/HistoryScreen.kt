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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FileCategory
import com.example.model.ShareFileItem
import com.example.model.TransferDirection
import com.example.model.TransferItem
import com.example.ui.HistoryFilter
import com.example.ui.UiState
import com.example.ui.theme.BgLight
import com.example.ui.theme.BorderLight
import com.example.ui.theme.CardWhite
import com.example.ui.theme.CategoryAppGreen
import com.example.ui.theme.CategoryDocBlue
import com.example.ui.theme.CategoryMusicOrange
import com.example.ui.theme.CategoryPhotoPink
import com.example.ui.theme.CategoryVideoPurple
import com.example.ui.theme.ShareItBlue
import com.example.ui.theme.ShareItGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
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

    // Combine real received and sent items into unified display
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

    Box(
        modifier = modifier
            .testTag("history_screen")
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
                                tint = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Transfer History",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
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
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Segmented Filter Pill Tabs: [All] [Send] [Receive]
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFE2E8F0)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SegmentedPill(
                        text = "All",
                        isSelected = uiState.historyFilter == HistoryFilter.ALL,
                        modifier = Modifier.weight(1f),
                        onClick = { onFilterChange(HistoryFilter.ALL) }
                    )
                    SegmentedPill(
                        text = "Send",
                        isSelected = uiState.historyFilter == HistoryFilter.SEND,
                        modifier = Modifier.weight(1f),
                        onClick = { onFilterChange(HistoryFilter.SEND) }
                    )
                    SegmentedPill(
                        text = "Receive",
                        isSelected = uiState.historyFilter == HistoryFilter.RECEIVE,
                        modifier = Modifier.weight(1f),
                        onClick = { onFilterChange(HistoryFilter.RECEIVE) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No transfer history yet",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                        )
                        Text(
                            text = "Transferred and received files will appear here.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 12.sp
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
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(combinedItems, key = { it.id }) { item ->
                        HistoryRowItem(
                            item = item,
                            onClick = { onOpenFile(item.rawShareItem) }
                        )
                    }
                }
            }
        }

        // Delete Confirm Dialog
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor = CardWhite,
                title = { Text("Clear History", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = { Text("Are you sure you want to clear your transfer history?", color = TextSecondary) },
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
                        Text("Cancel", color = TextSecondary)
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
private fun SegmentedPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) ShareItBlue else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else TextSecondary
            )
        )
    }
}

@Composable
private fun HistoryRowItem(
    item: HistoryItemWrapper,
    onClick: () -> Unit
) {
    val (icon, bgCol) = when (item.category) {
        FileCategory.PHOTOS -> Icons.Default.Image to CategoryPhotoPink
        FileCategory.VIDEOS -> Icons.Default.Videocam to CategoryVideoPurple
        FileCategory.MUSIC -> Icons.Default.MusicNote to CategoryMusicOrange
        FileCategory.DOCS -> Icons.Default.Description to Color(0xFFEF4444)
        FileCategory.APPS -> Icons.Default.Android to CategoryAppGreen
        else -> Icons.Default.Image to CategoryPhotoPink
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = CardWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
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
                            color = TextPrimary
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
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Green circle with checkmark (Matching Screen 6)
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(ShareItGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Open",
                    tint = TextMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
