package com.example.model

enum class TransferDirection {
    SENDING,
    RECEIVING
}

enum class TransferStatus {
    QUEUED,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TransferItem(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val bytesTransferred: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val status: TransferStatus = TransferStatus.QUEUED,
    val direction: TransferDirection = TransferDirection.SENDING,
    val uri: String? = null,
    val localFilePath: String? = null,
    val mimeType: String = "*/*",
    val peerName: String = "Nearby Device",
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (fileSize > 0) (bytesTransferred.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt()

    val formattedSpeed: String
        get() {
            if (speedBytesPerSec <= 0) return "0 KB/s"
            val kb = speedBytesPerSec / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format("%.1f MB/s", mb)
            } else {
                String.format("%.0f KB/s", kb)
            }
        }

    val formattedSize: String
        get() = ShareFileItem.formatBytes(fileSize)

    val formattedTransferred: String
        get() = ShareFileItem.formatBytes(bytesTransferred)
}
