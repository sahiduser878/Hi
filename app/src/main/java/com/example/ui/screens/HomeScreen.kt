package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FileCategory
import com.example.model.ShareFileItem
import com.example.ui.AppScreen
import com.example.ui.UiState
import com.example.ui.theme.BgLight
import com.example.ui.theme.BorderLight
import com.example.ui.theme.CardWhite
import com.example.ui.theme.CategoryAppGreen
import com.example.ui.theme.CategoryDocBlue
import com.example.ui.theme.CategoryMoreGrey
import com.example.ui.theme.CategoryMusicOrange
import com.example.ui.theme.CategoryPhotoPink
import com.example.ui.theme.CategoryVideoPurple
import com.example.ui.theme.ShareItBlue
import com.example.ui.theme.ShareItBlueDark
import com.example.ui.theme.ShareItGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: UiState,
    onNavigate: (AppScreen) -> Unit,
    onSelectCategory: (FileCategory) -> Unit,
    onOpenFile: (ShareFileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .testTag("home_screen")
            .fillMaxSize()
            .background(BgLight),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Royal Blue Top Header Section
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(ShareItBlue, ShareItBlueDark)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column {
                    // Top App Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiTethering,
                                    contentDescription = "Logo",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SHAREit",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 22.sp,
                                    color = Color.White
                                )
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onNavigate(AppScreen.SETTINGS) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "VIP",
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(
                                onClick = { onNavigate(AppScreen.RECEIVE_FILES) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan QR",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Title & Description
                    Text(
                        text = "Fast File Transfer",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 24.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Share files, photos, videos, apps and more with ease and speed.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Big Send & Receive Action Buttons
                    // SEND Button
                    ActionCardItem(
                        title = "Send",
                        subtitle = "Send files to nearby devices",
                        icon = Icons.Default.NearMe,
                        backgroundColor = Color(0xFF0D6EFD),
                        testTag = "btn_home_send",
                        onClick = { onNavigate(AppScreen.SEND_SEARCH) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // RECEIVE Button
                    ActionCardItem(
                        title = "Receive",
                        subtitle = "Receive files from nearby devices",
                        icon = Icons.Default.ArrowDownward,
                        backgroundColor = ShareItGreen,
                        testTag = "btn_home_receive",
                        onClick = { onNavigate(AppScreen.RECEIVE_FILES) }
                    )
                }
            }
        }

        // 6 Category Grid Card
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = CardWhite,
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
                ) {
                    Column(modifier = Modifier.padding(vertical = 16.dp, horizontal = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            CategoryIcon(
                                label = "Photos",
                                icon = Icons.Default.Image,
                                color = CategoryPhotoPink,
                                testTag = "cat_photos",
                                onClick = {
                                    onSelectCategory(FileCategory.PHOTOS)
                                    onNavigate(AppScreen.SELECT_FILES)
                                }
                            )
                            CategoryIcon(
                                label = "Videos",
                                icon = Icons.Default.Videocam,
                                color = CategoryVideoPurple,
                                testTag = "cat_videos",
                                onClick = {
                                    onSelectCategory(FileCategory.VIDEOS)
                                    onNavigate(AppScreen.SELECT_FILES)
                                }
                            )
                            CategoryIcon(
                                label = "Music",
                                icon = Icons.Default.MusicNote,
                                color = CategoryMusicOrange,
                                testTag = "cat_music",
                                onClick = {
                                    onSelectCategory(FileCategory.MUSIC)
                                    onNavigate(AppScreen.SELECT_FILES)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            CategoryIcon(
                                label = "Documents",
                                icon = Icons.Default.Description,
                                color = CategoryDocBlue,
                                testTag = "cat_docs",
                                onClick = {
                                    onSelectCategory(FileCategory.DOCS)
                                    onNavigate(AppScreen.SELECT_FILES)
                                }
                            )
                            CategoryIcon(
                                label = "Apps",
                                icon = Icons.Default.Android,
                                color = CategoryAppGreen,
                                testTag = "cat_apps",
                                onClick = {
                                    onSelectCategory(FileCategory.APPS)
                                    onNavigate(AppScreen.SELECT_FILES)
                                }
                            )
                            CategoryIcon(
                                label = "More",
                                icon = Icons.Default.GridView,
                                color = CategoryMoreGrey,
                                testTag = "cat_more",
                                onClick = {
                                    onSelectCategory(FileCategory.ALL)
                                    onNavigate(AppScreen.SELECT_FILES)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Recent Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .testTag("btn_view_all_recent")
                        .clickable { onNavigate(AppScreen.HISTORY) }
                ) {
                    Text(
                        text = "View all",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "View all",
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        val displayRecent = uiState.receivedFiles.ifEmpty { uiState.availableFiles.take(3) }
        if (displayRecent.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = CardWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
                ) {
                    Text(
                        text = "No recent files transferred. Tap 'Send' to transfer files.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(displayRecent.take(4), key = { it.id }) { file ->
                RecentFileRow(
                    file = file,
                    onClick = { onOpenFile(file) }
                )
            }
        }
    }
}

@Composable
private fun ActionCardItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    backgroundColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .testTag(testTag)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = backgroundColor,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = backgroundColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun CategoryIcon(
    label: String,
    icon: ImageVector,
    color: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .testTag(testTag)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                fontSize = 12.sp
            )
        )
    }
}

@Composable
private fun RecentFileRow(
    file: ShareFileItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = CardWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
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
                        .background(CategoryPhotoPink.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "File",
                        tint = CategoryPhotoPink,
                        modifier = Modifier.size(22.dp)
                    )
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
                    val dateFormatted = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(file.dateModified))
                    Text(
                        text = "${file.formattedSize} • $dateFormatted",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Open",
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
