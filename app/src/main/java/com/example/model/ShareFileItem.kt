package com.example.model

import android.net.Uri

data class ShareFileItem(
    val id: String,
    val name: String,
    val size: Long,
    val uri: Uri? = null,
    val filePath: String? = null,
    val mimeType: String = "*/*",
    val category: FileCategory = FileCategory.FILES,
    val packageName: String? = null,
    val isApp: Boolean = false,
    val dateModified: Long = System.currentTimeMillis(),
    val isSelected: Boolean = false
) {
    val formattedSize: String
        get() = formatBytes(size)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.1f MB", mb)
                kb >= 1.0 -> String.format("%.1f KB", kb)
                else -> "$bytes B"
            }
        }
    }
}
