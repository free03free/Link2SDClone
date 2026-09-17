package com.example.link2sdclone.freeze

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Wraps Island's public, documented Intent API
 * (https://github.com/oasisfeng/island/blob/master/shared/src/main/java/com/oasisfeng/island/api/Api.java).
 *
 * Two packages can host it: the Play Store build "com.oasisfeng.island" and the
 * F-Droid build "com.oasisfeng.island.fdroid". Only works on apps Island
 * actually controls: either apps cloned *into* the Island space, or — if the
 * user has turned on Island's "Managed Mainland" mode — any app outside it too.
 *
 * Island's permission is a normal (non-Shizuku-style) Android runtime
 * permission, so it goes through the ordinary ActivityCompat flow.
 */
object IslandFreezeBackend {

    private const val PACKAGE_PLAY = "com.oasisfeng.island"
    private const val PACKAGE_FDROID = "com.oasisfeng.island.fdroid"
    private const val PERMISSION_FREEZE = "com.oasisfeng.island.permission.FREEZE_PACKAGE"

    private const val ACTION_FREEZE = "com.oasisfeng.island.action.FREEZE"
    private const val ACTION_UNFREEZE = "com.oasisfeng.island.action.UNFREEZE"

    fun installedPackageName(context: android.content.Context): String? {
        val pm = context.packageManager
        for (candidate in arrayOf(PACKAGE_PLAY, PACKAGE_FDROID)) {
            try {
                pm.getPackageInfo(candidate, 0)
                return candidate
            } catch (e: PackageManager.NameNotFoundException) {
                // try next
            }
        }
        return null
    }

    fun isInstalled(context: android.content.Context): Boolean =
        installedPackageName(context) != null

    fun hasPermission(context: android.content.Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION_FREEZE) ==
            PackageManager.PERMISSION_GRANTED

    fun requestPermission(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, arrayOf(PERMISSION_FREEZE), requestCode)
    }

    /**
     * Sends the freeze/unfreeze request and waits for the result in
     * onActivityResult(requestCode, RESULT_OK / RESULT_CANCELED, ...).
     *
     * Per Island's own docs: never target the component explicitly — always
     * resolve via setPackage() so Island can route the request internally.
     */
    fun requestFreeze(activity: Activity, packageName: String, freeze: Boolean, requestCode: Int): Boolean {
        val islandPackage = installedPackageName(activity) ?: return false
        val intent = Intent(if (freeze) ACTION_FREEZE else ACTION_UNFREEZE).apply {
            setPackage(islandPackage)
            data = Uri.parse("package:$packageName")
        }
        return try {
            activity.startActivityForResult(intent, requestCode)
            true
        } catch (e: Throwable) {
            false
        }
    }
}
