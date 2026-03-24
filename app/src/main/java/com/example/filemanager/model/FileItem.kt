package com.example.filemanager.model

import android.net.Uri

/**
 * Unified row model: gallery items use contentUri (MediaStore); browsed folders use localPath with a file:// contentUri.
 */
data class FileItem(
    val id: Long,
    val name: String,
    val type: String,
    val sizeBytes: Long,
    val dateAddedSeconds: Long,
    val contentUri: Uri?,
    val mimeType: String?,
    val iconRes: Int,
    val localPath: String? = null,
    val isDirectory: Boolean = false
)
