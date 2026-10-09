package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import com.example.model.PeerDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class PeerDiscoveryManager(
    private val context: Context,
    private val broadcastPort: Int = 8889,
    private val tcpPort: Int = 8888
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var beaconJob: Job? = null
    private var listenerJob: Job? = null
    private var receiverDiscoveryJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    init {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("swiftshare_multicast")
            multicastLock?.setReferenceCounted(true)
        } catch (_: Exception) {}
    }

    /**
     * Receiver calls this to announce itself over UDP broadcast on the Wi-Fi / Hotspot network.
     */
    fun startReceiverBeacon(deviceName: String, port: Int = tcpPort) {
        startBeacon("SWIFTSHARE_RECEIVER", deviceName, port)
    }

    /**
     * Sender calls this to announce itself so receivers can also discover senders nearby.
     */
    fun startSenderBeacon(deviceName: String, port: Int = tcpPort) {
        startBeacon("SWIFTSHARE_SENDER", deviceName, port)
    }

    private fun startBeacon(prefixHeader: String, deviceName: String, port: Int) {
        stopBeacon()
        beaconJob = scope.launch {
            try {
                multicastLock?.acquire()
            } catch (_: Exception) {}

            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true

                while (isActive) {
                    val localIp = NetworkUtils.getLocalIpAddress(context)
                    val message = "$prefixHeader:$deviceName:$localIp:$port"
                    val data = message.toByteArray(Charsets.UTF_8)

                    // 1. Send to standard broadcast
                    try {
                        val broadcastAddr = InetAddress.getByName("255.255.255.255")
                        val packet = DatagramPacket(data, data.size, broadcastAddr, broadcastPort)
                        socket.send(packet)
                    } catch (_: Exception) {}

                    // 2. Subnet broadcast
                    if (localIp.contains(".")) {
                        try {
                            val prefix = localIp.substringBeforeLast(".")
                            val subnetBroadcast = InetAddress.getByName("$prefix.255")
                            val packet = DatagramPacket(data, data.size, subnetBroadcast, broadcastPort)
                            socket.send(packet)
                        } catch (_: Exception) {}
                    }

                    // 3. Android Hotspot default subnet broadcast (192.168.43.255)
                    try {
                        val hotspotBroadcast = InetAddress.getByName("192.168.43.255")
                        val packet = DatagramPacket(data, data.size, hotspotBroadcast, broadcastPort)
                        socket.send(packet)
                    } catch (_: Exception) {}

                    delay(1200)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {}
                try {
                    if (multicastLock?.isHeld == true) multicastLock?.release()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Sender calls this to listen for active Receiver beacons and broadcast search probes.
     */
    fun startSenderDiscovery(onReceiverFound: (PeerDevice) -> Unit) {
        stopDiscovery()
        listenerJob = scope.launch {
            listenForPeers("SWIFTSHARE_RECEIVER", "Receiver", onReceiverFound)
        }
    }

    /**
     * Receiver calls this to listen for active Sender beacons and search probes.
     */
    fun startReceiverDiscovery(onSenderFound: (PeerDevice) -> Unit) {
        receiverDiscoveryJob?.cancel()
        receiverDiscoveryJob = scope.launch {
            listenForPeers("SWIFTSHARE_SENDER", "Sender", onSenderFound)
        }
    }

    private suspend fun listenForPeers(
        expectedPrefix: String,
        defaultRoleName: String,
        onDeviceFound: (PeerDevice) -> Unit
    ) {
        try {
            multicastLock?.acquire()
        } catch (_: Exception) {}

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(broadcastPort))
                soTimeout = 2000
            }

            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)

            while (scope.isActive) {
                try {
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    val senderIp = packet.address?.hostAddress ?: ""

                    if (text.startsWith("$expectedPrefix:")) {
                        val parts = text.split(":")
                        val deviceName = if (parts.size >= 2) parts[1] else "$defaultRoleName Device"
                        val declaredIp = if (parts.size >= 3 && parts[2].isNotBlank()) parts[2] else senderIp
                        val targetPort = if (parts.size >= 4) parts[3].toIntOrNull() ?: tcpPort else tcpPort

                        val finalIp = if (declaredIp != "127.0.0.1" && declaredIp.isNotBlank()) declaredIp else senderIp
                        if (finalIp.isNotBlank()) {
                            onDeviceFound(
                                PeerDevice(
                                    id = "peer_$finalIp",
                                    name = deviceName,
                                    ipAddress = finalIp,
                                    port = targetPort,
                                    deviceType = "Android",
                                    signalStrength = 95
                                )
                            )
                        }
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Socket timeout is normal; loop continues
                } catch (_: Exception) {}

                // Active subnet & Hotspot probe fallback in background every loop iteration
                val localIp = NetworkUtils.getLocalIpAddress(context)
                val testIps = mutableListOf<String>()

                // Check Hotspot gateway (192.168.43.1)
                testIps.add("192.168.43.1")
                testIps.add("192.168.49.1")

                if (localIp.contains(".") && localIp != "127.0.0.1") {
                    val prefix = localIp.substringBeforeLast(".")
                    val lastOctet = localIp.substringAfterLast(".").toIntOrNull() ?: 1
                    testIps.add("$prefix.1")
                    testIps.add("$prefix.2")
                    if (lastOctet > 1) testIps.add("$prefix.${lastOctet - 1}")
                    if (lastOctet < 254) testIps.add("$prefix.${lastOctet + 1}")
                }

                for (testIp in testIps.distinct()) {
                    if (testIp != localIp && isPortOpen(testIp, tcpPort, 150)) {
                        onDeviceFound(
                            PeerDevice(
                                id = "peer_$testIp",
                                name = "$defaultRoleName ($testIp)",
                                ipAddress = testIp,
                                port = tcpPort,
                                deviceType = "Android",
                                signalStrength = 94
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
            try {
                if (multicastLock?.isHeld == true) multicastLock?.release()
            } catch (_: Exception) {}
        }
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun stopBeacon() {
        beaconJob?.cancel()
        beaconJob = null
    }

    fun stopDiscovery() {
        listenerJob?.cancel()
        listenerJob = null
        receiverDiscoveryJob?.cancel()
        receiverDiscoveryJob = null
    }

    fun stopAll() {
        stopBeacon()
        stopDiscovery()
        try {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        } catch (_: Exception) {}
    }
}
