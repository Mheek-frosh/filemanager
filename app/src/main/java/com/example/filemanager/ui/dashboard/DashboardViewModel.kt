package com.example.filemanager.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.filemanager.R
import com.example.filemanager.data.FileRepository
import com.example.filemanager.data.StorageVolumeProvider
import com.example.filemanager.model.CategoryItem
import com.example.filemanager.model.FileItem
import com.example.filemanager.model.StorageCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val storageVolumeProvider: StorageVolumeProvider
) : ViewModel() {
    private val _recentFiles = MutableLiveData<List<FileItem>>()
    val recentFiles: LiveData<List<FileItem>> = _recentFiles

    private val _storageCards = MutableLiveData<List<StorageCard>>()
    val storageCards: LiveData<List<StorageCard>> = _storageCards

    val categories = listOf(
        CategoryItem("pictures", "Pictures", R.drawable.ic_image),
        CategoryItem("videos", "Videos", R.drawable.ic_play_circle),
        CategoryItem("music", "Music", R.drawable.ic_music_note),
        CategoryItem("apps", "Apps", R.drawable.ic_apps),
        CategoryItem("documents", "Document", R.drawable.ic_description),
        CategoryItem("zip", "Zip Files", R.drawable.ic_folder_zip),
        CategoryItem("downloads", "Download", R.drawable.ic_download),
        CategoryItem("all", "Add", R.drawable.ic_add)
    )

    init {
        _storageCards.value = storageVolumeProvider.getStorageCards()
    }

    fun refreshStorage() {
        _storageCards.value = storageVolumeProvider.getStorageCards()
    }

    fun loadRecentFiles() {
        val files = fileRepository.getRecentFiles(30)
        _recentFiles.value = if (files.isNotEmpty()) {
            files
        } else {
            listOf(
                FileItem(1, "Project_Alpha_Brief.pdf", "PDF", 2516582, 0, null, null, R.drawable.ic_description, null, false),
                FileItem(2, "IMG_8472.jpg", "JPG", 3250585, 0, null, null, R.drawable.ic_image, null, false),
                FileItem(3, "UI_Design_Assets.zip", "ZIP", 15518924, 0, null, null, R.drawable.ic_folder_zip, null, false)
            )
        }
    }
}
