package com.example.model

data class PeerDevice(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int = 8888,
    val deviceType: String = "Android",
    val signalStrength: Int = 95, // 0 - 100
    val isVerified: Boolean = true,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val alternateIp: String? = null
)
