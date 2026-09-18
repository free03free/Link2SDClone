package com.example.link2sdclone

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.link2sdclone.ui.CacheExcludeActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_container)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
        title = getString(R.string.menu_settings)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<androidx.preference.ListPreference>("pref_theme")?.setOnPreferenceChangeListener { _, newValue ->
                applyTheme(newValue as String)
                true
            }

            findPreference<EditTextPreference>("pref_cache_threshold_mb")?.setOnBindEditTextListener {
                it.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }

            findPreference<Preference>("pref_cache_exclude")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), CacheExcludeActivity::class.java))
                true
            }
        }

        private fun applyTheme(value: String) {
            val mode = when (value) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}
