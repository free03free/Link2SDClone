package com.example.link2sdclone.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.link2sdclone.R
import java.util.Locale

/** يمسح مخبأ كل التطبيقات دوريًا عبر Root أو Shizuku، ويرسل تنبيهًا إن تجاوز المحرَّر الحد المحدد. */
class AutoClearCacheWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (!sp.getBoolean("pref_auto_clear_cache", false)) return Result.success()

        val mode = PrivilegedShell.detect()
        if (mode != PrivilegedShell.Mode.ROOT && mode != PrivilegedShell.Mode.SHIZUKU) {
            return Result.success()
        }

        val path = Environment.getDataDirectory().path
        val before = StatFs(path).availableBytes
        val r = PrivilegedShell.run(mode, "pm trim-caches 999G", 120_000)
        if (!r.ok) return Result.success()
        val freed = StatFs(path).availableBytes - before

        val minMb = sp.getInt("clear_cache_notif_size_mb", 5)
        if (sp.getBoolean("pref_auto_delete_cache_notif", true) && freed >= minMb * 1024L * 1024L) {
            notifyFreed(ctx, freed)
        }
        return Result.success()
    }

    private fun notifyFreed(ctx: Context, freed: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, "android.permission.POST_NOTIFICATIONS")
            != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, ctx.getString(R.string.st2_notif_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val mb = String.format(Locale.getDefault(), "%.1f", freed / 1048576.0)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_info_outline)
            .setContentTitle(ctx.getString(R.string.st2_notif_title))
            .setContentText(ctx.getString(R.string.st2_notif_text, mb))
            .setAutoCancel(true)
            .build()
        nm.notify(7301, n)
    }

    private companion object {
        const val CHANNEL = "auto_clear_cache"
    }
}
