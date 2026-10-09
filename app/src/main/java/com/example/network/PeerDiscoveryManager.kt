package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
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

class PeerDiscoveryManager(
    private val context: Context,
    private val broadcastPort: Int = 8889,
    private val tcpPort: Int = 8888
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var beaconJob: Job? = null
    private var senderListenerJob: Job? = null
    private var receiverDiscoveryJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    companion object {
        private const val TAG = "PeerDiscovery"
    }

    init {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("swiftshare_multicast")
            multicastLock?.setReferenceCounted(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create multicast lock", e)
        }
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
                socket = DatagramSocket().apply {
                    broadcast = true
                }

                while (isActive) {
                    val localIps = NetworkUtils.getAllLocalIpAddresses(context)
                    val primaryIp = NetworkUtils.getLocalIpAddress(context)
                    val message = "$prefixHeader:$deviceName:$primaryIp:$port"
                    val data = message.toByteArray(Charsets.UTF_8)

                    // 1. Send to standard global broadcast
                    try {
                        val broadcastAddr = InetAddress.getByName("255.255.255.255")
                        socket.send(DatagramPacket(data, data.size, broadcastAddr, broadcastPort))
                    } catch (_: Exception) {}

                    // 2. Send to every active local subnet's directed broadcast
                    for (ip in localIps) {
                        if (ip.contains(".")) {
                            try {
                                val prefix = ip.substringBeforeLast(".")
                                val subnetBroadcast = InetAddress.getByName("$prefix.255")
                                socket.send(DatagramPacket(data, data.size, subnetBroadcast, broadcastPort))
                            } catch (_: Exception) {}
                        }
                    }

                    // 3. Android Hotspot default subnet broadcast (192.168.43.255)
                    try {
                        val hotspotBroadcast = InetAddress.getByName("192.168.43.255")
                        socket.send(DatagramPacket(data, data.size, hotspotBroadcast, broadcastPort))
                    } catch (_: Exception) {}

                    // 4. DHCP Gateway broadcast if connected as client to another device's Hotspot
                    val gatewayIp = NetworkUtils.getHotspotGatewayIp(context)
                    if (gatewayIp.contains(".")) {
                        try {
                            val gwPrefix = gatewayIp.substringBeforeLast(".")
                            val gwBroadcast = InetAddress.getByName("$gwPrefix.255")
                            socket.send(DatagramPacket(data, data.size, gwBroadcast, broadcastPort))
                            // Also send directly to gateway IP
                            socket.send(DatagramPacket(data, data.size, InetAddress.getByName(gatewayIp), broadcastPort))
                        } catch (_: Exception) {}
                    }

                    delay(1200)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in beacon loop for $prefixHeader", e)
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
     * Sender calls this to listen for active Receiver beacons and search probes.
     */
    fun startSenderDiscovery(onReceiverFound: (PeerDevice) -> Unit) {
        senderListenerJob?.cancel()
        senderListenerJob = scope.launch {
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
                soTimeout = 2500
            }

            val buffer = ByteArray(2048)
            val packet = DatagramPacket(buffer, buffer.size)

            while (scope.isActive) {
                try {
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    val packetSenderIp = packet.address?.hostAddress ?: ""

                    if (text.startsWith("$expectedPrefix:")) {
                        val parts = text.split(":")
                        val deviceName = if (parts.size >= 2 && parts[1].isNotBlank()) parts[1] else "$defaultRoleName Device"
                        val declaredIp = if (parts.size >= 3 && parts[2].isNotBlank()) parts[2] else packetSenderIp
                        val targetPort = if (parts.size >= 4) parts[3].toIntOrNull() ?: tcpPort else tcpPort

                        // Crucial fix: The packet's physical source IP (packetSenderIp) arrived over
                        // the shared radio link, making it the most reliable reachable address.
                        val effectiveIp = if (packetSenderIp.isNotBlank() && packetSenderIp != "127.0.0.1") {
                            packetSenderIp
                        } else if (declaredIp.isNotBlank() && declaredIp != "127.0.0.1") {
                            declaredIp
                        } else {
                            ""
                        }

                        val altIp = if (declaredIp.isNotBlank() && declaredIp != effectiveIp && declaredIp != "127.0.0.1") {
                            declaredIp
                        } else null

                        if (effectiveIp.isNotBlank()) {
                            Log.d(TAG, "Discovered $defaultRoleName: '$deviceName' at $effectiveIp:$targetPort (alt=$altIp)")
                            onDeviceFound(
                                PeerDevice(
                                    id = "peer_${effectiveIp}_$targetPort",
                                    name = deviceName,
                                    ipAddress = effectiveIp,
                                    port = targetPort,
                                    deviceType = "Android",
                                    signalStrength = 95,
                                    alternateIp = altIp
                                )
                            )
                        }
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Normal timeout for soTimeout
                } catch (e: Exception) {
                    Log.e(TAG, "Error receiving peer packet", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in listenForPeers socket setup", e)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
            try {
                if (multicastLock?.isHeld == true) multicastLock?.release()
            } catch (_: Exception) {}
        }
    }

    fun stopBeacon() {
        beaconJob?.cancel()
        beaconJob = null
    }

    fun stopDiscovery() {
        senderListenerJob?.cancel()
        senderListenerJob = null
        receiverDiscoveryJob?.cancel()
        receiverDiscoveryJob = null
    }

    fun stopAll() {
        stopBeacon()
        stopDiscovery()
    }
}
