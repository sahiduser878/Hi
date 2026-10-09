package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonPurple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileItemCard(
    fileItem: ShareFileItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier,
    showCheckbox: Boolean = true
) {
    val (icon, gradientColors) = getCategoryVisuals(fileItem.category, fileItem.isApp)

    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (isSelected) CyberCyan else Color.White.copy(alpha = 0.08f)
    val bgColor = if (isSelected) CyberCyan.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface

    Row(
        modifier = modifier
            .testTag("file_item_${fileItem.id}")
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onToggleSelect)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon / Avatar
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(gradientColors)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = fileItem.category.displayName,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // File Details
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = fileItem.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = fileItem.formattedSize,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = CyberCyan
                    )
                )
                Text(
                    text = " • ",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(fileItem.dateModified))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1
                )
            }
        }

        if (showCheckbox) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.testTag("checkbox_${fileItem.id}"),
                colors = CheckboxDefaults.colors(
                    checkedColor = CyberCyan,
                    checkmarkColor = Color(0xFF090D16),
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

private fun getCategoryVisuals(category: FileCategory, isApp: Boolean): Pair<ImageVector, List<Color>> {
    if (isApp) {
        return Icons.Default.Android to listOf(Color(0xFF00C853), Color(0xFF64DD17))
    }
    return when (category) {
        FileCategory.APPS -> Icons.Default.Android to listOf(Color(0xFF00C853), Color(0xFF64DD17))
        FileCategory.PHOTOS -> Icons.Default.Image to listOf(CyberCyan, ElectricBlue)
        FileCategory.VIDEOS -> Icons.Default.Movie to listOf(Color(0xFFFF5252), Color(0xFFFF1744))
        FileCategory.MUSIC -> Icons.Default.AudioFile to listOf(NeonPurple, Color(0xFFD500F9))
        FileCategory.DOCS -> Icons.Default.Description to listOf(Color(0xFFFF9100), Color(0xFFFF6D00))
        FileCategory.FILES, FileCategory.ALL -> Icons.Default.Folder to listOf(Color(0xFF2979FF), Color(0xFF00B0FF))
    }
}
