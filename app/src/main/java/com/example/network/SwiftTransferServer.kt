package com.example.network

import android.content.Context
import android.os.Environment
import com.example.model.ShareFileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

class SwiftTransferServer(
    private val context: Context,
    private val port: Int = 8888,
    private val onTransferStarted: (fileName: String, totalBytes: Long) -> Unit = { _, _ -> },
    private val onProgress: (fileName: String, bytesTransferred: Long, totalBytes: Long, speed: Long) -> Unit,
    private val onFileReceived: (file: File, originalName: String, size: Long, mimeType: String) -> Unit,
    private val onPeerConnected: (clientAddress: String) -> Unit,
    private val onRequestTransferFromPeer: ((peerIp: String, peerPort: Int) -> Unit)? = null
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var sharedFiles: List<ShareFileItem> = emptyList()

    @Volatile
    var isRunning = false
        private set

    var actualPort = port
        private set

    fun updateSharedFiles(files: List<ShareFileItem>) {
        this.sharedFiles = files
    }

    fun start(): Boolean {
        if (isRunning) return true
        try {
            var selectedPort = port
            var createdSocket: ServerSocket? = null
            for (offset in 0..10) {
                try {
                    createdSocket = ServerSocket(selectedPort + offset)
                    actualPort = selectedPort + offset
                    break
                } catch (_: Exception) {}
            }

            if (createdSocket == null) {
                createdSocket = ServerSocket(0)
                actualPort = createdSocket.localPort
            }

            serverSocket = createdSocket
            isRunning = true

            serverJob = scope.launch {
                while (isRunning && !serverSocket!!.isClosed) {
                    try {
                        val client = serverSocket!!.accept()
                        launch {
                            handleClient(client)
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            isRunning = false
            return false
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        val clientIp = socket.inetAddress?.hostAddress ?: "Unknown"
        onPeerConnected(clientIp)

        try {
            socket.soTimeout = 45000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())
            val reader = BufferedReader(InputStreamReader(input))

            val requestLine = reader.readLine() ?: run {
                socket.close()
                return
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return
            }

            val method = parts[0]
            val fullPath = parts[1]
            val path = if (fullPath.contains("?")) fullPath.substringBefore("?") else fullPath
            val queryString = if (fullPath.contains("?")) fullPath.substringAfter("?") else ""

            val headers = HashMap<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                val headerParts = line!!.split(":", limit = 2)
                if (headerParts.size == 2) {
                    headers[headerParts[0].trim().lowercase()] = headerParts[1].trim()
                }
            }

            when {
                method == "GET" && path == "/" -> {
                    serveWebPortal(output)
                }
                method == "GET" && path == "/api/info" -> {
                    serveApiInfo(output)
                }
                method == "GET" && path == "/api/files" -> {
                    val jsonArray = sharedFiles.joinToString(separator = ",", prefix = "[", postfix = "]") { file ->
                        """{"id":"${file.id}","name":"${escapeHtml(file.name)}","size":${file.size},"mime":"${file.mimeType}"}"""
                    }
                    sendHttpResponse(output, 200, "OK", "application/json", jsonArray.toByteArray(Charsets.UTF_8))
                }
                method == "POST" && path.startsWith("/api/request_files") -> {
                    val targetPort = extractQueryParam(queryString, "port")?.toIntOrNull() ?: 8888
                    onRequestTransferFromPeer?.invoke(clientIp, targetPort)
                    sendHttpResponse(output, 200, "OK", "application/json", """{"status":"transfer_started"}""".toByteArray())
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
                        URLDecoder.decode(rawFilename, "UTF-8")
                    } else {
                        "received_${System.currentTimeMillis()}.bin"
                    }
                    val mimeType = headers["content-type"] ?: "application/octet-stream"

                    onTransferStarted(fileName, contentLength)
                    handleIncomingUpload(input, contentLength, fileName, mimeType, output)
                }
                else -> {
                    sendHttpResponse(output, 404, "Not Found", "text/plain", "Not Found".toByteArray())
                }
            }
            output.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
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
                    .upload-box { border: 2px dashed #1867FF; border-radius: 12px; padding: 24px; text-align: center; cursor: pointer; background: #F0F6FF; }
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
                    <div class="card">
                        <h3>Send Files to Device</h3>
                        <div class="upload-box" onclick="document.getElementById('f').click()">
                            <input type="file" id="f" multiple onchange="upload(this.files)" style="display:none">
                            <span style="color:#1867FF; font-weight:bold;">Click here to select files to send</span>
                        </div>
                        <div id="st" style="margin-top:10px; font-size:13px; text-align:center;"></div>
                    </div>
                </div>
                <script>
                    function upload(files) {
                        if (!files || !files.length) return;
                        var file = files[0];
                        document.getElementById('st').textContent = 'Uploading ' + file.name + '...';
                        var xhr = new XMLHttpRequest();
                        xhr.open('POST', '/api/upload?filename=' + encodeURIComponent(file.name), true);
                        xhr.setRequestHeader('x-filename', encodeURIComponent(file.name));
                        xhr.onload = function() {
                            document.getElementById('st').textContent = xhr.status === 200 ? '✅ Successfully transferred to device!' : '❌ Failed';
                        };
                        xhr.send(file);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        sendHttpResponse(output, 200, "OK", "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
    }

    private fun serveApiInfo(output: OutputStream) {
        val json = """{"appName":"SHAREit","device":"Android","filesCount":${sharedFiles.size}}"""
        sendHttpResponse(output, 200, "OK", "application/json", json.toByteArray(Charsets.UTF_8))
    }

    private fun streamFileToClient(fileItem: ShareFileItem, output: OutputStream) {
        var inputStream: InputStream? = null
        try {
            inputStream = if (fileItem.uri != null) {
                context.contentResolver.openInputStream(fileItem.uri)
            } else if (fileItem.filePath != null) {
                File(fileItem.filePath).inputStream()
            } else {
                null
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
            var lastTime = System.currentTimeMillis()
            var bytesSinceLastSample = 0L
            var speed = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalSent += bytesRead
                bytesSinceLastSample += bytesRead

                val now = System.currentTimeMillis()
                val delta = now - lastTime
                if (delta >= 300) {
                    speed = (bytesSinceLastSample * 1000) / delta
                    lastTime = now
                    bytesSinceLastSample = 0L
                    onProgress(fileItem.name, totalSent, fileSize, speed)
                }
            }
            output.flush()
            onProgress(fileItem.name, totalSent, fileSize, 0L)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                inputStream?.close()
            } catch (_: Exception) {}
        }
    }

    private fun handleIncomingUpload(
        input: InputStream,
        contentLength: Long,
        fileName: String,
        mimeType: String,
        output: OutputStream
    ) {
        val destDir = com.example.data.FileManagerRepository.getReceivedFilesDir(context)
        val targetFile = File(destDir, fileName)

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
                if (delta >= 300) {
                    speed = (bytesSinceLastSample * 1000) / delta
                    lastTime = now
                    bytesSinceLastSample = 0L
                    onProgress(fileName, totalRead, if (contentLength > 0) contentLength else totalRead, speed)
                }
            }
            outStream.flush()
            onProgress(fileName, totalRead, totalRead, 0L)

            onFileReceived(targetFile, fileName, totalRead, mimeType)

            sendHttpResponse(output, 200, "OK", "application/json", "{\"status\":\"ok\"}".toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
            sendHttpResponse(output, 500, "Server Error", "text/plain", "Upload failed".toByteArray())
        } finally {
            try {
                outStream?.close()
            } catch (_: Exception) {}
        }
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
                return URLDecoder.decode(keyValue[1], "UTF-8")
            }
        }
        return null
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
