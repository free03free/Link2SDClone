package com.example.link2sdclone.ui

import android.os.Bundle
import android.content.Context
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.link2sdclone.R
import com.example.link2sdclone.freeze.FreezeBackend
import com.example.link2sdclone.freeze.FreezeManager
import com.example.link2sdclone.lock.LockSetupActivity
import com.example.link2sdclone.util.LocaleHelper

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
            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:")
            }
            startActivity(android.content.Intent.createChooser(intent, null))
            true
        }

        // "قم بمسح تلقائي لذاكرة التخزين" غير مفعّلة عمدًا -- مش بسبب الدفع.
        // حتى بالنسخة الأصلية المدفوعة هاي الميزة تحتاج صلاحيات
        // CLEAR_APP_CACHE / DELETE_CACHE_FILES وهي صلاحيات نظام لا يمنحها
        // أندرويد لأي تطبيق طرف ثالث بدون Root، بغض النظر عن الدفع.
        // تعطيلها هون صادق تقنيًا، مش قيد دفع مصطنع.
        findPreference<CheckBoxPreference>("pref_auto_clear_cache")?.apply {
            isEnabled = false
            summary = "يتطلب صلاحية نظام (Root) غير متاحة لتطبيقات الطرف الثالث — حتى بالنسخة الأصلية المدفوعة"
        }

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

        // المظهر: ListPreference يحفظ القيمة تلقائيًا بـ SharedPreferences
        // الافتراضية قبل استدعاء هذا الـ listener، فما محتاجين نكتبها يدويًا.
        findPreference<ListPreference>("pref_appearance")?.setOnPreferenceChangeListener { _, _ ->
            LocaleHelper.applyTheme(requireContext())
            true
        }

        // اللغة: نفس المبدأ -- القيمة محفوظة تلقائيًا، إحنا بس بنطبقها فورًا
        // ونعيد إنشاء الشاشة الحالية لتظهر النتيجة على الفور.
        findPreference<ListPreference>("pref_language")?.setOnPreferenceChangeListener { _, newValue ->
            LocaleHelper.applyLanguageAndRecreate(requireActivity(), newValue as String)
            true
        }
    }
}
