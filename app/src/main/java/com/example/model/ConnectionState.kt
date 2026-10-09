package com.example.model

enum class ConnectionState {
    IDLE,
    SEARCHING,
    DEVICE_DISCOVERED,
    CONNECTING,
    CONNECTED,
    WAITING_FOR_FILES,
    RECEIVING,
    TRANSFERRING,
    COMPLETED,
    FAILED
}
