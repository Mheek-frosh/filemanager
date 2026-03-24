package com.example.filemanager.ui.files

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.filemanager.data.FileRepository
import com.example.filemanager.model.FileItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Backing data for FileListFragment: category listing from FileRepository, or directory listing
 * when `storageRootPath` is non-empty (browse / SD).
 */
@HiltViewModel
class FilesViewModel @Inject constructor(
    private val repository: FileRepository
) : ViewModel() {
    private val _files = MutableLiveData<List<FileItem>>()
    val files: LiveData<List<FileItem>> = _files

    fun load(categoryId: String, storageRootPath: String) {
        _files.value = when {
            storageRootPath.isNotBlank() -> repository.listFilesInDirectory(storageRootPath)
            else -> repository.getFilesForCategory(categoryId)
        }
    }
}
