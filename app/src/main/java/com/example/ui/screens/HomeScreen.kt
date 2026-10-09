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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
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
import com.example.ui.components.GlassCard
import com.example.ui.components.LiquidAuroraBackground
import com.example.ui.theme.CatDocBlue
import com.example.ui.theme.CatMusicOrange
import com.example.ui.theme.CatPhotoPink
import com.example.ui.theme.CatVideoPurple
import com.example.ui.theme.ElectricPillBlue
import com.example.ui.theme.ElectricPillCyan
import com.example.ui.theme.GlassTextMuted
import com.example.ui.theme.GlassTextPrimary
import com.example.ui.theme.GlassTextSecondary
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
    LiquidAuroraBackground(
        modifier = modifier.testTag("home_screen"),
        isHeroDeepBlue = true
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 85.dp)
        ) {
            // Top App Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = "Logo",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SHAREit",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "Fast • Safe • Share",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .clickable { onNavigate(AppScreen.SETTINGS) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "VIP",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = "Notifications",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))
            }

            // Hero Title & Description
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "Connect\nShare Everything",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 28.sp,
                            lineHeight = 32.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Transfer files, photos, videos, apps\nand more — anytime, anywhere.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Dual Liquid Glass Action Cards (Send & Receive)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassActionCard(
                        title = "Send",
                        subtitle = "Send files to nearby devices",
                        icon = Icons.Default.ArrowUpward,
                        iconGradient = listOf(ElectricPillBlue, ElectricPillCyan),
                        testTag = "btn_home_send",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppScreen.SEND_SEARCH) }
                    )

                    GlassActionCard(
                        title = "Receive",
                        subtitle = "Receive files from nearby devices",
                        icon = Icons.Default.ArrowDownward,
                        iconGradient = listOf(ElectricPillCyan, Color(0xFF0077B6)),
                        testTag = "btn_home_receive",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppScreen.RECEIVE_FILES) }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            // 4-Category Frosted Glass Card (Photos, Videos, Music, Documents)
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            GlassCategoryItem(
                                label = "Photos",
                                countText = String.format("%,d", uiState.photoCount),
                                icon = Icons.Default.Image,
                                color = CatPhotoPink,
                                onClick = {
                                    onSelectCategory(FileCategory.PHOTOS)
                                    onNavigate(AppScreen.SELECT_FILES)
                                }
                            )
                            GlassCategoryItem(
                                label = "Videos",
                                countText = String.format("%,d", uiState.videoCount),
                                icon = Icons.Default.Videocam,
                                color = CatVideoPurple,
                                onClick = {
                                    onSelectCategory(FileCategory.VIDEOS)
                                    onNavigate(AppScreen.SELECT_FILES)
                                }
                            )
                            GlassCategoryItem(
                                label = "Music",
                                countText = String.format("%,d", uiState.musicCount),
                                icon = Icons.Default.MusicNote,
                                color = CatMusicOrange,
                                onClick = {
                                    onSelectCategory(FileCategory.MUSIC)
                                    onNavigate(AppScreen.SELECT_FILES)
                                }
                            )
                            GlassCategoryItem(
                                label = "Documents",
                                countText = String.format("%,d", uiState.docCount),
                                icon = Icons.Default.Description,
                                color = CatDocBlue,
                                onClick = {
                                    onSelectCategory(FileCategory.DOCS)
                                    onNavigate(AppScreen.SELECT_FILES)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Recent Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = GlassTextPrimary
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
                                color = GlassTextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "View all",
                            tint = GlassTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            val displayRecent = uiState.receivedFiles.ifEmpty { uiState.availableFiles.take(3) }
            if (displayRecent.isEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No recent files transferred. Tap Send to transfer files.",
                                style = MaterialTheme.typography.bodySmall.copy(color = GlassTextMuted),
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                }
            } else {
                items(displayRecent.take(4), key = { it.id }) { file ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenFile(file) }
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
                                            .background(CatPhotoPink.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = "File",
                                            tint = CatPhotoPink,
                                            modifier = Modifier.size(22.dp)
                                        )
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
                                        val dateFormatted = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(file.dateModified))
                                        Text(
                                            text = "${file.formattedSize} • $dateFormatted",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = GlassTextSecondary,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = "Open",
                                    tint = GlassTextMuted,
                                    modifier = Modifier.size(14.dp)
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
private fun GlassActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconGradient: List<Color>,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .testTag(testTag)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(iconGradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = GlassTextPrimary,
                    fontSize = 16.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = GlassTextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GlassCategoryItem(
    label: String,
    countText: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = GlassTextPrimary,
                fontSize = 11.sp
            )
        )
        Text(
            text = countText,
            style = MaterialTheme.typography.labelSmall.copy(
                color = GlassTextSecondary,
                fontSize = 10.sp
            )
        )
    }
}
