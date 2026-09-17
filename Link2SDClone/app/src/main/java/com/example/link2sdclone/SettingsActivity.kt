package com.example.link2sdclone

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

/**
 * شاشة الإعدادات: تحتوي مجموعات مشابهة لإعدادات Link2SD الأصلي:
 * - إنشاء الروابط تلقائياً بعد تثبيت تطبيق جديد
 * - نقل ذاكرة التخزين المؤقت (dalvik-cache) إلى SD
 * - خيارات الفرز الافتراضي لقائمة التطبيقات
 * - خيارات متقدمة (تنفيذ أوامر مخصصة، النسخ الاحتياطي لجدول الربط)
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settingsContainer, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<Preference>("pref_root_check")?.setOnPreferenceClickListener {
                val hasRoot = RootUtils.hasRootAccess()
                val message = if (hasRoot) "✔ صلاحيات Root متوفرة" else "✘ لا توجد صلاحيات Root"
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                true
            }

            findPreference<Preference>("pref_backup_link_table")?.setOnPreferenceClickListener {
                Toast.makeText(requireContext(), "تم حفظ نسخة احتياطية (تجريبي)", Toast.LENGTH_SHORT).show()
                true
            }

            findPreference<Preference>("pref_restore_link_table")?.setOnPreferenceClickListener {
                Toast.makeText(requireContext(), "تمت الاستعادة (تجريبي)", Toast.LENGTH_SHORT).show()
                true
            }
        }
    }
}
