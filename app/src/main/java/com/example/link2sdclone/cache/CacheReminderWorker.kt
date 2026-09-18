package com.example.link2sdclone.cache

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.link2sdclone.MainActivity
import com.example.link2sdclone.R
import com.example.link2sdclone.storage.StorageStatsHelper

/**
 * Periodic reminder only. It CANNOT clear another app's cache -- no
 * non-root Android API allows a third-party app to do that (this was
 * removed in KitKat). It only surfaces which apps crossed the threshold
 * so the user can clear them manually, one tap away, from their own
 * system App Info screen.
 */
class CacheReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("cache_exclude_prefs", Context.MODE_PRIVATE)
        val excluded = prefs.getStringSet("excluded_packages", emptySet()) ?: emptySet()
        val thresholdMb = applicationContext
            .getSharedPreferences("Link2SDClonePrefs", Context.MODE_PRIVATE)
            .getString("pref_cache_threshold_mb", "100")?.toLongOrNull() ?: 100L
        val thresholdBytes = thresholdMb * 1024 * 1024

        if (!StorageStatsHelper.hasUsageAccess(applicationContext)) return Result.success()

        val pm = applicationContext.packageManager
        val heavyApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName !in excluded }
            .mapNotNull { info ->
                val sizes = StorageStatsHelper.queryRealSizes(applicationContext, info.uid) ?: return@mapNotNull null
                if (sizes.cacheBytes >= thresholdBytes) info.packageName to sizes.cacheBytes else null
            }

        if (heavyApps.isNotEmpty()) notify(heavyApps.size)
        return Result.success()
    }

    private fun notify(count: Int) {
        val channelId = "cache_reminder"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "تذكير الكاش", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_sd_card)
            .setContentTitle("تذكير محو الكاش")
            .setContentText("$count تطبيق تجاوز حد الكاش، اضغط للمراجعة")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(9911, notification)
    }
}
