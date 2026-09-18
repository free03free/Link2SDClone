package com.example.link2sdclone.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import java.util.Locale

/**
 * Applies the real, currently-selected value of pref_appearance (theme) and
 * pref_language every time an Activity starts, and lets SettingsFragment
 * push a live change into the running process.
 *
 * Honest limitation: this uses the legacy Locale.setDefault() +
 * updateConfiguration() approach rather than per-Activity attachBaseContext
 * wrapping (which would require editing every single Activity). It works
 * for the app's own resources on the overwhelming majority of devices, but
 * is not the modern per-app-language API introduced in Android 13. If a
 * screen doesn't refresh, closing and reopening it (or restarting the app)
 * always applies it correctly.
 */
object LocaleHelper {

    private fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun applyTheme(context: Context) {
        val mode = when (prefs(context).getString("pref_appearance", "light")) {
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun applyLanguage(context: Context) {
        val langValue = prefs(context).getString("pref_language", "default") ?: "default"
        val locale = if (langValue == "default") {
            Locale.getDefault()
        } else {
            Locale(langValue)
        }
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    /** Call from SettingsFragment right after the user picks a new language,
     *  so the currently-open screen reflects it immediately. */
    fun applyLanguageAndRecreate(activity: Activity) {
        applyLanguage(activity)
        activity.recreate()
    }
}
