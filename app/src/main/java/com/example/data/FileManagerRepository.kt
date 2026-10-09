package com.example.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.model.FileCategory
import com.example.model.ShareFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileManagerRepository(private val context: Context) {

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
                    val appName = pm.getApplicationLabel(app).toString()
                    val apkFile = File(app.sourceDir)
                    val size = if (apkFile.exists()) apkFile.length() else 10 * 1024 * 1024L

                    appList.add(
                        ShareFileItem(
                            id = "app_${app.packageName}",
                            name = "$appName.apk",
                            size = size,
                            filePath = app.sourceDir,
                            mimeType = "application/vnd.android.package-archive",
                            category = FileCategory.APPS,
                            packageName = app.packageName,
                            isApp = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        appList.sortByDescending { it.size }
        return@withContext appList
    }

    suspend fun loadMediaFiles(category: FileCategory): List<ShareFileItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<ShareFileItem>()
        try {
            val resolver = context.contentResolver
            val (uri, projection, mimePrefix) = when (category) {
                FileCategory.PHOTOS -> Triple(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.SIZE,
                        MediaStore.Images.Media.DATE_MODIFIED,
                        MediaStore.Images.Media.MIME_TYPE
                    ),
                    "image/"
                )
                FileCategory.VIDEOS -> Triple(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Video.Media._ID,
                        MediaStore.Video.Media.DISPLAY_NAME,
                        MediaStore.Video.Media.SIZE,
                        MediaStore.Video.Media.DATE_MODIFIED,
                        MediaStore.Video.Media.MIME_TYPE
                    ),
                    "video/"
                )
                FileCategory.MUSIC -> Triple(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.SIZE,
                        MediaStore.Audio.Media.DATE_MODIFIED,
                        MediaStore.Audio.Media.MIME_TYPE
                    ),
                    "audio/"
                )
                else -> Triple(
                    MediaStore.Files.getContentUri("external"),
                    arrayOf(
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.DISPLAY_NAME,
                        MediaStore.Files.FileColumns.SIZE,
                        MediaStore.Files.FileColumns.DATE_MODIFIED,
                        MediaStore.Files.FileColumns.MIME_TYPE
                    ),
                    ""
                )
            }

            val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            resolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
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
            // Permission not granted or restricted
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext items
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
                category = category
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun loadReceivedFiles(): List<ShareFileItem> = withContext(Dispatchers.IO) {
        val destDir = File(context.filesDir, "received")
        if (!destDir.exists()) return@withContext emptyList()

        val files = destDir.listFiles() ?: return@withContext emptyList()
        return@withContext files.filter { it.isFile }.map { file ->
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

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
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
            val destDir = File(context.filesDir, "received")
            if (destDir.exists()) {
                destDir.listFiles()?.forEach { it.delete() }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
