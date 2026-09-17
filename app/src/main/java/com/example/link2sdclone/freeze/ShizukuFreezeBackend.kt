package com.example.link2sdclone.freeze

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Talks to the framework's hidden `IPackageManager` through a binder that
 * Shizuku hands us (running as `shell`/`root`, so it's allowed to call the
 * otherwise-restricted `setApplicationEnabledSetting`). No AIDL stub or
 * hidden-API jar is needed at compile time — everything below is plain
 * reflection, which is the same trick apps like "App Manager" / "Hyperceiler"
 * use.
 *
 * Requires (in app/build.gradle.kts):
 *   implementation("dev.rikka.shizuku:api:13.1.5")
 *   implementation("dev.rikka.shizuku:provider:13.1.5")
 *
 * The host Activity must also install a Shizuku.OnRequestPermissionResultListener
 * (see MainActivity) and forward onRequestPermissionsResult — Shizuku's own
 * permission dialog does NOT go through the normal Android callback on all
 * versions, so both paths are wired for safety.
 */
object ShizukuFreezeBackend {

    private const val PACKAGE_NAME_SHIZUKU = "moe.shizuku.privileged.api"

    /** Component enabled-state constants from android.content.pm.PackageManager (public API). */
    private const val COMPONENT_ENABLED_STATE_DEFAULT = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    private const val COMPONENT_ENABLED_STATE_DISABLED_USER = PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE_NAME_SHIZUKU, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** True once the Shizuku *service* (the running daemon, started from the
     *  Shizuku app or via `adb shell sh /sdcard/Android/.../start.sh`) is reachable. */
    fun isServiceRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        if (!isServiceRunning()) false
        else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    /** Fires Shizuku's own permission dialog. Result arrives via the listener
     *  registered with Shizuku.addRequestPermissionResultListener(...). */
    fun requestPermission(requestCode: Int) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (e: Throwable) {
            // Service not running / too old Shizuku — caller should fall back to Island.
        }
    }

    /**
     * @param enabled true = unfreeze (restore to DEFAULT), false = freeze (DISABLED_USER,
     *   the same state `pm disable-user` produces — reversible without root, unlike
     *   COMPONENT_ENABLED_STATE_DISABLED which some OEMs treat as sticky).
     */
    fun setAppEnabled(packageName: String, enabled: Boolean, userId: Int = 0): Boolean {
        return try {
            val rawBinder = SystemServiceHelper.getSystemService("package")
            val wrappedBinder = ShizukuBinderWrapper(rawBinder)

            val stubClass = Class.forName("android.content.pm.IPackageManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
            val packageManagerProxy = asInterface.invoke(null, wrappedBinder)
                ?: return false

            val newState = if (enabled) COMPONENT_ENABLED_STATE_DEFAULT else COMPONENT_ENABLED_STATE_DISABLED_USER

            val method = packageManagerProxy.javaClass.getMethod(
                "setApplicationEnabledSetting",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            method.invoke(packageManagerProxy, packageName, newState, 0 /* flags */, userId, packageName)
            true
        } catch (e: Throwable) {
            false
        }
    }

    /** Best-effort read of the current enabled state, same reflection path as above. */
    fun isAppEnabled(context: Context, packageName: String): Boolean {
        // Cheaper path: this doesn't need Shizuku at all for *reading* the state of
        // an app the caller can already see via getInstalledApplications().
        return try {
            context.packageManager.getApplicationEnabledSetting(packageName) !=
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER &&
                context.packageManager.getApplicationEnabledSetting(packageName) !=
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (e: Throwable) {
            true
        }
    }
}
