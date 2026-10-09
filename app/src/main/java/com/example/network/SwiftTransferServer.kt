package com.example.network

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.FileManagerRepository
import com.example.model.ShareFileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

class SwiftTransferServer(
    private val context: Context,
    private val port: Int = 8888,
    private val onTransferStarted: (fileName: String, totalBytes: Long) -> Unit = { _, _ -> },
    private val onProgress: (fileName: String, bytesTransferred: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit = { _, _, _, _ -> },
    private val onFileReceived: (file: File, originalName: String, size: Long, mimeType: String) -> Unit = { _, _, _, _ -> },
    private val onPeerConnected: (clientIp: String) -> Unit = {},
    var onRequestTransferFromPeer: ((peerIp: String, peerPort: Int) -> Unit)? = null
) {
    companion object {
        private const val TAG = "SwiftTransferServer"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    var isRunning = false
        private set

    var actualPort: Int = port
        private set

    var sharedFiles: List<ShareFileItem> = emptyList()

    fun start(): Boolean {
        if (isRunning) return true

        try {
            serverSocket = ServerSocket(port).apply {
                reuseAddress = true
            }
            actualPort = serverSocket!!.localPort
            isRunning = true
            Log.i(TAG, "Server started successfully on port $actualPort")

            serverJob = scope.launch {
                while (isRunning) {
                    try {
                        val client = serverSocket!!.accept()
                        launch {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        if (isRunning) Log.e(TAG, "Exception in accept loop", e)
                        break
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server on port $port, trying ephemeral port...", e)
            try {
                serverSocket = ServerSocket(0).apply { reuseAddress = true }
                actualPort = serverSocket!!.localPort
                isRunning = true
                Log.i(TAG, "Server started on fallback port $actualPort")
                serverJob = scope.launch {
                    while (isRunning) {
                        try {
                            val client = serverSocket!!.accept()
                            launch { handleClient(client) }
                        } catch (_: Exception) {
                            break
                        }
                    }
                }
                return true
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to bind any port for SwiftTransferServer", ex)
                isRunning = false
                return false
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
        serverSocket = null
        Log.i(TAG, "Server stopped")
    }

    private fun handleClient(socket: Socket) {
        val clientIp = socket.inetAddress?.hostAddress ?: "Unknown"
        Log.d(TAG, "Client connected from $clientIp")
        onPeerConnected(clientIp)

        try {
            socket.soTimeout = 45000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            // Read HTTP header directly from input stream without wrapping in BufferedReader.
            // Wrapping input in BufferedReader would buffer ahead into characters,
            // corrupting binary payload for POST /api/upload!
            val (requestLine, headers) = readHttpHeader(input)
            if (requestLine.isBlank()) {
                socket.close()
                return
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return
            }

            val method = parts[0].uppercase()
            val fullPath = parts[1]
            val path = if (fullPath.contains("?")) fullPath.substringBefore("?") else fullPath
            val queryString = if (fullPath.contains("?")) fullPath.substringAfter("?") else ""

            Log.d(TAG, "HTTP Request: $method $path (client: $clientIp)")

            when {
                method == "GET" && path == "/" -> {
                    serveWebPortal(output)
                }
                method == "GET" && path == "/api/info" -> {
                    serveApiInfo(output)
                }
                method == "GET" && path == "/api/files" -> {
                    val jsonArray = sharedFiles.joinToString(separator = ",", prefix = "[", postfix = "]") { file ->
                        """{"id":"${file.id}","name":"${escapeJson(file.name)}","size":${file.size},"mime":"${file.mimeType}"}"""
                    }
                    sendHttpResponse(output, 200, "OK", "application/json", jsonArray.toByteArray(Charsets.UTF_8))
                }
                method == "POST" && path.startsWith("/api/request_files") -> {
                    val targetPort = extractQueryParam(queryString, "port")?.toIntOrNull() ?: 8888
                    val declaredReceiverIp = extractQueryParam(queryString, "receiverIp")
                    // If receiverIp was passed and valid, use it; otherwise fallback to socket clientIp
                    val effectiveReceiverIp = if (!declaredReceiverIp.isNullOrBlank() && declaredReceiverIp != "127.0.0.1") {
                        declaredReceiverIp
                    } else {
                        clientIp
                    }
                    Log.i(TAG, "Request files endpoint triggered by $clientIp! targetIp=$effectiveReceiverIp, targetPort=$targetPort")
                    onRequestTransferFromPeer?.invoke(effectiveReceiverIp, targetPort)
                    val responseJson = """{"status":"accepted","peer":"$effectiveReceiverIp","port":$targetPort}"""
                    sendHttpResponse(output, 200, "OK", "application/json", responseJson.toByteArray(Charsets.UTF_8))
                }
                method == "GET" && path.startsWith("/api/download") -> {
                    val id = extractQueryParam(queryString, "id")
                    val fileItem = sharedFiles.find { it.id == id }
                    if (fileItem != null) {
                        streamFileToClient(fileItem, output)
                    } else {
                        sendHttpResponse(output, 404, "Not Found", "text/plain", "File not found".toByteArray())
                    }
                }
                method == "POST" && path.startsWith("/api/upload") -> {
                    val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
                    val rawFilename = headers["x-filename"] ?: extractQueryParam(queryString, "filename")
                    val fileName = if (!rawFilename.isNullOrEmpty()) {
                        try { URLDecoder.decode(rawFilename, "UTF-8") } catch (_: Exception) { rawFilename }
                    } else {
                        "received_${System.currentTimeMillis()}.bin"
                    }
                    val mimeType = headers["content-type"] ?: "application/octet-stream"

                    Log.i(TAG, "Starting incoming upload: $fileName ($contentLength bytes, mime=$mimeType)")
                    onTransferStarted(fileName, contentLength)
                    handleIncomingUpload(input, contentLength, fileName, mimeType, output)
                }
                else -> {
                    sendHttpResponse(output, 404, "Not Found", "text/plain", "Not Found".toByteArray())
                }
            }
            output.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client $clientIp", e)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Reads the HTTP header byte-by-byte from BufferedInputStream without reading past \r\n\r\n.
     * Guarantees the very next byte in input is byte 0 of the HTTP request body!
     */
    private fun readHttpHeader(input: BufferedInputStream): Pair<String, Map<String, String>> {
        val headerBytes = ByteArrayOutputStream()
        var prev3 = -1
        var prev2 = -1
        var prev1 = -1

        while (true) {
            val b = input.read()
            if (b == -1) break
            headerBytes.write(b)

            if (prev1 == '\n'.code && b == '\n'.code) break // \n\n
            if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) break // \r\n\r\n

            prev3 = prev2
            prev2 = prev1
            prev1 = b

            if (headerBytes.size() > 65536) { // 64KB max header safety limit
                break
            }
        }

        val headerText = headerBytes.toString("UTF-8")
        val lines = headerText.lines()
        val requestLine = lines.firstOrNull()?.trim() ?: ""
        val headers = HashMap<String, String>()

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            val idx = line.indexOf(':')
            if (idx != -1) {
                val key = line.substring(0, idx).trim().lowercase()
                val value = line.substring(idx + 1).trim()
                headers[key] = value
            }
        }

        return Pair(requestLine, headers)
    }

    private fun handleIncomingUpload(
        input: InputStream,
        contentLength: Long,
        originalFileName: String,
        mimeType: String,
        output: OutputStream
    ) {
        val destDir = FileManagerRepository.getReceivedFilesDir(context)
        val targetFile = getUniqueDestinationFile(destDir, originalFileName)

        var totalRead = 0L
        var lastTime = System.currentTimeMillis()
        var bytesSinceLastSample = 0L
        var speed = 0L

        val buffer = ByteArray(64 * 1024)
        var outStream: FileOutputStream? = null

        try {
            outStream = FileOutputStream(targetFile)
            val bytesToReadTotal = if (contentLength > 0) contentLength else Long.MAX_VALUE

            while (totalRead < bytesToReadTotal) {
                val toRead = if (contentLength > 0) {
                    minOf(buffer.size.toLong(), bytesToReadTotal - totalRead).toInt()
                } else {
                    buffer.size
                }
                val read = input.read(buffer, 0, toRead)
                if (read == -1) break
                outStream.write(buffer, 0, read)
                totalRead += read
                bytesSinceLastSample += read

                val now = System.currentTimeMillis()
                val delta = now - lastTime
                if (delta >= 250) {
                    speed = (bytesSinceLastSample * 1000) / maxOf(1L, delta)
                    lastTime = now
                    bytesSinceLastSample = 0L
                    onProgress(targetFile.name, totalRead, if (contentLength > 0) contentLength else totalRead, speed)
                }
            }
            outStream.flush()
            outStream.close()
            outStream = null

            // Validate that we didn't receive an incomplete file
            if (contentLength > 0 && totalRead < contentLength) {
                Log.w(TAG, "Upload incomplete: expected $contentLength bytes, but only received $totalRead bytes. Deleting incomplete file.")
                targetFile.delete()
                sendHttpResponse(output, 400, "Bad Request", "text/plain", "Incomplete transfer".toByteArray())
                return
            }

            onProgress(targetFile.name, totalRead, totalRead, 0L)
            Log.i(TAG, "File received successfully: ${targetFile.name} ($totalRead bytes) saved to ${targetFile.absolutePath}")
            onFileReceived(targetFile, targetFile.name, totalRead, mimeType)

            sendHttpResponse(output, 200, "OK", "application/json", """{"status":"ok","fileName":"${escapeJson(targetFile.name)}","bytes":$totalRead}""".toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving incoming upload", e)
            try { targetFile.delete() } catch (_: Exception) {}
            sendHttpResponse(output, 500, "Server Error", "text/plain", "Upload failed".toByteArray())
        } finally {
            try {
                outStream?.close()
            } catch (_: Exception) {}
        }
    }

    private fun getUniqueDestinationFile(destDir: File, originalName: String): File {
        var candidate = File(destDir, originalName)
        if (!candidate.exists()) return candidate

        val nameWithoutExt = originalName.substringBeforeLast(".")
        val ext = if (originalName.contains(".")) "." + originalName.substringAfterLast(".") else ""
        var counter = 1

        while (candidate.exists()) {
            candidate = File(destDir, "$nameWithoutExt ($counter)$ext")
            counter++
        }
        return candidate
    }

    private fun streamFileToClient(fileItem: ShareFileItem, output: OutputStream) {
        var inputStream: InputStream? = null
        try {
            inputStream = when {
                fileItem.uri != null -> context.contentResolver.openInputStream(fileItem.uri)
                fileItem.filePath != null -> FileInputStream(File(fileItem.filePath))
                else -> null
            }

            if (inputStream == null) {
                sendHttpResponse(output, 404, "Not Found", "text/plain", "Cannot open file".toByteArray())
                return
            }

            val fileSize = fileItem.size
            val headers = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: ${fileItem.mimeType}\r\n" +
                    "Content-Length: $fileSize\r\n" +
                    "Content-Disposition: attachment; filename=\"${fileItem.name}\"\r\n" +
                    "Connection: close\r\n\r\n"

            output.write(headers.toByteArray(Charsets.UTF_8))
            output.flush()

            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var totalSent = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalSent += bytesRead
            }
            output.flush()
            Log.d(TAG, "Finished streaming file: ${fileItem.name} ($totalSent bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Error streaming file ${fileItem.name}", e)
        } finally {
            try {
                inputStream?.close()
            } catch (_: Exception) {}
        }
    }

    private fun serveApiInfo(output: OutputStream) {
        val info = """{"name":"SHAREit Node","port":$actualPort,"filesCount":${sharedFiles.size}}"""
        sendHttpResponse(output, 200, "OK", "application/json", info.toByteArray(Charsets.UTF_8))
    }

    private fun serveWebPortal(output: OutputStream) {
        val filesHtml = if (sharedFiles.isEmpty()) {
            """<div class="empty">No files currently selected for sharing on this device.</div>"""
        } else {
            sharedFiles.joinToString("") { file ->
                """
                <div class="file-item">
                    <div class="file-info">
                        <span class="file-name">${escapeHtml(file.name)}</span>
                        <span class="file-meta">${file.formattedSize} • ${file.category.displayName}</span>
                    </div>
                    <a href="/api/download?id=${file.id}" class="btn-download" download="${escapeHtml(file.name)}">Download</a>
                </div>
                """.trimIndent()
            }
        }

        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>SHAREit Web Share</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #F5F7FB; color: #1E293B; margin: 0; padding: 16px; }
                    .container { max-width: 600px; margin: 0 auto; }
                    .header { text-align: center; padding: 20px 0; }
                    .logo { font-size: 26px; font-weight: 900; color: #1867FF; }
                    .card { background: white; border-radius: 16px; padding: 18px; margin-bottom: 16px; box-shadow: 0 4px 12px rgba(0,0,0,0.06); }
                    .file-item { display: flex; justify-content: space-between; align-items: center; padding: 10px; border-bottom: 1px solid #F1F5F9; }
                    .file-name { font-weight: 600; font-size: 14px; }
                    .btn-download { background: #1867FF; color: white; text-decoration: none; padding: 8px 16px; border-radius: 8px; font-weight: bold; font-size: 13px; }
                    .empty { text-align: center; color: #94A3B8; padding: 20px; font-size: 14px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <div class="logo">⚡ SHAREit Portal</div>
                        <p style="color:#64748B; font-size: 13px;">Direct Wi-Fi / Hotspot File Transfer</p>
                    </div>
                    <div class="card">
                        <h3>Files Ready to Download</h3>
                        $filesHtml
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        sendHttpResponse(output, 200, "OK", "text/html", html.toByteArray(Charsets.UTF_8))
    }

    private fun sendHttpResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: ByteArray
    ) {
        val response = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(Charsets.UTF_8))
        output.write(body)
        output.flush()
    }

    private fun extractQueryParam(queryString: String, param: String): String? {
        val pairs = queryString.split("&")
        for (pair in pairs) {
            val keyValue = pair.split("=")
            if (keyValue.size == 2 && keyValue[0] == param) {
                return try { URLDecoder.decode(keyValue[1], "UTF-8") } catch (_: Exception) { keyValue[1] }
            }
        }
        return null
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    }
}
