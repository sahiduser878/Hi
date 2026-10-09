package com.example.network

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    private const val TAG = "NetworkUtils"

    private fun isExcludedInterface(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith("rmnet") ||   // Qualcomm Cellular
                lower.startsWith("ccmni") ||  // MediaTek Cellular
                lower.startsWith("pdp") ||    // Legacy Cellular
                lower.startsWith("wwan") ||   // Generic WWAN / Cellular
                lower.startsWith("dummy") ||
                lower.startsWith("radio") ||
                lower.startsWith("tun") ||    // VPN
                lower.startsWith("ppp")
    }

    private fun getInterfaceScore(name: String): Int {
        val lower = name.lowercase()
        if (isExcludedInterface(name)) return -100

        return when {
            // Android Wi-Fi Hotspot / SoftAP / Tethering interfaces (highest priority)
            lower.startsWith("softap") ||
                    lower.startsWith("ap") ||
                    lower.startsWith("swlan") ||
                    lower.startsWith("wlan_ap") ||
                    lower.startsWith("tether") -> 100
            lower.startsWith("p2p") -> 90
            // Wi-Fi Station interfaces
            lower.startsWith("wlan") || lower.startsWith("wifi") -> 80
            // Ethernet
            lower.startsWith("eth") -> 70
            // USB Tethering
            lower.startsWith("rndis") -> 60
            else -> 10
        }
    }

    /**
     * Returns all candidate IPv4 addresses for local Wi-Fi / Hotspot file sharing,
     * ordered by interface priority (Hotspot / SoftAP > Wi-Fi Station > Ethernet).
     * Strictly filters out cellular/mobile data (rmnet/ccmni) and loopback addresses.
     */
    fun getAllLocalIpAddresses(context: Context): List<String> {
        val candidates = mutableListOf<Pair<Int, String>>()

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val score = getInterfaceScore(intf.name)
                if (score < 0) continue // Skip cellular/VPN

                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (!host.startsWith("127.") && host != "0.0.0.0") {
                            // Give slight preference to private subnets (192.168.x.x, 172.16-31.x.x, 10.x.x.x)
                            val subnetBonus = when {
                                host.startsWith("192.168.") -> 5
                                host.startsWith("172.") -> 3
                                else -> 0
                            }
                            candidates.add(Pair(score + subnetBonus, host))
                            Log.d(TAG, "Found candidate IP: $host on interface ${intf.name} (score=${score + subnetBonus})")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating network interfaces", e)
        }

        // Also check WifiManager connection info if available
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiIpInt = wifiManager?.connectionInfo?.ipAddress
            if (wifiIpInt != null && wifiIpInt != 0) {
                val wifiIpStr = String.format(
                    "%d.%d.%d.%d",
                    wifiIpInt and 0xff,
                    wifiIpInt shr 8 and 0xff,
                    wifiIpInt shr 16 and 0xff,
                    wifiIpInt shr 24 and 0xff
                )
                if (wifiIpStr != "0.0.0.0" && candidates.none { it.second == wifiIpStr }) {
                    candidates.add(Pair(85, wifiIpStr))
                    Log.d(TAG, "Found WifiManager IP: $wifiIpStr")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WifiManager connection info", e)
        }

        return candidates
            .sortedByDescending { it.first }
            .map { it.second }
            .distinct()
    }

    /**
     * Returns the primary local IPv4 address for Wi-Fi / Hotspot sharing.
     * Guaranteed to never return a cellular IP (10.x.x.x rmnet) if a Wi-Fi/Hotspot interface is present.
     */
    fun getLocalIpAddress(context: Context): String {
        val allIps = getAllLocalIpAddresses(context)
        val selected = allIps.firstOrNull() ?: "127.0.0.1"
        Log.d(TAG, "Selected primary local IP: $selected from candidates: $allIps")
        return selected
    }

    /**
     * Determines whether Wi-Fi or Hotspot connectivity is active.
     */
    fun isWifiOrHotspotConnected(context: Context): Boolean {
        if (isHotspotActive(context)) return true

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Gets the connected Wi-Fi SSID if available.
     */
    fun getWifiSsid(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info = wifiManager?.connectionInfo
            val ssid = info?.ssid?.replace("\"", "")
            if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
                return ssid
            }
        } catch (_: Exception) {}
        return "Direct Wi-Fi / Hotspot"
    }

    /**
     * Checks whether this device is currently hosting a Wi-Fi Hotspot (SoftAP).
     */
    fun isHotspotActive(context: Context): Boolean {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val name = intf.name.lowercase()
                val isHotspotInterface = name.startsWith("softap") ||
                        name.startsWith("ap") ||
                        name.startsWith("swlan") ||
                        name.startsWith("wlan_ap") ||
                        name.startsWith("tether")
                if (isHotspotInterface && intf.isUp) {
                    val addrs = Collections.list(intf.inetAddresses)
                    if (addrs.any { !it.isLoopbackAddress && it is Inet4Address }) {
                        return true
                    }
                }
            }
            // Check if local IP is a known Hotspot AP IP
            val allIps = getAllLocalIpAddresses(context)
            if (allIps.any { it == "192.168.43.1" || it.startsWith("192.168.49.") }) {
                return true
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Gets the Hotspot Gateway IP address.
     * When this device is connected to another device's Hotspot, the Gateway is the Host IP!
     */
    fun getHotspotGatewayIp(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcpInfo = wifiManager?.dhcpInfo
            val gatewayInt = dhcpInfo?.gateway
            if (gatewayInt != null && gatewayInt != 0) {
                val gatewayIp = String.format(
                    "%d.%d.%d.%d",
                    gatewayInt and 0xff,
                    gatewayInt shr 8 and 0xff,
                    gatewayInt shr 16 and 0xff,
                    gatewayInt shr 24 and 0xff
                )
                if (gatewayIp != "0.0.0.0") {
                    return gatewayIp
                }
            }
        } catch (_: Exception) {}
        return "192.168.43.1"
    }

    fun openHotspotSettings(context: Context): Boolean {
        val tetheringIntents = listOf(
            Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
            Intent("android.settings.TETHER_SETTINGS"),
            Intent("android.settings.WIFI_AP_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_WIFI_SETTINGS)
        )
        for (intent in tetheringIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }
        return false
    }

    fun openWifiSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
