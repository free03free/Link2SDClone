package com.example.link2sdclone

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.example.link2sdclone.lock.LockActivity
import com.example.link2sdclone.lock.LockManager

class Link2SDApp : Application() {

    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()
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
