package com.example.filemanager.model

data class StorageCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val progress: Int,
    val rootPath: String? = null,
    val available: Boolean = true
)
