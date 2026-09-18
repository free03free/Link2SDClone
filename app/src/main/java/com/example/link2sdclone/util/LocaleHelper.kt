package com.example.link2sdclone.util

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager

/**
 * Applies the real, currently-selected value of pref_appearance (theme) and
 * pref_language.
 *
 * Theme: AppCompatDelegate.setDefaultNightMode() now works correctly since
 * AppTheme's parent is Theme.MaterialComponents.DayNight.*  -- it
 * automatically recreates every open AppCompatActivity, no per-Activity
 * code needed.
 *
 * Language: uses AppCompatDelegate.setApplicationLocales(), the official
 * per-app-language API (AppCompat 1.6.0+). Unlike the old
 * Configuration/updateConfiguration hack, this is backed by Android's own
 * per-app locale mechanism, so RTL layout direction, Arabic digit/date
 * formatting, and every screen (not just the one that set it) update
 * correctly and persist automatically across app restarts -- no manual
 * SharedPreferences read/write needed for this part.
 */
object LocaleHelper {

    fun applyTheme(context: Context) {
        val mode = when (
            PreferenceManager.getDefaultSharedPreferences(context)
                .getString("pref_appearance", "light")
        ) {
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /** langValue: "default", "ar", or "en" (matches pref_language's entryValues). */
    fun applyLanguage(langValue: String) {
        val locales = if (langValue == "default") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(langValue)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** Call from SettingsFragment right after the user picks a new language. */
    fun applyLanguageAndRecreate(activity: Activity, langValue: String) {
        applyLanguage(langValue)
        activity.recreate()
    }
}
