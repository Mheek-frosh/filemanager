package com.example.filemanager.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import com.example.filemanager.R
import com.example.filemanager.model.StorageCard
import com.example.filemanager.utils.FileFormatUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Builds internal + SD StorageCards using StatFs and StorageManager for removable volumes. */
@Singleton
class StorageVolumeProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getStorageCards(): List<StorageCard> {
        val cards = mutableListOf<StorageCard>()
        val primary = Environment.getExternalStorageDirectory()
        if (primary != null) {
            cards += buildCardForPath(
                id = ID_PRIMARY,
                title = "Internal Storage",
                iconRes = R.drawable.ic_phone_android,
                root = primary
            )
        }

        val removable = findRemovableVolumeRoot()
        if (removable != null) {
            cards += buildCardForPath(
                id = ID_SD,
                title = "Micro SD",
                iconRes = R.drawable.ic_sd_card,
                root = removable
            )
        } else {
            cards += StorageCard(
                id = ID_SD,
                title = "Micro SD",
                subtitle = "No SD card",
                iconRes = R.drawable.ic_sd_card,
                progress = 0,
                rootPath = null,
                available = false
            )
        }
        return cards
    }

    private fun buildCardForPath(
        id: String,
        title: String,
        iconRes: Int,
        root: java.io.File
    ): StorageCard {
        return try {
            val stat = StatFs(root.absolutePath)
            val total = stat.totalBytes
            val free = stat.availableBytes
            val used = (total - free).coerceAtLeast(0L)
            val progress = if (total > 0) ((used * 100) / total).toInt().coerceIn(0, 100) else 0
            StorageCard(
                id = id,
                title = title,
                subtitle = "${FileFormatUtils.sizeToDisplay(used)} / ${FileFormatUtils.sizeToDisplay(total)}",
                iconRes = iconRes,
                progress = progress,
                rootPath = root.absolutePath,
                available = true
            )
        } catch (_: Exception) {
            StorageCard(
                id = id,
                title = title,
                subtitle = "Unavailable",
                iconRes = iconRes,
                progress = 0,
                rootPath = root.absolutePath,
                available = false
            )
        }
    }

    private fun findRemovableVolumeRoot(): java.io.File? {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val primaryPath = Environment.getExternalStorageDirectory()?.absolutePath
        for (volume in sm.storageVolumes) {
            if (!volume.isVolumeMountedCompat()) continue
            val dir = volume.directory ?: continue
            if (dir.absolutePath == primaryPath) continue
            if (volume.isRemovable) return dir
        }
        for (volume in sm.storageVolumes) {
            if (!volume.isVolumeMountedCompat()) continue
            val dir = volume.directory ?: continue
            if (dir.absolutePath != primaryPath) return dir
        }
        return null
    }

    companion object {
        const val ID_PRIMARY = "internal_primary"
        const val ID_SD = "micro_sd"
    }
}

private fun android.os.storage.StorageVolume.isVolumeMountedCompat(): Boolean {
    return state == Environment.MEDIA_MOUNTED
}
