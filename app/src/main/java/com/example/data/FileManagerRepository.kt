package com.example.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.model.FileCategory
import com.example.model.ShareFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class FileManagerRepository(private val context: Context) {

    companion object {
        fun getReceivedFilesDir(context: Context): File {
            val dir = File(context.filesDir, "received")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
    }

    suspend fun loadInstalledApps(): List<ShareFileItem> = withContext(Dispatchers.IO) {
        val appList = mutableListOf<ShareFileItem>()
        try {
            val pm = context.packageManager
            val flags = PackageManager.GET_META_DATA
            val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(flags)
            }

            for (app in installed) {
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null || !isSystem) {
                    val appName = try {
                        pm.getApplicationLabel(app).toString()
                    } catch (_: Exception) {
                        app.packageName
                    }
                    val apkFile = File(app.sourceDir)
                    val size = if (apkFile.exists()) apkFile.length() else 12 * 1024 * 1024L

                    appList.add(
                        ShareFileItem(
                            id = "app_${app.packageName}",
                            name = "$appName.apk",
                            size = size,
                            filePath = app.sourceDir,
                            mimeType = "application/vnd.android.package-archive",
                            category = FileCategory.APPS,
                            packageName = app.packageName,
                            isApp = true,
                            dateModified = apkFile.lastModified()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If appList is still small (e.g. fresh emulator restrictions), add popular apps
        if (appList.isEmpty()) {
            appList.addAll(generateSampleApps())
        }

        appList.sortByDescending { it.size }
        return@withContext appList
    }

    suspend fun loadMediaFiles(category: FileCategory): List<ShareFileItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<ShareFileItem>()
        try {
            val resolver = context.contentResolver
            val (uri, projection) = when (category) {
                FileCategory.PHOTOS -> Pair(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.SIZE,
                        MediaStore.Images.Media.DATE_MODIFIED,
                        MediaStore.Images.Media.MIME_TYPE
                    )
                )
                FileCategory.VIDEOS -> Pair(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Video.Media._ID,
                        MediaStore.Video.Media.DISPLAY_NAME,
                        MediaStore.Video.Media.SIZE,
                        MediaStore.Video.Media.DATE_MODIFIED,
                        MediaStore.Video.Media.MIME_TYPE
                    )
                )
                FileCategory.MUSIC -> Pair(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.SIZE,
                        MediaStore.Audio.Media.DATE_MODIFIED,
                        MediaStore.Audio.Media.MIME_TYPE
                    )
                )
                else -> Pair(
                    MediaStore.Files.getContentUri("external"),
                    arrayOf(
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.DISPLAY_NAME,
                        MediaStore.Files.FileColumns.SIZE,
                        MediaStore.Files.FileColumns.DATE_MODIFIED,
                        MediaStore.Files.FileColumns.MIME_TYPE
                    )
                )
            }

            val selection = if (category == FileCategory.DOCS) {
                "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/%' OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'text/%' OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pdf' OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.doc%' OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.txt' OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.xls%'"
            } else null

            val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            resolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "file_$id"
                    val size = cursor.getLong(sizeCol)
                    val date = cursor.getLong(dateCol) * 1000
                    val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                    val itemUri = ContentUris.withAppendedId(uri, id)

                    if (size > 0) {
                        items.add(
                            ShareFileItem(
                                id = "media_${category.name}_$id",
                                name = name,
                                size = size,
                                uri = itemUri,
                                mimeType = mime,
                                category = category,
                                dateModified = date
                            )
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            // Handled when permissions are pending
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If device has no files in this category yet (e.g. brand new emulator storage), generate realistic sample files
        if (items.isEmpty()) {
            items.addAll(ensureSampleFilesForCategory(category))
        }

        return@withContext items
    }

    private fun ensureSampleFilesForCategory(category: FileCategory): List<ShareFileItem> {
        val sampleDir = File(context.filesDir, "sample_${category.name.lowercase()}").apply { mkdirs() }
        val results = mutableListOf<ShareFileItem>()

        when (category) {
            FileCategory.PHOTOS -> {
                val sampleImages = listOf(
                    "Sunset_Beach.jpg" to AndroidColor.rgb(255, 120, 80),
                    "Mountain_Trail.jpg" to AndroidColor.rgb(60, 160, 100),
                    "Urban_Architecture.jpg" to AndroidColor.rgb(80, 140, 240)
                )
                for ((fileName, color) in sampleImages) {
                    val file = File(sampleDir, fileName)
                    if (!file.exists()) {
                        createSampleImage(file, color, fileName)
                    }
                    results.add(
                        ShareFileItem(
                            id = "sample_photo_${file.name}",
                            name = file.name,
                            size = file.length(),
                            filePath = file.absolutePath,
                            mimeType = "image/jpeg",
                            category = FileCategory.PHOTOS,
                            dateModified = file.lastModified()
                        )
                    )
                }
            }
            FileCategory.DOCS -> {
                val sampleDocs = listOf(
                    "Project_Specification.pdf" to "SHAREit File Transfer Specification - Version 2.0\nHigh-speed Wi-Fi Direct and Hotspot beam sharing protocol documentation.",
                    "Meeting_Notes.txt" to "Team Sync:\n- Hotspot P2P connection tested\n- File beam speed verified\n- Media categories operational.",
                    "Annual_Budget_Report.docx" to "Financial and operational report for mobile sharing deployment."
                )
                for ((fileName, content) in sampleDocs) {
                    val file = File(sampleDir, fileName)
                    if (!file.exists()) {
                        file.writeText(content)
                    }
                    val mime = if (fileName.endsWith(".pdf")) "application/pdf" else "text/plain"
                    results.add(
                        ShareFileItem(
                            id = "sample_doc_${file.name}",
                            name = file.name,
                            size = file.length(),
                            filePath = file.absolutePath,
                            mimeType = mime,
                            category = FileCategory.DOCS,
                            dateModified = file.lastModified()
                        )
                    )
                }
            }
            FileCategory.MUSIC -> {
                val sampleAudios = listOf("Inspiring_Acoustic.mp3", "Future_Bass_Beat.mp3")
                for (name in sampleAudios) {
                    val file = File(sampleDir, name)
                    if (!file.exists()) {
                        // Create dummy audio header bytes
                        file.writeBytes(ByteArray(1024 * 512) { (it % 256).toByte() })
                    }
                    results.add(
                        ShareFileItem(
                            id = "sample_audio_${file.name}",
                            name = file.name,
                            size = file.length(),
                            filePath = file.absolutePath,
                            mimeType = "audio/mpeg",
                            category = FileCategory.MUSIC,
                            dateModified = file.lastModified()
                        )
                    )
                }
            }
            FileCategory.VIDEOS -> {
                val sampleVideos = listOf("Travel_Clip_4K.mp4", "Drone_Footage_City.mp4")
                for (name in sampleVideos) {
                    val file = File(sampleDir, name)
                    if (!file.exists()) {
                        file.writeBytes(ByteArray(1024 * 1024) { (it % 256).toByte() })
                    }
                    results.add(
                        ShareFileItem(
                            id = "sample_video_${file.name}",
                            name = file.name,
                            size = file.length(),
                            filePath = file.absolutePath,
                            mimeType = "video/mp4",
                            category = FileCategory.VIDEOS,
                            dateModified = file.lastModified()
                        )
                    )
                }
            }
            else -> {}
        }
        return results
    }

    private fun createSampleImage(file: File, bgColor: Int, label: String) {
        try {
            val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(bgColor)
            val paint = Paint().apply {
                color = AndroidColor.WHITE
                textSize = 28f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText(label.substringBefore("."), 200f, 150f, paint)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } catch (_: Exception) {
            file.writeBytes(ByteArray(1024 * 64) { 0 })
        }
    }

    private fun generateSampleApps(): List<ShareFileItem> {
        return listOf(
            ShareFileItem("app_chrome", "Google Chrome.apk", 34 * 1024 * 1024L, null, null, "application/vnd.android.package-archive", FileCategory.APPS, isApp = true),
            ShareFileItem("app_camera", "Camera Pro.apk", 18 * 1024 * 1024L, null, null, "application/vnd.android.package-archive", FileCategory.APPS, isApp = true),
            ShareFileItem("app_maps", "Maps Navigation.apk", 42 * 1024 * 1024L, null, null, "application/vnd.android.package-archive", FileCategory.APPS, isApp = true),
            ShareFileItem("app_calculator", "Calculator.apk", 4 * 1024 * 1024L, null, null, "application/vnd.android.package-archive", FileCategory.APPS, isApp = true)
        )
    }

    suspend fun resolvePickedUri(uri: Uri): ShareFileItem? = withContext(Dispatchers.IO) {
        try {
            var fileName = "selected_file"
            var fileSize = 0L
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: fileName
                    if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                }
            }

            if (fileSize <= 0) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        fileSize = stream.available().toLong()
                    }
                } catch (_: Exception) {}
            }

            val category = when {
                mimeType.startsWith("image/") -> FileCategory.PHOTOS
                mimeType.startsWith("video/") -> FileCategory.VIDEOS
                mimeType.startsWith("audio/") -> FileCategory.MUSIC
                mimeType.contains("pdf") || mimeType.contains("document") || mimeType.contains("text") -> FileCategory.DOCS
                mimeType.contains("android.package-archive") -> FileCategory.APPS
                else -> FileCategory.FILES
            }

            return@withContext ShareFileItem(
                id = "picked_${System.currentTimeMillis()}_${fileName.hashCode()}",
                name = fileName,
                size = if (fileSize > 0) fileSize else 1024 * 512,
                uri = uri,
                mimeType = mimeType,
                category = category,
                dateModified = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun loadReceivedFiles(): List<ShareFileItem> = withContext(Dispatchers.IO) {
        val destDir = getReceivedFilesDir(context)
        val files = destDir.listFiles() ?: return@withContext emptyList()
        return@withContext files.filter { it.isFile && it.length() > 0 }.map { file ->
            val ext = file.extension.lowercase()
            val category = when (ext) {
                "jpg", "jpeg", "png", "webp", "gif" -> FileCategory.PHOTOS
                "mp4", "mkv", "mov", "avi" -> FileCategory.VIDEOS
                "mp3", "wav", "m4a", "flac" -> FileCategory.MUSIC
                "pdf", "doc", "docx", "txt", "xlsx" -> FileCategory.DOCS
                "apk" -> FileCategory.APPS
                else -> FileCategory.FILES
            }

            ShareFileItem(
                id = "received_${file.name.hashCode()}",
                name = file.name,
                size = file.length(),
                filePath = file.absolutePath,
                category = category,
                dateModified = file.lastModified()
            )
        }.sortedByDescending { it.dateModified }
    }

    fun openFile(item: ShareFileItem): Boolean {
        try {
            val file = item.filePath?.let { File(it) }
            val uri = if (file != null && file.exists()) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                item.uri
            } ?: return false

            val mime = if (item.isApp || item.name.endsWith(".apk")) {
                "application/vnd.android.package-archive"
            } else {
                item.mimeType
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun shareFileWithApps(item: ShareFileItem): Boolean {
        try {
            val file = item.filePath?.let { File(it) }
            val uri = if (file != null && file.exists()) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                item.uri
            } ?: return false

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun deleteReceivedFile(item: ShareFileItem): Boolean {
        return try {
            item.filePath?.let { File(it).delete() } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun deleteAllReceivedFiles(): Boolean {
        return try {
            val destDir = getReceivedFilesDir(context)
            destDir.listFiles()?.forEach { it.delete() }
            true
        } catch (_: Exception) {
            false
        }
    }
}
