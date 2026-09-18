package com.example.link2sdclone

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.example.link2sdclone.lock.LockActivity
import com.example.link2sdclone.lock.LockManager
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Link2SDApp : Application() {

    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()

        // Temporary diagnostic handler: writes any uncaught crash (message +
        // full stack trace + timestamp) to a file under this app's own
        // external-files directory. No storage permission is needed for
        // this path on any Android version, and the file is readable from
        // Termux without adb/root, e.g.:
        //   cat /sdcard/Android/data/com.example.link2sdclone/files/crash_log.txt
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val entry = "\n===== CRASH at $timestamp (thread: ${thread.name}) =====\n$sw"
                val file = File(getExternalFilesDir(null), "crash_log.txt")
                file.appendText(entry)
            } catch (e: Exception) {
                // If logging itself fails, don't block the default handler.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                if (activity !is LockActivity &&
                    LockManager.isEnabled(activity) &&
                    !LockManager.isUnlockedForSession()
                ) {
                    activity.startActivity(Intent(activity, LockActivity::class.java))
                }
            }

            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount--
                if (startedActivityCount <= 0) {
                    LockManager.markLocked()
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
