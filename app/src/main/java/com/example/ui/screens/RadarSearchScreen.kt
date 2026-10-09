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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionState
import com.example.model.FileCategory
import com.example.model.PeerDevice
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
import com.example.ui.theme.EmeraldPillGreen
import com.example.ui.theme.GlassTextMuted
import com.example.ui.theme.GlassTextPrimary
import com.example.ui.theme.GlassTextSecondary

@Composable
fun RadarSearchScreen(
    uiState: UiState,
    onBack: () -> Unit,
    onNavigateToSelect: (FileCategory) -> Unit,
    onNavigateToReceiveQr: () -> Unit,
    onSendToPeer: (PeerDevice) -> Unit,
    onConnectManualIp: (String) -> Unit,
    onOpenHotspotSettings: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    var showManualIpDialog by remember { mutableStateOf(false) }
    var manualIpText by remember { mutableStateOf("192.168.43.1") }

    val transition = rememberInfiniteTransition(label = "LiquidGlassRadar")
    val wave1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "LiquidWave1"
    )
    val wave2 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "LiquidWave2"
    )

    LiquidAuroraBackground(modifier = modifier.testTag("radar_search_screen")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("btn_back_radar")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = GlassTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Send Files",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = GlassTextPrimary
                            )
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onNavigateToReceiveQr) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR",
                                tint = GlassTextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = onOpenWifiSettings) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "Wi-Fi Settings",
                                tint = GlassTextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Selected Files Indicator Card
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToSelect(uiState.selectedCategory) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(ElectricPillBlue.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Selected Files",
                                    tint = ElectricPillBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                if (uiState.selectedFiles.isNotEmpty()) {
                                    Text(
                                        text = "${uiState.selectedFiles.size} files selected (${uiState.formattedSelectedBytes})",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = GlassTextPrimary
                                        )
                                    )
                                    Text(
                                        text = "Tap to view or add more files",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = ElectricPillBlue,
                                            fontSize = 11.sp
                                        )
                                    )
                                } else {
                                    Text(
                                        text = "No files selected yet",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = GlassTextPrimary
                                        )
                                    )
                                    Text(
                                        text = "Tap to pick Apps, Photos, Videos, or Docs",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = GlassTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Select",
                            tint = GlassTextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Liquid Glass Ripple Radar Animation
            item {
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxRadius = size.width / 2f

                        // Liquid glass ripple rings
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.5f), Color(0x3300C6FF), Color.Transparent),
                                center = center,
                                radius = maxRadius
                            ),
                            radius = maxRadius * 0.95f,
                            center = center
                        )

                        listOf(wave1, wave2).forEach { w ->
                            val r = maxRadius * w
                            val alpha = (1f - w) * 0.5f
                            drawCircle(
                                color = ElectricPillCyan.copy(alpha = alpha),
                                radius = r,
                                center = center,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }

                    // Floating Glowing Blue Sphere with Paper Airplane
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(ElectricPillBlue, ElectricPillCyan)
                                )
                            ),
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

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Status Texts
            item {
                Text(
                    text = when (uiState.connectionState) {
                        ConnectionState.CONNECTING -> "Connecting to ${uiState.targetPeer?.name ?: "receiver"}..."
                        ConnectionState.TRANSFERRING -> "Transferring files..."
                        ConnectionState.COMPLETED -> "Transfer completed"
                        ConnectionState.FAILED -> "Connection or transfer failed"
                        ConnectionState.DEVICE_DISCOVERED -> "Receiver discovered nearby"
                        else -> "Looking for nearby devices..."
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = GlassTextPrimary,
                        fontSize = 17.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (uiState.connectionState) {
                        ConnectionState.CONNECTING -> "Establishing direct TCP link with receiver..."
                        ConnectionState.TRANSFERRING -> "Streaming file bytes over Wi-Fi / Hotspot..."
                        ConnectionState.FAILED -> "Failed to connect. Check Wi-Fi / Hotspot connection."
                        else -> "Make sure receiver is in Receive mode on Wi-Fi or Hotspot."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = GlassTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Discovered Receivers List
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discovered Receivers (${uiState.discoveredPeers.size})",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = GlassTextPrimary
                        )
                    )
                    TextButton(onClick = { showManualIpDialog = true }) {
                        Text(
                            text = "Manual IP",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ElectricPillBlue,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            if (uiState.discoveredPeers.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Scanning network radar...",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = GlassTextPrimary
                                    )
                                )
                                Text(
                                    text = "Connect to receiver Hotspot or tap Direct Connect (192.168.43.1).",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = GlassTextSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            Button(
                                onClick = { onConnectManualIp("192.168.43.1") },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricPillBlue),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Beam 43.1",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            } else {
                items(uiState.discoveredPeers, key = { it.id }) { peer ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSendToPeer(peer) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(ElectricPillBlue, ElectricPillCyan))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = "Device",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = peer.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = GlassTextPrimary
                                        )
                                    )
                                    Text(
                                        text = "${peer.ipAddress}:${peer.port} • Signal ${peer.signalStrength}%",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = ElectricPillBlue,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }

                            val isSendingToThis = (uiState.connectionState == ConnectionState.CONNECTING || uiState.connectionState == ConnectionState.TRANSFERRING) && uiState.targetPeer?.id == peer.id

                            Button(
                                onClick = { onSendToPeer(peer) },
                                enabled = !uiState.isConnectingToPeer,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricPillBlue),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                if (isSendingToThis) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(13.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Sending",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Send",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Hotspot Sharing Helper Card
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE0E7FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiTethering,
                                    contentDescription = "Hotspot",
                                    tint = ElectricPillBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Hotspot Direct Beam",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = GlassTextPrimary
                                    )
                                )
                                Text(
                                    text = "Connect to Receiver's Hotspot or turn yours on",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = GlassTextSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onOpenHotspotSettings,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricPillBlue
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Quick Category Selectors
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassCategorySummary(
                        title = "Apps",
                        count = "${uiState.appCount}",
                        icon = Icons.Default.Android,
                        color = CatAppGreen,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToSelect(FileCategory.APPS) }
                    )
                    GlassCategorySummary(
                        title = "Photos",
                        count = "${uiState.photoCount}",
                        icon = Icons.Default.Image,
                        color = CatPhotoPink,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToSelect(FileCategory.PHOTOS) }
                    )
                    GlassCategorySummary(
                        title = "Videos",
                        count = "${uiState.videoCount}",
                        icon = Icons.Default.Videocam,
                        color = CatVideoPurple,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToSelect(FileCategory.VIDEOS) }
                    )
                    GlassCategorySummary(
                        title = "Docs",
                        count = "${uiState.docCount}",
                        icon = Icons.Default.Description,
                        color = CatDocBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToSelect(FileCategory.DOCS) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    if (showManualIpDialog) {
        AlertDialog(
            onDismissRequest = { showManualIpDialog = false },
            title = {
                Text(
                    text = "Direct IP Connection",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter the Receiver's IP address (default Android Hotspot gateway is 192.168.43.1):",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = manualIpText,
                        onValueChange = { manualIpText = it },
                        label = { Text("Receiver IP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { manualIpText = "192.168.43.1" }) {
                            Text("192.168.43.1")
                        }
                        TextButton(onClick = { manualIpText = "127.0.0.1" }) {
                            Text("127.0.0.1")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualIpText.isNotBlank()) {
                            showManualIpDialog = false
                            onConnectManualIp(manualIpText.trim())
                        }
                    }
                ) {
                    Text("Connect & Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualIpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun GlassCategorySummary(
    title: String,
    count: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = GlassTextPrimary,
                    fontSize = 11.sp
                )
            )
            Text(
                text = count,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = GlassTextSecondary,
                    fontSize = 10.sp
                )
            )
        }
    }
}
