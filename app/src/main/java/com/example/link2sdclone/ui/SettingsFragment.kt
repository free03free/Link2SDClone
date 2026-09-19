package com.example.link2sdclone.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.link2sdclone.R
import com.example.link2sdclone.freeze.FreezeBackend
import com.example.link2sdclone.freeze.FreezeManager
import com.example.link2sdclone.lock.LockSetupActivity
import com.example.link2sdclone.util.AutoClearCacheWorker
import com.example.link2sdclone.util.LocaleHelper
import com.example.link2sdclone.util.PrivilegedShell
import com.example.link2sdclone.util.PrivilegedShell.Mode
import java.util.concurrent.TimeUnit

/**
 * شاشة الإعدادات. كل خيار ظاهر ومربوط: ما يمكن تنفيذه ينفَّذ، وما يحتاج Root أو Shizuku
 * يعرض سبب عدم توفره بدل أن يُخفى.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    private val sp get() = PreferenceManager.getDefaultSharedPreferences(requireContext())
    private var bypassAutoClear = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>("pref_upgrade_plus")?.setOnPreferenceClickListener {
            openPlusListing()
            true
        }

        findPreference<Preference>("pref_install_location")?.setOnPreferenceClickListener {
            val labels = resources.getStringArray(R.array.st2_install_locations)
            val values = arrayOf("auto", "internal", "external")
            val cur = values.indexOf(sp.getString(KEY_INSTALL_LOC, "auto")).coerceAtLeast(0)
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_install_location_title)
                .setSingleChoiceItems(labels, cur) { d, which ->
                    d.dismiss()
                    sp.edit().putString(KEY_INSTALL_LOC, values[which]).apply()
                    applyInstallLocation(which)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }

        findPreference<Preference>("pref_exclusion_list")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), CacheExcludeActivity::class.java))
            true
        }

        findPreference<Preference>("pref_auto_link_settings")?.setOnPreferenceClickListener {
            val keys = arrayOf("auto_link_apk", "auto_link_dex", "auto_link_lib")
            val labels = resources.getStringArray(R.array.st2_auto_link_items)
            val checked = BooleanArray(3) { sp.getBoolean(keys[it], false) }
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_auto_link_settings_title)
                .setMultiChoiceItems(labels, checked) { _, i, c -> checked[i] = c }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val e = sp.edit()
                    keys.forEachIndexed { i, k -> e.putBoolean(k, checked[i]) }
                    e.apply()
                    toast(R.string.st2_saved_needs_engine)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }

        findPreference<Preference>("pref_app_lock")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), LockSetupActivity::class.java))
            true
        }

        findPreference<Preference>("pref_about")?.setOnPreferenceClickListener {
            val ctx = requireContext()
            val ver = try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "-"
            } catch (e: Exception) { "-" }
            AlertDialog.Builder(ctx)
                .setTitle(R.string.pref_about_title)
                .setMessage(getString(R.string.st2_about_body, ver))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            true
        }

        findPreference<Preference>("pref_whats_new")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_whats_new_title)
                .setMessage(R.string.st2_whats_new_body)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            true
        }

        findPreference<Preference>("pref_send_email")?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:") }
            startActivity(Intent.createChooser(intent, null))
            true
        }

        // حجم تنبيه المسح (MB): حوار رقمي، يُقرأ من العامل الدوري.
        val sizePref = findPreference<Preference>("pref_clear_cache_notif_size")
        sizePref?.summary = "${sp.getInt(KEY_NOTIF_MB, 5)}MB"
        sizePref?.setOnPreferenceClickListener {
            val input = EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(sp.getInt(KEY_NOTIF_MB, 5).toString())
                setSelection(text.length)
            }
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_clear_cache_notif_size_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val mb = input.text.toString().toIntOrNull()?.coerceIn(1, 100000) ?: 5
                    sp.edit().putInt(KEY_NOTIF_MB, mb).apply()
                    sizePref?.summary = "${mb}MB"
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }

        // المسح التلقائي: يعمل بـ Root أو Shizuku. بلاهما يبقى الخيار ظاهرًا ويشرح السبب.
        findPreference<CheckBoxPreference>("pref_auto_clear_cache")?.setOnPreferenceChangeListener { pref, newValue ->
            val on = newValue as Boolean
            if (bypassAutoClear) {
                bypassAutoClear = false
                scheduleAutoClear(on)
                return@setOnPreferenceChangeListener true
            }
            if (!on) {
                scheduleAutoClear(false)
                return@setOnPreferenceChangeListener true
            }
            withMode { m ->
                when (m) {
                    Mode.ROOT, Mode.SHIZUKU -> {
                        bypassAutoClear = true
                        (pref as CheckBoxPreference).isChecked = true
                    }
                    Mode.SHIZUKU_NEEDS_PERMISSION -> {
                        PrivilegedShell.requestShizuku(5504)
                        toast(R.string.st2_shizuku_asked)
                    }
                    Mode.NONE -> showInfo(R.string.st2_auto_clear_title, R.string.st2_need_access)
                }
                Unit
            }
            false
        }

        // خيارات الربط والإقلاع: تُحفظ الآن وتُنفَّذ حين يكتمل محرك الربط. نخبر المستخدم بصراحة.
        listOf(
            "pref_auto_link", "pref_relink_lib_boot", "pref_relink_dex_boot",
            "pref_bind_dirs_notification", "pref_auto_link_notification", "pref_app2sd_notification"
        ).forEach { key ->
            findPreference<CheckBoxPreference>(key)?.setOnPreferenceChangeListener { _, v ->
                if (v == true) toast(R.string.st2_saved_needs_engine)
                true
            }
        }
        findPreference<CheckBoxPreference>("pref_clear_external_cache")?.setOnPreferenceChangeListener { _, v ->
            if (v == true) toast(R.string.st2_saved_ext_cache)
            true
        }

        // "auto" (null) = استخدم المتاح من Shizuku/Island، والاختيار هنا يثبّت وسيطًا واحدًا.
        findPreference<ListPreference>("pref_freeze_backend")?.setOnPreferenceChangeListener { _, newValue ->
            FreezeManager.preferredBackend = when (newValue as String) {
                "shizuku" -> FreezeBackend.SHIZUKU
                "island" -> FreezeBackend.ISLAND
                "root" -> FreezeBackend.ROOT
                else -> null
            }
            true
        }

        findPreference<ListPreference>("pref_appearance")?.setOnPreferenceChangeListener { _, _ ->
            LocaleHelper.applyTheme(requireContext())
            true
        }

        findPreference<ListPreference>("pref_language")?.setOnPreferenceChangeListener { _, newValue ->
            LocaleHelper.applyLanguageAndRecreate(requireActivity(), newValue as String)
            true
        }
    }

    private fun withMode(block: (Mode) -> Unit) {
        val act = activity ?: return
        Thread {
            val m = PrivilegedShell.detect()
            act.runOnUiThread { if (isAdded) block(m) }
        }.start()
    }

    private fun applyInstallLocation(code: Int) {
        withMode { m ->
            if (m == Mode.ROOT || m == Mode.SHIZUKU) {
                Thread {
                    val r = PrivilegedShell.run(m, "pm set-install-location $code")
                    activity?.runOnUiThread {
                        toast(if (r.ok) R.string.st2_loc_applied else R.string.st2_loc_failed)
                    }
                }.start()
            } else {
                toast(R.string.st2_loc_saved_only)
            }
        }
    }

    private fun scheduleAutoClear(on: Boolean) {
        val wm = WorkManager.getInstance(requireContext().applicationContext)
        if (on) {
            wm.enqueueUniquePeriodicWork(
                "auto_clear_cache",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AutoClearCacheWorker>(6, TimeUnit.HOURS).build()
            )
        } else {
            wm.cancelUniqueWork("auto_clear_cache")
        }
    }

    private fun openPlusListing() {
        val q = Uri.encode("Link2SD Plus")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$q&c=apps")))
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$q&c=apps")))
            } catch (e2: ActivityNotFoundException) {
                toast(R.string.appinfo_cannot_open)
            }
        }
    }

    private fun showInfo(titleRes: Int, msgRes: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle(titleRes)
            .setMessage(msgRes)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun toast(id: Int) {
        context?.let { Toast.makeText(it, id, Toast.LENGTH_LONG).show() }
    }

    private companion object {
        const val KEY_INSTALL_LOC = "install_location"
        const val KEY_NOTIF_MB = "clear_cache_notif_size_mb"
    }
}
