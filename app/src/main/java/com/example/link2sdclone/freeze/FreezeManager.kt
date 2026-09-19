package com.example.link2sdclone.freeze

import android.app.Activity
import android.content.Context
import com.example.link2sdclone.util.PrivilegedShell

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

    // Lists, not single nullable callbacks: when a group action fires
    // setFrozen() for several packages back-to-back before permission is
    // granted, each call used to overwrite the previous pending callback,
    // silently dropping the freeze for every package but the last one in
    // the batch (and breaking the remaining-count Toast). Queuing callbacks
    // and invoking+draining the whole list once permission is granted fixes
    // both the missing-Toast bug and the "only last app freezes" bug.
    private val pendingIslandCallbacks = mutableListOf<(Boolean) -> Unit>()
    private val pendingShizukuCallbacks = mutableListOf<(Boolean) -> Unit>()

    // Queued in FIFO order at the moment each Island freeze/unfreeze intent
    // is actually sent. onActivityResult() drains one per result received,
    // so batch operations route each Island outcome back to the correct
    // package's original callback instead of losing it.
    private val pendingIslandResultCallbacks = mutableListOf<(FreezeResult) -> Unit>()
    private var shizukuPermissionListener: Any? = null

    fun availableBackends(context: Context): List<FreezeBackend> = buildList {
        if (PrivilegedShell.rootKnown == true) add(FreezeBackend.ROOT)
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
            FreezeBackend.ROOT -> performRootFreezeAsync(activity, packageName, freeze, onFinalResult)

            FreezeBackend.SHIZUKU -> {
                if (!ShizukuFreezeBackend.isInstalled(activity) || !ShizukuFreezeBackend.isServiceRunning()) {
                    onFinalResult(FreezeResult.BackendNotAvailable); return
                }
                if (!ShizukuFreezeBackend.hasPermission()) {
                    val alreadyPending = pendingShizukuCallbacks.isNotEmpty()
                    pendingShizukuCallbacks.add { granted ->
                        if (granted) {
                            onFinalResult(performShizukuFreeze(activity, packageName, freeze))
                        } else {
                            onFinalResult(FreezeResult.Failed("shizuku_permission_denied"))
                        }
                    }
                    // Only fire the actual system prompt once; the rest of
                    // this batch just queues up and waits for the same
                    // permission result below.
                    if (!alreadyPending) {
                        ShizukuFreezeBackend.requestPermission(FreezeRequestCodes.SHIZUKU_PERMISSION)
                    }
                    onFinalResult(FreezeResult.PermissionRequested)
                    return
                }
                onFinalResult(performShizukuFreeze(activity, packageName, freeze))
            }

            FreezeBackend.ISLAND -> {
                if (!IslandFreezeBackend.isInstalled(activity)) {
                    onFinalResult(FreezeResult.BackendNotAvailable); return
                }
                if (!IslandFreezeBackend.hasPermission(activity)) {
                    val alreadyPending = pendingIslandCallbacks.isNotEmpty()
                    pendingIslandCallbacks.add { granted ->
                        if (granted) {
                            val sent = IslandFreezeBackend.requestFreeze(
                                activity, packageName, freeze, FreezeRequestCodes.ISLAND_API_RESULT
                            )
                            if (sent) {
                                pendingIslandResultCallbacks.add(onFinalResult)
                            } else {
                                onFinalResult(FreezeResult.Failed("island_intent_failed"))
                            }
                        } else {
                            onFinalResult(FreezeResult.Failed("island_permission_denied"))
                        }
                    }
                    if (!alreadyPending) {
                        IslandFreezeBackend.requestPermission(activity, FreezeRequestCodes.ISLAND_PERMISSION)
                    }
                    onFinalResult(FreezeResult.PermissionRequested)
                    return
                }
                val sent = IslandFreezeBackend.requestFreeze(
                    activity, packageName, freeze, FreezeRequestCodes.ISLAND_API_RESULT
                )
                if (sent) {
                    pendingIslandResultCallbacks.add(onFinalResult)
                } else {
                    onFinalResult(FreezeResult.Failed("island_intent_failed"))
                }
            }
        }
    }

    /**
     * Calls the reflection-based Shizuku disable/enable call, then re-reads
     * the REAL system state via PackageManager to confirm it actually
     * changed -- setAppEnabled() returning true only means the binder call
     * didn't throw, not that Android actually applied it (e.g. silent
     * SELinux denial). Without this check the UI could show "frozen" for
     * an app that was never really touched, which reverts the moment the
     * app re-reads real state (e.g. on next launch) -- exactly the ghost
     * freeze bug this fixes.
     */
    private fun performShizukuFreeze(context: Context, packageName: String, freeze: Boolean): FreezeResult {
        val ok = ShizukuFreezeBackend.setAppEnabled(packageName, enabled = !freeze)
        if (!ok) return FreezeResult.Failed("shizuku_call_failed")
        val nowEnabled = ShizukuFreezeBackend.isAppEnabled(context, packageName)
        val expectedEnabled = !freeze
        return if (nowEnabled == expectedEnabled) FreezeResult.Success
        else FreezeResult.Failed("shizuku_verify_failed")
    }

    // Root: أوامر pm عبر su، في خيط واحد متسلسل كي لا تتزاحم عمليات su في التجميد الجماعي.
    private val rootExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun performRootFreezeAsync(
        context: Context, packageName: String, freeze: Boolean, onFinalResult: (FreezeResult) -> Unit
    ) {
        val app = context.applicationContext
        rootExecutor.execute { onFinalResult(performRootFreeze(app, packageName, freeze)) }
    }

    private fun performRootFreeze(context: Context, packageName: String, freeze: Boolean): FreezeResult {
        if (packageName == context.packageName) return FreezeResult.Failed("root_self_package")
        if (!Regex("[A-Za-z0-9_.]+").matches(packageName)) return FreezeResult.Failed("root_bad_package")
        val cmd = if (freeze) "pm disable-user --user 0 $packageName" else "pm enable --user 0 $packageName"
        val r = PrivilegedShell.run(PrivilegedShell.Mode.ROOT, cmd)
        if (!r.ok) return FreezeResult.Failed("root_call_failed: " + r.output.take(100))
        // نتحقق من الحالة الحقيقية في النظام، لا من نجاح الأمر فقط.
        val state = try {
            context.packageManager.getApplicationEnabledSetting(packageName)
        } catch (e: Exception) {
            return FreezeResult.Failed("root_verify_failed")
        }
        val frozenNow = state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
            state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        return if (frozenNow == freeze) FreezeResult.Success else FreezeResult.Failed("root_verify_failed")
    }

    // ---- Activity lifecycle plumbing ----

    /** Call from Activity.onRequestPermissionsResult(). */
    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == FreezeRequestCodes.ISLAND_PERMISSION) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            val callbacks = pendingIslandCallbacks.toList()
            pendingIslandCallbacks.clear()
            callbacks.forEach { it.invoke(granted) }
        }
    }

    /**
     * Call from Activity.onActivityResult() for Island's freeze/unfreeze result.
     * Drains one queued per-package callback (FIFO) if any are pending --
     * this is what makes batch group operations attribute each Island
     * result to the right package. onIslandOutcome is an optional fallback
     * for callers that only ever handle one pending app at a time and don't
     * queue through setFrozen's own onFinalResult (kept for compatibility).
     */
    fun onActivityResult(requestCode: Int, resultCode: Int, onIslandOutcome: ((FreezeResult) -> Unit)? = null) {
        if (requestCode == FreezeRequestCodes.ISLAND_API_RESULT) {
            val result = if (resultCode == Activity.RESULT_OK) FreezeResult.Success else FreezeResult.Failed("island_denied_or_not_managed")
            if (pendingIslandResultCallbacks.isNotEmpty()) {
                val callback = pendingIslandResultCallbacks.removeAt(0)
                callback(result)
            } else {
                onIslandOutcome?.invoke(result)
            }
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
                val callbacks = pendingShizukuCallbacks.toList()
                pendingShizukuCallbacks.clear()
                callbacks.forEach { it.invoke(granted) }
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
