package com.example.network

import android.content.Context
import android.net.Uri
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
    private val onProgress: (fileName: String, bytesTransferred: Long, totalBytes: Long, speed: Long) -> Unit,
    private val onFileReceived: (file: File, originalName: String, size: Long, mimeType: String) -> Unit,
    private val onPeerConnected: (clientAddress: String) -> Unit
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
            socket.soTimeout = 30000
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
                <title>SwiftShare Web Portal</title>
                <style>
                    :root {
                        --bg: #0B0F19;
                        --card: #161F30;
                        --primary: #00D2FF;
                        --accent: #7928CA;
                        --text: #F3F4F6;
                        --muted: #9CA3AF;
                    }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        background: var(--bg);
                        color: var(--text);
                        margin: 0;
                        padding: 16px;
                    }
                    .container {
                        max-width: 640px;
                        margin: 0 auto;
                    }
                    .header {
                        text-align: center;
                        padding: 24px 0 16px;
                    }
                    .logo {
                        font-size: 28px;
                        font-weight: 800;
                        background: linear-gradient(135deg, var(--primary), var(--accent));
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                    }
                    .badge {
                        display: inline-block;
                        padding: 4px 12px;
                        border-radius: 999px;
                        background: rgba(0, 210, 255, 0.15);
                        color: var(--primary);
                        font-size: 13px;
                        margin-top: 6px;
                        font-weight: 600;
                    }
                    .card {
                        background: var(--card);
                        border-radius: 16px;
                        padding: 20px;
                        margin-bottom: 20px;
                        box-shadow: 0 8px 24px rgba(0,0,0,0.3);
                        border: 1px solid rgba(255,255,255,0.06);
                    }
                    h2 {
                        margin-top: 0;
                        font-size: 18px;
                        font-weight: 700;
                        color: #FFFFFF;
                        display: flex;
                        align-items: center;
                        gap: 8px;
                    }
                    .file-item {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        padding: 12px;
                        background: rgba(255,255,255,0.03);
                        border-radius: 10px;
                        margin-bottom: 8px;
                    }
                    .file-info {
                        display: flex;
                        flex-direction: column;
                        overflow: hidden;
                        padding-right: 12px;
                    }
                    .file-name {
                        font-weight: 600;
                        font-size: 15px;
                        white-space: nowrap;
                        overflow: hidden;
                        text-overflow: ellipsis;
                    }
                    .file-meta {
                        color: var(--muted);
                        font-size: 12px;
                        margin-top: 2px;
                    }
                    .btn-download {
                        background: linear-gradient(135deg, #00D2FF, #0082FB);
                        color: #000;
                        font-weight: 700;
                        text-decoration: none;
                        padding: 8px 16px;
                        border-radius: 8px;
                        font-size: 14px;
                        white-space: nowrap;
                    }
                    .upload-box {
                        border: 2px dashed rgba(0, 210, 255, 0.4);
                        border-radius: 12px;
                        padding: 24px;
                        text-align: center;
                        cursor: pointer;
                        background: rgba(0, 210, 255, 0.03);
                    }
                    .upload-box input {
                        display: none;
                    }
                    .upload-box label {
                        cursor: pointer;
                        font-weight: 600;
                        color: var(--primary);
                    }
                    .progress-bar {
                        height: 8px;
                        width: 100%;
                        background: rgba(255,255,255,0.1);
                        border-radius: 4px;
                        margin-top: 12px;
                        overflow: hidden;
                        display: none;
                    }
                    .progress-fill {
                        height: 100%;
                        width: 0%;
                        background: linear-gradient(90deg, var(--primary), var(--accent));
                        transition: width 0.2s;
                    }
                    .empty {
                        color: var(--muted);
                        text-align: center;
                        padding: 24px 0;
                        font-size: 14px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <div class="logo">⚡ SwiftShare</div>
                        <div class="badge">Direct Web Share Portal</div>
                    </div>

                    <div class="card">
                        <h2>📥 Files to Download (${sharedFiles.size})</h2>
                        $filesHtml
                    </div>

                    <div class="card">
                        <h2>📤 Upload Files to Android Device</h2>
                        <div class="upload-box" onclick="document.getElementById('fileInput').click()">
                            <input type="file" id="fileInput" multiple onchange="uploadSelectedFiles(this.files)">
                            <label>Click or Drop files here to send to device</label>
                            <p style="margin: 4px 0 0; font-size: 12px; color: var(--muted)">Direct peer-to-peer transmission</p>
                        </div>
                        <div class="progress-bar" id="progressBar">
                            <div class="progress-fill" id="progressFill"></div>
                        </div>
                        <div id="uploadStatus" style="font-size: 13px; color: var(--muted); margin-top: 8px; text-align: center;"></div>
                    </div>
                </div>

                <script>
                    function uploadSelectedFiles(files) {
                        if (!files || files.length === 0) return;
                        var pBar = document.getElementById('progressBar');
                        var pFill = document.getElementById('progressFill');
                        var status = document.getElementById('uploadStatus');
                        pBar.style.display = 'block';

                        var file = files[0];
                        status.textContent = 'Uploading ' + file.name + ' (' + (file.size / (1024*1024)).toFixed(1) + ' MB)...';

                        var xhr = new XMLHttpRequest();
                        xhr.open('POST', '/api/upload?filename=' + encodeURIComponent(file.name), true);
                        xhr.setRequestHeader('x-filename', encodeURIComponent(file.name));
                        xhr.setRequestHeader('content-type', file.type || 'application/octet-stream');

                        xhr.upload.onprogress = function(e) {
                            if (e.lengthComputable) {
                                var percent = Math.round((e.loaded / e.total) * 100);
                                pFill.style.width = percent + '%';
                                status.textContent = 'Uploading: ' + percent + '%';
                            }
                        };

                        xhr.onload = function() {
                            if (xhr.status === 200) {
                                status.textContent = '✅ Transfer complete! Sent to SwiftShare.';
                                pFill.style.width = '100%';
                            } else {
                                status.textContent = '❌ Upload failed with status ' + xhr.status;
                            }
                        };
                        xhr.onerror = function() {
                            status.textContent = '❌ Upload failed (network error)';
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
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{")
        jsonBuilder.append("\"appName\":\"SwiftShare\",")
        jsonBuilder.append("\"files\":[")
        sharedFiles.forEachIndexed { index, item ->
            jsonBuilder.append("{")
            jsonBuilder.append("\"id\":\"${item.id}\",")
            jsonBuilder.append("\"name\":\"${escapeJson(item.name)}\",")
            jsonBuilder.append("\"size\":${item.size},")
            jsonBuilder.append("\"mimeType\":\"${escapeJson(item.mimeType)}\",")
            jsonBuilder.append("\"category\":\"${item.category.name}\"")
            jsonBuilder.append("}")
            if (index < sharedFiles.size - 1) jsonBuilder.append(",")
        }
        jsonBuilder.append("]}")

        sendHttpResponse(output, 200, "OK", "application/json", jsonBuilder.toString().toByteArray(Charsets.UTF_8))
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
                sendHttpResponse(output, 404, "Not Found", "text/plain", "Cannot open stream".toByteArray())
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
                if (delta >= 400) {
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
        val destDir = File(context.filesDir, "received").apply { mkdirs() }
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
                if (delta >= 400) {
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
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
