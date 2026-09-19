package com.example.link2sdclone.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.link2sdclone.R
import java.util.concurrent.TimeUnit

/** عند الإقلاع: يجدول إعادة الربط إن فعّلت أحد خيارات الربط عند التشغيل. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(app)
        if (!sp.getBoolean("pref_auto_link", false) &&
            !sp.getBoolean("pref_relink_lib_boot", false) &&
            !sp.getBoolean("pref_relink_dex_boot", false)
        ) return
        WorkManager.getInstance(app).enqueue(
            OneTimeWorkRequestBuilder<BootRelinkWorker>()
                .setInitialDelay(60, TimeUnit.SECONDS)
                .build()
        )
    }
}

class BootRelinkWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val app = applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(app)
        val ready = LinkEngine.check(app)
        val mp = ready.mountPoint
        if (!ready.ok || mp == null) return Result.success()
        if (sp.getBoolean("pref_auto_link", false)) LinkBatch.relinkAll(app, mp, true, null, null)
        if (sp.getBoolean("pref_relink_lib_boot", false)) LinkBatch.relinkAll(app, mp, false, false, true)
        if (sp.getBoolean("pref_relink_dex_boot", false)) LinkBatch.relinkAll(app, mp, false, true, false)
        if (sp.getBoolean("pref_auto_link_notification", false)) notifyDone(app)
        return Result.success()
    }

    private fun notifyDone(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, "android.permission.POST_NOTIFICATIONS")
            != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("boot_relink", ctx.getString(R.string.fin_boot_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n = NotificationCompat.Builder(ctx, "boot_relink")
            .setSmallIcon(R.drawable.ic_info_outline)
            .setContentTitle(ctx.getString(R.string.fin_boot_title))
            .setContentText(ctx.getString(R.string.fin_boot_text))
            .setAutoCancel(true)
            .build()
        nm.notify(7302, n)
    }
}
