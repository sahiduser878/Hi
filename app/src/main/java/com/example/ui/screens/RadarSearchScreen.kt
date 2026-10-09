package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FileCategory
import com.example.model.PeerDevice
import com.example.ui.AppScreen
import com.example.ui.UiState
import com.example.ui.theme.BgLight
import com.example.ui.theme.BorderLight
import com.example.ui.theme.CardWhite
import com.example.ui.theme.CategoryDocBlue
import com.example.ui.theme.CategoryMusicOrange
import com.example.ui.theme.CategoryPhotoPink
import com.example.ui.theme.CategoryVideoPurple
import com.example.ui.theme.ShareItBlue
import com.example.ui.theme.ShareItBlueLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun RadarSearchScreen(
    uiState: UiState,
    onBack: () -> Unit,
    onNavigateToSelect: (FileCategory) -> Unit,
    onNavigateToReceiveQr: () -> Unit,
    onSendToPeer: (PeerDevice) -> Unit,
    onConnectManualIp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    var showManualIpDialog by remember { mutableStateOf(false) }
    var manualIpText by remember { mutableStateOf("") }

    val transition = rememberInfiniteTransition(label = "BlueRadar")
    val wave1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Wave1"
    )
    val wave2 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Wave2"
    )

    Box(
        modifier = modifier
            .testTag("radar_search_screen")
            .fillMaxSize()
            .background(BgLight)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_back_radar")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Send Files",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Blue Concentric Radar Circle Animation
            item {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxRadius = size.width / 2f

                        // Static soft blue rings
                        drawCircle(color = ShareItBlueLight, radius = maxRadius * 0.95f, center = center)
                        drawCircle(color = Color(0xFFD6E6FF), radius = maxRadius * 0.72f, center = center)
                        drawCircle(color = Color(0xFFBED8FF), radius = maxRadius * 0.50f, center = center)

                        // Animated wave pulses
                        listOf(wave1, wave2).forEach { w ->
                            val r = maxRadius * w
                            val alpha = (1f - w) * 0.5f
                            drawCircle(
                                color = ShareItBlue.copy(alpha = alpha),
                                radius = r,
                                center = center,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }

                    // Floating Dark Blue Center Circle with Paper Airplane
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(ShareItBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NearMe,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Status Texts
            item {
                Text(
                    text = "Looking for nearby devices...",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 17.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Make sure the receiving device is open\nand in the same Wi-Fi network.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 2x2 Category Summary Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CategorySummaryCard(
                        title = "Photos",
                        countText = "${uiState.photoCount} items",
                        icon = Icons.Default.Image,
                        color = CategoryPhotoPink,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToSelect(FileCategory.PHOTOS) }
                    )
                    CategorySummaryCard(
                        title = "Videos",
                        countText = "${uiState.videoCount} items",
                        icon = Icons.Default.Videocam,
                        color = CategoryVideoPurple,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToSelect(FileCategory.VIDEOS) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CategorySummaryCard(
                        title = "Music",
                        countText = "${uiState.musicCount} items",
                        icon = Icons.Default.MusicNote,
                        color = CategoryMusicOrange,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToSelect(FileCategory.MUSIC) }
                    )
                    CategorySummaryCard(
                        title = "Documents",
                        countText = "${uiState.docCount} items",
                        icon = Icons.Default.Description,
                        color = CategoryDocBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToSelect(FileCategory.DOCS) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Discovered Peers List (if any detected on local network)
            if (uiState.discoveredPeers.isNotEmpty()) {
                item {
                    Text(
                        text = "Discovered Receivers (${uiState.discoveredPeers.size})",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                items(uiState.discoveredPeers, key = { it.id }) { peer ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSendToPeer(peer) },
                        color = CardWhite,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ShareItBlueLight)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = peer.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = "${peer.ipAddress}:${peer.port}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                            Button(
                                onClick = { onSendToPeer(peer) },
                                colors = ButtonDefaults.buttonColors(containerColor = ShareItBlue),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Send", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(14.dp)) }
            }

            // "Don't see the device? Try connecting via QR code >" Card
            item {
                Surface(
                    modifier = Modifier
                        .testTag("btn_qr_connect_option")
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onNavigateToReceiveQr() },
                    color = CardWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "QR Code",
                                tint = ShareItBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Don't see the device?",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = "Try connecting via QR code",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Next",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Direct IP option
                Text(
                    text = "Or connect via IP Address",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ShareItBlue,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier
                        .testTag("btn_direct_ip")
                        .clickable { showManualIpDialog = true }
                        .padding(8.dp)
                )
            }
        }

        if (showManualIpDialog) {
            AlertDialog(
                onDismissRequest = { showManualIpDialog = false },
                containerColor = CardWhite,
                title = { Text("Direct Receiver IP", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Column {
                        Text("Enter the IP shown on receiver screen (e.g. 192.168.1.108:8888):", color = TextSecondary)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = manualIpText,
                            onValueChange = { manualIpText = it },
                            placeholder = { Text("192.168.1.100:8888") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (manualIpText.isNotBlank()) {
                                showManualIpDialog = false
                                onConnectManualIp(manualIpText)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ShareItBlue)
                    ) {
                        Text("Connect", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualIpDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun CategorySummaryCard(
    title: String,
    countText: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = CardWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}
