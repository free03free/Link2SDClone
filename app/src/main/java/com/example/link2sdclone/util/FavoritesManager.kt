package com.example.link2sdclone.util

import android.content.Context

/** حفظ التطبيقات المفضلة في التخزين لتبقى بعد إغلاق التطبيق. */
object FavoritesManager {
    private const val PREFS = "favorites"
    private const val KEY = "packages"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isFavorite(context: Context, pkg: String): Boolean =
        prefs(context).getStringSet(KEY, null)?.contains(pkg) == true

    fun set(context: Context, pkg: String, favorite: Boolean) {
        val s = (prefs(context).getStringSet(KEY, null) ?: emptySet()).toMutableSet()
        if (favorite) s.add(pkg) else s.remove(pkg)
        prefs(context).edit().putStringSet(KEY, s).apply()
    }
}
