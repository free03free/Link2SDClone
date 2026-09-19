package com.example.link2sdclone.ui

import android.content.Context
import androidx.preference.PreferenceManager
import com.example.link2sdclone.freeze.FreezeBackend
import com.example.link2sdclone.freeze.FreezeManager

/** يطبّق وسيط التجميد المحفوظ في الإعدادات عند فتح التطبيق (كان يُطبَّق فقط عند تغييره). */
fun applySavedFreezeBackend(context: Context) {
    val v = PreferenceManager.getDefaultSharedPreferences(context)
        .getString("pref_freeze_backend", "auto")
    FreezeManager.preferredBackend = when (v) {
        "shizuku" -> FreezeBackend.SHIZUKU
        "island" -> FreezeBackend.ISLAND
        "root" -> FreezeBackend.ROOT
        else -> null
    }
}
