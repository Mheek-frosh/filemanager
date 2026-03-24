package com.example.filemanager.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.MediaStore
import com.example.filemanager.R
import com.example.filemanager.model.FileItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URLConnection
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getRecentFiles(limit: Int = 20): List<FileItem> {
        val files = mutableListOf<FileItem>()
        files += queryImages(limit)
        files += queryVideos(limit)
        files += queryDownloads(limit)
        return files.sortedByDescending { it.dateAddedSeconds }.take(limit)
    }

    fun getFilesForCategory(categoryId: String): List<FileItem> {
        return when (categoryId) {
            "pictures" -> queryImages(500)
            "videos" -> queryVideos(500)
            "music" -> queryAudio(500)
            "documents" -> queryDocuments(500)
            "zip" -> queryZipFiles(500)
            "downloads" -> queryDownloadsFolder(500)
            "apps" -> getInstalledApps()
            "all" -> listFilesInDirectory(FileRepositoryHelper.primaryExternalPath())
            else -> getRecentFiles(200)
        }.sortedWith(
            compareByDescending<FileItem> { it.dateAddedSeconds }
                .thenBy { it.name.lowercase() }
        )
    }

    fun listFilesInDirectory(path: String): List<FileItem> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val files = dir.listFiles() ?: return emptyList()
        return files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).map { file ->
            fileToItem(file)
        }
    }

    private fun fileToItem(file: File): FileItem {
        val isDir = file.isDirectory
        val mime = if (file.isFile) {
            URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
        } else {
            null
        }
        val ext = file.extension.uppercase()
        val icon = when {
            isDir -> R.drawable.ic_folder
            mime?.startsWith("image/") == true -> R.drawable.ic_image
            mime?.startsWith("video/") == true -> R.drawable.ic_play_circle
            mime?.startsWith("audio/") == true -> R.drawable.ic_music_note
            file.name.endsWith(".zip", true) -> R.drawable.ic_folder_zip
            mime?.contains("pdf") == true -> R.drawable.ic_description
            else -> R.drawable.ic_description
        }
        val uri = Uri.fromFile(file)
        return FileItem(
            id = file.absolutePath.hashCode().toLong(),
            name = file.name,
            type = if (isDir) "Folder" else ext.ifEmpty { "File" },
            sizeBytes = if (file.isFile) file.length() else 0L,
            dateAddedSeconds = file.lastModified() / 1000L,
            contentUri = uri,
            mimeType = mime,
            iconRes = icon,
            localPath = file.absolutePath,
            isDirectory = isDir
        )
    }

    private fun queryImages(limit: Int): List<FileItem> {
        return query(
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            typeFallback = "JPG",
            iconRes = R.drawable.ic_image,
            limit = limit
        )
    }

    private fun queryVideos(limit: Int): List<FileItem> {
        return query(
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            typeFallback = "MP4",
            iconRes = R.drawable.ic_play_circle,
            limit = limit
        )
    }

    private fun queryAudio(limit: Int): List<FileItem> {
        return query(
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            typeFallback = "AUDIO",
            iconRes = R.drawable.ic_music_note,
            limit = limit
        )
    }

    private fun queryDocuments(limit: Int): List<FileItem> {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        val mimeFilter =
            "${MediaStore.MediaColumns.MIME_TYPE} LIKE ? OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE ? OR ${MediaStore.MediaColumns.MIME_TYPE} = ? OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE ?"
        val mimeArgs = arrayOf(
            "application/pdf",
            "text/%",
            "application/msword",
            "application/vnd.openxmlformats%"
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        val result = mutableListOf<FileItem>()
        context.contentResolver.query(collection, projection, mimeFilter, mimeArgs, sortOrder)?.use { cursor ->
            fillFromCursor(cursor, collection, R.drawable.ic_description, result, limit, "DOC")
        }
        return result
    }

    private fun queryZipFiles(limit: Int): List<FileItem> {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.MediaColumns.MIME_TYPE} = ?"
        val args = arrayOf("%.zip", "application/zip")
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        val result = mutableListOf<FileItem>()
        context.contentResolver.query(collection, projection, selection, args, sortOrder)?.use { cursor ->
            fillFromCursor(cursor, collection, R.drawable.ic_folder_zip, result, limit, "ZIP")
        }
        return result
    }

    private fun queryDownloads(limit: Int): List<FileItem> {
        return query(
            collection = MediaStore.Files.getContentUri("external"),
            typeFallback = "FILE",
            iconRes = R.drawable.ic_description,
            limit = limit
        )
    }

    private fun queryDownloadsFolder(limit: Int): List<FileItem> {
        val downloads = FileRepositoryHelper.downloadsPath()
        if (downloads != null) {
            return listFilesInDirectory(downloads).take(limit)
        }
        return queryDownloads(limit)
    }

    private fun query(
        collection: Uri,
        typeFallback: String,
        iconRes: Int,
        limit: Int
    ): List<FileItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        val result = mutableListOf<FileItem>()
        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            fillFromCursor(cursor, collection, iconRes, result, limit, typeFallback)
        }
        return result
    }

    private fun fillFromCursor(
        cursor: android.database.Cursor,
        collection: Uri,
        iconRes: Int,
        out: MutableList<FileItem>,
        limit: Int,
        typeFallback: String
    ) {
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        while (cursor.moveToNext() && out.size < limit) {
            val id = cursor.getLong(idIndex)
            val name = cursor.getString(nameIndex) ?: "Unknown"
            val mimeType = cursor.getString(mimeIndex)
            val size = cursor.getLong(sizeIndex)
            val dateAdded = cursor.getLong(dateIndex)
            val itemUri = Uri.withAppendedPath(collection, id.toString())
            val type = mimeType?.substringAfterLast("/")?.uppercase() ?: typeFallback
            val mappedIcon = when {
                name.endsWith(".zip", true) -> R.drawable.ic_folder_zip
                type.contains("PDF", true) -> R.drawable.ic_description
                mimeType?.startsWith("image/") == true -> R.drawable.ic_image
                mimeType?.startsWith("video/") == true -> R.drawable.ic_play_circle
                mimeType?.startsWith("audio/") == true -> R.drawable.ic_music_note
                else -> iconRes
            }
            out += FileItem(
                id = id,
                name = name,
                type = type,
                sizeBytes = size,
                dateAddedSeconds = dateAdded,
                contentUri = itemUri,
                mimeType = mimeType,
                iconRes = mappedIcon,
                localPath = null,
                isDirectory = false
            )
        }
    }

    private fun getInstalledApps(): List<FileItem> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
        return apps
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { app ->
                val label = pm.getApplicationLabel(app).toString()
                FileItem(
                    id = app.uid.toLong() + app.packageName.hashCode(),
                    name = label,
                    type = "APP",
                    sizeBytes = 0L,
                    dateAddedSeconds = 0L,
                    contentUri = Uri.parse("package:${app.packageName}"),
                    mimeType = null,
                    iconRes = R.drawable.ic_apps,
                    localPath = app.packageName,
                    isDirectory = false
                )
            }
            .sortedBy { it.name.lowercase() }
    }
}

private object FileRepositoryHelper {
    fun primaryExternalPath(): String {
        return android.os.Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
    }

    fun downloadsPath(): String? {
        val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        return dir?.absolutePath
    }
}
