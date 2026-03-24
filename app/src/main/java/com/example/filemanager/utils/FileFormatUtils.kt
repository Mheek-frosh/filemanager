package com.example.filemanager.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Human-readable file size and time labels for lists and detail screens. */
object FileFormatUtils {
    fun sizeToDisplay(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = sizeBytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
    }

    fun dateTimeFromEpochSeconds(epochSeconds: Long): String {
        val date = Date(epochSeconds * 1000)
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
    }
}
