package com.example.link2sdclone.freeze

import android.app.Activity
import android.content.Context

/**
 * Single entry point MainActivity calls into. Picks whichever backend the
 * user prefers (see [preferredBackend]) among the ones actually installed,
 * asks for its permission the first time, then performs the freeze/unfreeze.
 *
 * Wiring required in the host Activity (see MainActivity for the concrete example):
 *  - onCreate(): FreezeManager.registerShizukuListener(this)
 *  - onDestroy(): FreezeManager.unregisterShizukuListener()
 *  - onRequestPermissionsResult(): FreezeManager.onRequestPermissionsResult(requestCode, grantResults) { granted -> retry }
 *  - onActivityResult(): FreezeManager.onActivityResult(requestCode, resultCode) { success -> ... }
 */
object FreezeManager {

    /** Sticky choice, e.g. read from Settings; null = "ask me" / "use whichever is installed". */
    var preferredBackend: FreezeBackend? = null

    private var pendingIslandCallback: ((Boolean) -> Unit)? = null
    private var pendingShizukuCallback: ((Boolean) -> Unit)? = null
    private var shizukuPermissionListener: Any? = null

    fun availableBackends(context: Context): List<FreezeBackend> = buildList {
        if (ShizukuFreezeBackend.isInstalled(context) && ShizukuFreezeBackend.isServiceRunning()) add(FreezeBackend.SHIZUKU)
        if (IslandFreezeBackend.isInstalled(context)) add(FreezeBackend.ISLAND)
    }

    /**
     * Attempts to freeze/unfreeze [packageName] using [backend].
     * [onFinalResult] is invoked synchronously for immediate outcomes (success/
     * failure/not-available) and asynchronously later for PermissionRequested
     * outcomes, once the user answers the permission prompt — the caller
     * should update its UI in both cases the same way.
     */
    fun setFrozen(
        activity: Activity,
        backend: FreezeBackend,
        packageName: String,
        freeze: Boolean,
        onFinalResult: (FreezeResult) -> Unit
    ) {
        when (backend) {
            FreezeBackend.SHIZUKU -> {
                if (!ShizukuFreezeBackend.isInstalled(activity) || !ShizukuFreezeBackend.isServiceRunning()) {
                    onFinalResult(FreezeResult.BackendNotAvailable); return
                }
                if (!ShizukuFreezeBackend.hasPermission()) {
                    pendingShizukuCallback = { granted ->
                        if (granted) {
                            val ok = ShizukuFreezeBackend.setAppEnabled(packageName, enabled = !freeze)
                            onFinalResult(if (ok) FreezeResult.Success else FreezeResult.Failed("shizuku_call_failed"))
                        } else {
                            onFinalResult(FreezeResult.Failed("shizuku_permission_denied"))
                        }
                    }
                    ShizukuFreezeBackend.requestPermission(FreezeRequestCodes.SHIZUKU_PERMISSION)
                    onFinalResult(FreezeResult.PermissionRequested)
                    return
                }
                val ok = ShizukuFreezeBackend.setAppEnabled(packageName, enabled = !freeze)
                onFinalResult(if (ok) FreezeResult.Success else FreezeResult.Failed("shizuku_call_failed"))
            }

            FreezeBackend.ISLAND -> {
                if (!IslandFreezeBackend.isInstalled(activity)) {
                    onFinalResult(FreezeResult.BackendNotAvailable); return
                }
                if (!IslandFreezeBackend.hasPermission(activity)) {
                    pendingIslandCallback = { granted ->
                        if (granted) {
                            val sent = IslandFreezeBackend.requestFreeze(
                                activity, packageName, freeze, FreezeRequestCodes.ISLAND_API_RESULT
                            )
                            if (!sent) onFinalResult(FreezeResult.Failed("island_intent_failed"))
                            // else: final Success/Failed comes from onActivityResult()
                        } else {
                            onFinalResult(FreezeResult.Failed("island_permission_denied"))
                        }
                    }
                    IslandFreezeBackend.requestPermission(activity, FreezeRequestCodes.ISLAND_PERMISSION)
                    onFinalResult(FreezeResult.PermissionRequested)
                    return
                }
                val sent = IslandFreezeBackend.requestFreeze(
                    activity, packageName, freeze, FreezeRequestCodes.ISLAND_API_RESULT
                )
                if (!sent) onFinalResult(FreezeResult.Failed("island_intent_failed"))
                // else: caller's onActivityResult() reports the real outcome.
            }
        }
    }

    // ---- Activity lifecycle plumbing ----

    /** Call from Activity.onRequestPermissionsResult(). */
    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == FreezeRequestCodes.ISLAND_PERMISSION) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            pendingIslandCallback?.invoke(granted)
            pendingIslandCallback = null
        }
    }

    /** Call from Activity.onActivityResult() for Island's freeze/unfreeze result. */
    fun onActivityResult(requestCode: Int, resultCode: Int, onIslandOutcome: (FreezeResult) -> Unit) {
        if (requestCode == FreezeRequestCodes.ISLAND_API_RESULT) {
            onIslandOutcome(if (resultCode == Activity.RESULT_OK) FreezeResult.Success else FreezeResult.Failed("island_denied_or_not_managed"))
        }
    }

    /**
     * Registers Shizuku's own permission-result listener. Shizuku delivers the
     * result this way (not always through onRequestPermissionsResult) on
     * every supported version, so this is the reliable path.
     */
    fun registerShizukuListener(activity: Activity) {
        val listener = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == FreezeRequestCodes.SHIZUKU_PERMISSION) {
                val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
                pendingShizukuCallback?.invoke(granted)
                pendingShizukuCallback = null
            }
        }
        shizukuPermissionListener = listener
        rikka.shizuku.Shizuku.addRequestPermissionResultListener(listener)
    }

    fun unregisterShizukuListener() {
        (shizukuPermissionListener as? rikka.shizuku.Shizuku.OnRequestPermissionResultListener)?.let {
            rikka.shizuku.Shizuku.removeRequestPermissionResultListener(it)
        }
        shizukuPermissionListener = null
    }
}
