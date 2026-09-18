package com.example.link2sdclone.ui

import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.link2sdclone.R
import com.example.link2sdclone.freeze.FreezeBackend
import com.example.link2sdclone.freeze.FreezeManager
import com.example.link2sdclone.lock.LockSetupActivity

/**
 * Settings screen — matches Link2SD's preference layout:
 * upgrade banner -> عام -> محو ذاكرة التخزين المؤقت -> الربط التلقائي
 * -> الإعدادات البديهية لـ apps2SD -> حول Link2SD
 *
 * Add to build.gradle: implementation 'androidx.preference:preference-ktx:1.2.1'
 */
class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>("pref_upgrade_plus")?.setOnPreferenceClickListener {
            // TODO: open Play Store listing for the paid version
            true
        }

        findPreference<Preference>("pref_install_location")?.setOnPreferenceClickListener {
            // TODO: show a dialog to choose install location (internal / auto / sd)
            true
        }

        findPreference<Preference>("pref_exclusion_list")?.setOnPreferenceClickListener {
            startActivity(android.content.Intent(requireContext(), CacheExcludeActivity::class.java))
            true
        }

        findPreference<Preference>("pref_auto_link_settings")?.setOnPreferenceClickListener {
            // TODO: open dialog with apk/dex/lib auto-link toggles
            true
        }

        findPreference<Preference>("pref_app_lock")?.setOnPreferenceClickListener {
            startActivity(android.content.Intent(requireContext(), LockSetupActivity::class.java))
            true
        }

        findPreference<Preference>("pref_about")?.setOnPreferenceClickListener {
            // TODO: show about dialog
            true
        }

        findPreference<Preference>("pref_whats_new")?.setOnPreferenceClickListener {
            // TODO: show changelog dialog
            true
        }

        findPreference<Preference>("pref_send_email")?.setOnPreferenceClickListener {
            // TODO: launch ACTION_SENDTO mailto intent
            true
        }

        // "قم بمسح تلقائي لذاكرة التخزين" is a paid-only feature in Link2SD
        // (shown disabled with a small red lock badge). Re-enable it here
        // once you gate it behind your own purchase/pro check.
        findPreference<CheckBoxPreference>("pref_auto_clear_cache")?.isEnabled = false

        // "auto" (null) means: use whichever of Shizuku/Island is installed,
        // asking the user to pick if both are. Picking one here pins it.
        findPreference<ListPreference>("pref_freeze_backend")?.setOnPreferenceChangeListener { _, newValue ->
            FreezeManager.preferredBackend = when (newValue as String) {
                "shizuku" -> FreezeBackend.SHIZUKU
                "island" -> FreezeBackend.ISLAND
                else -> null
            }
            true
        }
    }
}
