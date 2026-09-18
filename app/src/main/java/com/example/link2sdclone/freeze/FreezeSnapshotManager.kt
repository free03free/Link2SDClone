package com.example.link2sdclone.freeze

import android.content.Context

/**
 * Stores exactly ONE snapshot of "which packages were frozen at the moment
 * the user pressed the button". No history, no multiple snapshots -- by
 * design, to avoid ambiguity (per user's explicit request).
 *
 * Never touches any package that wasn't already frozen by the user
 * beforehand, so system apps are never at risk here -- the snapshot only
 * ever contains what was already in a frozen state.
 */
object FreezeSnapshotManager {
    private const val PREFS = "freeze_snapshot_prefs"
    private const val KEY_PACKAGES = "snapshot_packages"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasSnapshot(context: Context): Boolean = prefs(context).contains(KEY_PACKAGES)

    fun save(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_PACKAGES, packages).apply()
    }

    fun get(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PACKAGES, emptySet())?.toSet() ?: emptySet()

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_PACKAGES).apply()
    }
}
