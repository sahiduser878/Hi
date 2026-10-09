package com.example.model

data class TransferStats(
    val totalSentBytes: Long = 0L,
    val totalReceivedBytes: Long = 0L,
    val filesSentCount: Int = 0,
    val filesReceivedCount: Int = 0,
    val peakSpeedBytesPerSec: Long = 0L
) {
    val totalTransferredBytes: Long
        get() = totalSentBytes + totalReceivedBytes

    val formattedSent: String
        get() = ShareFileItem.formatBytes(totalSentBytes)

    val formattedReceived: String
        get() = ShareFileItem.formatBytes(totalReceivedBytes)

    val formattedTotal: String
        get() = ShareFileItem.formatBytes(totalTransferredBytes)

    val formattedPeakSpeed: String
        get() {
            val mb = peakSpeedBytesPerSec / (1024.0 * 1024.0)
            return if (mb >= 1.0) String.format("%.1f MB/s", mb) else "${peakSpeedBytesPerSec / 1024} KB/s"
        }
}
