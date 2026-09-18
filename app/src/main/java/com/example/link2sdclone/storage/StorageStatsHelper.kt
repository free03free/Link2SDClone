package com.example.link2sdclone.storage

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings

data class RealSizes(val appBytes: Long, val cacheBytes: Long, val dataBytes: Long)

/**
 * Wraps StorageStatsManager to read the REAL apk/data/cache size of an
 * installed package -- the same mechanism the original Link2SD relies on
 * (that's why its manifest requests PACKAGE_USAGE_STATS). Without this
 * permission granted, data/cache sizes for OTHER apps are not readable by
 * any non-root app on modern Android; there is no workaround, so we degrade
 * gracefully (return null) rather than showing fake numbers.
 */
object StorageStatsHelper {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun queryRealSizes(context: Context, uid: Int): RealSizes? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        if (!hasUsageAccess(context)) return null
        return try {
            val statsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val uuid = storageManager.getUuidForPath(context.filesDir)
            val stats = statsManager.queryStatsForUid(uuid, uid)
            RealSizes(appBytes = stats.appBytes, cacheBytes = stats.cacheBytes, dataBytes = stats.dataBytes)
        } catch (e: Exception) {
            null
        }
    }
}
