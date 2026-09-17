package com.example.link2sdclone.model

import android.graphics.drawable.Drawable

data class AppEntry(
    val packageName: String,
    val label: String,
    val apkPath: String,
    val icon: Drawable?,
    val apkSizeBytes: Long,
    val dataSizeBytes: Long,
    val cacheSizeBytes: Long,
    val isSystemApp: Boolean,
    val isOnSdCard: Boolean,
    val isFrozen: Boolean = false,
    var isFavorite: Boolean = false
) {
    val totalSizeBytes: Long get() = apkSizeBytes + dataSizeBytes + cacheSizeBytes
}
