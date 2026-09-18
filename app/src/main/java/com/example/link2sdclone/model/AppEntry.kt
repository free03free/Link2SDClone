package com.example.link2sdclone.model

import android.graphics.drawable.Drawable

data class AppEntry(
    val packageName: String,
    val label: String,
    val apkPath: String,
    val icon: Drawable?,
    val uid: Int = 0,
    val apkSizeBytes: Long = 0L,
    var dataSizeBytes: Long = 0L,      // real value once StorageStatsHelper succeeds, else 0
    var cacheSizeBytes: Long = 0L,     // real value once StorageStatsHelper succeeds, else 0
    var hasRealSizes: Boolean = false, // true once dataSizeBytes/cacheSizeBytes came from StorageStatsManager
    val isSystemApp: Boolean = false,
    val isOnSdCard: Boolean = false,
    var isFrozen: Boolean = false,
    var isFavorite: Boolean = false,
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L
) {
    val isRecentlyUpdated: Boolean get() = lastUpdateTime > firstInstallTime + 60_000L
    val totalSizeBytes: Long get() = apkSizeBytes + dataSizeBytes + cacheSizeBytes
}
