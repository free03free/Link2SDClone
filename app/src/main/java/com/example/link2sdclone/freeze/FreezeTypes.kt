package com.example.link2sdclone.freeze

/**
 * The two no-root ways this app can flip an app's enabled/disabled (frozen) state.
 * Root (if ever added) would bypass both and call `pm disable-user` / `pm enable`
 * directly via `su`.
 */
enum class FreezeBackend {
    SHIZUKU,
    ISLAND
}

/** Outcome of a single freeze/unfreeze attempt. */
sealed class FreezeResult {
    object Success : FreezeResult()

    /** The backend exists but we don't hold its permission yet.
     *  The caller just triggered the OS/Shizuku permission prompt;
     *  the real result will arrive in onRequestPermissionsResult /
     *  the Shizuku permission-result listener / onActivityResult. */
    object PermissionRequested : FreezeResult()

    object BackendNotAvailable : FreezeResult()
    data class Failed(val reason: String) : FreezeResult()
}

/** Request codes used for the async permission / activity-result round-trips. */
object FreezeRequestCodes {
    const val SHIZUKU_PERMISSION = 5501
    const val ISLAND_PERMISSION = 5502
    const val ISLAND_API_RESULT = 5503
}
