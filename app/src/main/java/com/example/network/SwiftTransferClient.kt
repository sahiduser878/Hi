package com.example.network

import android.content.Context
import android.util.Log
import com.example.model.FileCategory
import com.example.model.PeerDevice
import com.example.model.ShareFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder

class SwiftTransferClient(private val context: Context) {

    companion object {
        private const val TAG = "SwiftTransferClient"
    }

    suspend fun sendFileToPeer(
        peerIp: String,
        peerPort: Int,
        fileItem: ShareFileItem,
        onProgress: (bytesTransferred: Long, totalBytes: Long, speed: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var connection: HttpURLConnection? = null
        try {
            val encodedName = URLEncoder.encode(fileItem.name, "UTF-8")
            val targetUrl = URL("http://$peerIp:$peerPort/api/upload?filename=$encodedName")
            Log.d(TAG, "Connecting to peer at $targetUrl for sending '${fileItem.name}' (${fileItem.size} bytes)")

            connection = (targetUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 60000
                setRequestProperty("Content-Type", fileItem.mimeType)
                setRequestProperty("x-filename", encodedName)

                // Use Fixed Length streaming mode when size is known, so no chunked overhead or buffering
                if (fileItem.size > 0) {
                    setFixedLengthStreamingMode(fileItem.size)
                    setRequestProperty("Content-Length", fileItem.size.toString())
                } else {
                    setChunkedStreamingMode(64 * 1024)
                }
            }

            inputStream = when {
                fileItem.uri != null -> {
                    try {
                        context.contentResolver.openInputStream(fileItem.uri)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open Uri input stream for ${fileItem.uri}", e)
                        null
                    }
                }
                fileItem.filePath != null -> {
                    try {
                        File(fileItem.filePath).inputStream()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open File input stream for ${fileItem.filePath}", e)
                        null
                    }
                }
                else -> null
            }

            if (inputStream == null) {
                Log.e(TAG, "Cannot read file: ${fileItem.name}, aborting transfer")
                return@withContext false
            }

            val out = BufferedOutputStream(connection.outputStream)
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var totalSent = 0L
            val fileSize = if (fileItem.size > 0) fileItem.size else 1L
            var lastTime = System.currentTimeMillis()
            var bytesSinceSample = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
                totalSent += bytesRead
                bytesSinceSample += bytesRead

                val now = System.currentTimeMillis()
                val delta = now - lastTime
                if (delta >= 250) {
                    val speed = (bytesSinceSample * 1000) / maxOf(1L, delta)
                    lastTime = now
                    bytesSinceSample = 0L
                    onProgress(totalSent, fileSize, speed)
                }
            }
            out.flush()
            onProgress(fileSize, fileSize, 0L)

            val responseCode = connection.responseCode
            val success = responseCode in 200..299
            Log.i(TAG, "Sent '${fileItem.name}' to $peerIp:$peerPort. Server responseCode=$responseCode, success=$success")
            return@withContext success
        } catch (e: Exception) {
            Log.e(TAG, "Error sending file '${fileItem.name}' to $peerIp:$peerPort", e)
            return@withContext false
        } finally {
            try {
                inputStream?.close()
            } catch (_: Exception) {}
            try {
                connection?.disconnect()
            } catch (_: Exception) {}
        }
    }

    /**
     * Receiver asks Sender to beam files over to Receiver's IP/port.
     * Tries the primary IP, and if that fails, tries the alternate IP if available.
     */
    suspend fun requestTransferFromSender(
        senderIp: String,
        senderPort: Int,
        receiverPort: Int,
        receiverIp: String,
        alternateIp: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val targets = mutableListOf(senderIp)
        if (!alternateIp.isNullOrBlank() && alternateIp != senderIp) {
            targets.add(alternateIp)
        }

        for (target in targets) {
            try {
                val encodedReceiverIp = URLEncoder.encode(receiverIp, "UTF-8")
                val urlString = "http://$target:$senderPort/api/request_files?port=$receiverPort&receiverIp=$encodedReceiverIp"
                Log.d(TAG, "Requesting transfer from sender at $urlString")

                val url = URL(urlString)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..299) {
                    Log.i(TAG, "Successfully connected and requested transfer from sender $target:$senderPort")
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to connect to sender at $target:$senderPort: ${e.message}")
            }
        }
        return@withContext false
    }

    /**
     * Queries the sender's shared file list directly.
     */
    suspend fun fetchSharedFiles(peerIp: String, peerPort: Int): List<ShareFileItem> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = URL("http://$peerIp:$peerPort/api/files")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 4000
            }
            if (conn.responseCode in 200..299) {
                val jsonString = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val jsonArray = JSONArray(jsonString)
                val list = mutableListOf<ShareFileItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        ShareFileItem(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            size = obj.getLong("size"),
                            mimeType = obj.optString("mime", "*/*"),
                            category = FileCategory.FILES
                        )
                    )
                }
                list
            } else {
                conn.disconnect()
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching shared files from $peerIp:$peerPort", e)
            emptyList()
        }
    }

    /**
     * Receiver pulls/downloads a file directly from the sender's server.
     */
    suspend fun downloadFileFromPeer(
        peerIp: String,
        peerPort: Int,
        fileId: String,
        destFile: File,
        onProgress: (bytesRead: Long, totalBytes: Long, speed: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var input: InputStream? = null
        var output: FileOutputStream? = null
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$peerIp:$peerPort/api/download?id=${URLEncoder.encode(fileId, "UTF-8")}")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 60000
            }

            if (conn.responseCode !in 200..299) {
                return@withContext false
            }

            val totalBytes = conn.contentLength.toLong()
            input = BufferedInputStream(conn.inputStream)
            output = FileOutputStream(destFile)

            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var totalRead = 0L
            var lastTime = System.currentTimeMillis()
            var bytesSinceSample = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                bytesSinceSample += bytesRead

                val now = System.currentTimeMillis()
                val delta = now - lastTime
                if (delta >= 250) {
                    val speed = (bytesSinceSample * 1000) / maxOf(1L, delta)
                    lastTime = now
                    bytesSinceSample = 0L
                    onProgress(totalRead, if (totalBytes > 0) totalBytes else totalRead, speed)
                }
            }
            output.flush()
            onProgress(totalRead, totalRead, 0L)
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file $fileId from $peerIp:$peerPort", e)
            destFile.delete()
            return@withContext false
        } finally {
            try { input?.close() } catch (_: Exception) {}
            try { output?.close() } catch (_: Exception) {}
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Scans local subnet, hotspot gateway, and local loopback for active receivers.
     */
    suspend fun scanLocalSubnet(localIp: String, port: Int = 8888): List<PeerDevice> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<PeerDevice>()

        // 1. Probe Hotspot Gateway (from DHCP gateway and 192.168.43.1 / 192.168.49.1)
        val hotspotIps = mutableListOf("192.168.43.1", "192.168.49.1")
        val gateway = NetworkUtils.getHotspotGatewayIp(context)
        if (gateway.isNotBlank() && !hotspotIps.contains(gateway)) {
            hotspotIps.add(0, gateway)
        }

        for (hIp in hotspotIps) {
            if (hIp != localIp && isPortOpen(hIp, port, 250)) {
                discovered.add(
                    PeerDevice(
                        id = "hotspot_$hIp",
                        name = "Hotspot Device ($hIp)",
                        ipAddress = hIp,
                        port = port,
                        deviceType = "Android",
                        signalStrength = 99
                    )
                )
            }
        }

        // 2. Check local port (for instant self-beam local test)
        if (isPortOpen("127.0.0.1", port, 150)) {
            discovered.add(
                PeerDevice(
                    id = "local_receiver_loopback",
                    name = "This Device (Local Beam Test)",
                    ipAddress = "127.0.0.1",
                    port = port,
                    deviceType = "Android",
                    signalStrength = 100
                )
            )
        }

        // 3. Scan adjacent IPs on the local subnet
        if (localIp.contains(".") && !localIp.startsWith("127.")) {
            val prefix = localIp.substringBeforeLast(".")
            val myLastOctet = localIp.substringAfterLast(".").toIntOrNull() ?: 1
            val scanTargets = (maxOf(1, myLastOctet - 10)..minOf(254, myLastOctet + 10)).filter { it != myLastOctet }

            val subnetResults = coroutineScope {
                scanTargets.map { octet ->
                    async {
                        val testIp = "$prefix.$octet"
                        if (isPortOpen(testIp, port, 180)) {
                            PeerDevice(
                                id = "ip_$testIp",
                                name = "Nearby Receiver ($testIp)",
                                ipAddress = testIp,
                                port = port,
                                deviceType = "Android",
                                signalStrength = 85
                            )
                        } else null
                    }
                }.awaitAll().filterNotNull()
            }
            discovered.addAll(subnetResults)
        }

        return@withContext discovered.distinctBy { it.ipAddress }
    }

    fun isPortOpen(ip: String, port: Int, timeoutMs: Int = 300): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
