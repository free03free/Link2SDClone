package com.example.link2sdclone.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.link2sdclone.R
import com.example.link2sdclone.util.FavoritesManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppDetailsActivity : AppCompatActivity() {

    private lateinit var packageNameArg: String
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_details)

        packageNameArg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run { finish(); return }

        toolbar = findViewById(R.id.toolbar_details)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.navigationIcon?.mutate()?.setTint(Color.WHITE)
        toolbar.inflateMenu(R.menu.menu_app_details)
        toolbar.overflowIcon = ContextCompat.getDrawable(this, R.drawable.ic_more_vert)
            ?.mutate()?.apply { setTint(Color.WHITE) }
        toolbar.menu.findItem(R.id.details_system_info)?.icon =
            ContextCompat.getDrawable(this, R.drawable.ic_info_outline)
        for (i in 0 until toolbar.menu.size()) {
            toolbar.menu.getItem(i).icon?.mutate()?.setTint(Color.WHITE)
        }
        toolbar.setOnMenuItemClickListener { onDetailsMenuItem(it.itemId) }
        toolbar.setOnClickListener { }
        updateFavoriteIcon()
        updateFreezeTitle()
        bindHeader()

        val pager = findViewById<ViewPager2>(R.id.details_pager)
        val tabs = findViewById<TabLayout>(R.id.details_tabs)
        pager.adapter = DetailsPagerAdapter(this, packageNameArg)

        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = if (position == 0) getString(R.string.tab_storage) else getString(R.string.tab_files)
        }.attach()

        (tabs.getChildAt(0) as? android.widget.LinearLayout)?.apply {
            showDividers = android.widget.LinearLayout.SHOW_DIVIDER_MIDDLE
            dividerDrawable = ContextCompat.getDrawable(this@AppDetailsActivity, R.drawable.divider_tab)
            dividerPadding = (10 * resources.displayMetrics.density).toInt()
        }
    }

    private fun bindHeader() {
        val pm = packageManager
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
        val nameView = findViewById<TextView>(R.id.details_name)
        findViewById<TextView>(R.id.details_package).text = packageNameArg
        try {
            val info = pm.getApplicationInfo(packageNameArg, flags)
            nameView.text = pm.getApplicationLabel(info)
            findViewById<ImageView>(R.id.details_icon).setImageDrawable(pm.getApplicationIcon(info))
        } catch (e: Exception) {
            nameView.text = packageNameArg
        }
        try {
            val pi = pm.getPackageInfo(packageNameArg, flags)
            findViewById<TextView>(R.id.details_version).text = getString(
                R.string.appinfo_version, pi.versionName ?: "-", PackageInfoCompat.getLongVersionCode(pi)
            )
            val fmt = SimpleDateFormat("HH:mm:ss dd-MM-yyyy", Locale.getDefault())
            findViewById<TextView>(R.id.details_date).text =
                getString(R.string.appinfo_date, "\u202A" + fmt.format(Date(pi.lastUpdateTime)) + "\u202C")
        } catch (e: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        updateFavoriteIcon()
        updateFreezeTitle()
    }

    private fun updateFreezeTitle() {
        val frozen = com.example.link2sdclone.groups.GroupsManager.isPackageFrozen(this, packageNameArg)
        toolbar.menu.findItem(R.id.details_freeze)?.title =
            getString(if (frozen) R.string.ctx_unfreeze else R.string.ctx_freeze)
    }

    private fun updateFavoriteIcon() {
        val fav = FavoritesManager.isFavorite(this, packageNameArg)
        toolbar.menu.findItem(R.id.details_favorite)?.icon = ContextCompat.getDrawable(
            this, if (fav) R.drawable.ic_star else R.drawable.ic_star_outline
        )?.mutate()?.apply { setTint(Color.WHITE) }
    }

    private fun onDetailsMenuItem(id: Int): Boolean = when (id) {
        R.id.details_favorite -> {
            val now = !FavoritesManager.isFavorite(this, packageNameArg)
            FavoritesManager.set(this, packageNameArg, now)
            updateFavoriteIcon()
            true
        }
        R.id.details_system_info -> {
            openIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageNameArg, null)))
            true
        }
        R.id.details_run -> {
            val launch = packageManager.getLaunchIntentForPackage(packageNameArg)
            if (launch == null) Toast.makeText(this, R.string.appinfo_cannot_run, Toast.LENGTH_SHORT).show()
            else openIntent(launch)
            true
        }
        R.id.details_play -> {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageNameArg")))
            } catch (e: Exception) {
                openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageNameArg")))
            }
            true
        }
        R.id.details_reinstall -> {
            try {
                val path = packageManager.getApplicationInfo(packageNameArg, PackageManager.MATCH_UNINSTALLED_PACKAGES).sourceDir
                val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", java.io.File(path))
                openIntent(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            } catch (e: Exception) {
                Toast.makeText(this, R.string.freeze_failed, Toast.LENGTH_SHORT).show()
            }
            true
        }
        R.id.details_delete -> {
            openIntent(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageNameArg")))
            true
        }
        R.id.details_freeze -> {
            handleFreezeToggleForCurrent()
            true
        }
        R.id.details_convert_system -> {
            Toast.makeText(this, "يتطلب صلاحية Root", Toast.LENGTH_SHORT).show()
            true
        }
        R.id.details_clear_data -> {
            openIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageNameArg, null)))
            true
        }
        R.id.details_clear_cache -> {
            openIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageNameArg, null)))
            true
        }
        R.id.details_share -> {
            val label = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageNameArg, 0))
            } catch (e: Exception) { packageNameArg }
            val text = "$label - $packageNameArg\nhttps://play.google.com/store/apps/details?id=$packageNameArg"
            openIntent(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, label))
            true
        }
        R.id.details_share_apk -> {
            try {
                val path = packageManager.getApplicationInfo(packageNameArg, PackageManager.MATCH_UNINSTALLED_PACKAGES).sourceDir
                val label = try {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageNameArg, 0))
                } catch (e: Exception) { packageNameArg }
                val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", java.io.File(path))
                openIntent(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.android.package-archive"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, label))
            } catch (e: Exception) {
                Toast.makeText(this, R.string.freeze_failed, Toast.LENGTH_SHORT).show()
            }
            true
        }
        R.id.details_create_shortcut -> {
            createLaunchShortcutForCurrent()
            true
        }
        else -> false
    }

    private fun openIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.appinfo_cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------------
    // Freeze (نسخة مبسطة لتطبيق واحد، بلا الحاجة لـ pendingFreezeTarget الخاص بالقوائم المتعددة)
    // ---------------------------------------------------------------------
    private fun handleFreezeToggleForCurrent() {
        val backends = com.example.link2sdclone.freeze.FreezeManager.availableBackends(this)
        if (backends.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setTitle(R.string.freeze_no_backend_title)
                .setMessage(R.string.freeze_no_backend_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val backend = com.example.link2sdclone.freeze.FreezeManager.preferredBackend?.takeIf { it in backends }
            ?: backends.first()
        val wantFrozen = !com.example.link2sdclone.groups.GroupsManager.isPackageFrozen(this, packageNameArg)
        Toast.makeText(this, R.string.rt_running, Toast.LENGTH_SHORT).show()
        com.example.link2sdclone.freeze.FreezeManager.setFrozen(this, backend, packageNameArg, wantFrozen) { result ->
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                when (result) {
                    is com.example.link2sdclone.freeze.FreezeResult.Success -> {
                        updateFreezeTitle()
                        Toast.makeText(
                            this,
                            if (wantFrozen) R.string.freeze_success_frozen else R.string.freeze_success_unfrozen,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is com.example.link2sdclone.freeze.FreezeResult.BackendNotAvailable -> {
                        android.app.AlertDialog.Builder(this)
                            .setTitle(R.string.freeze_no_backend_title)
                            .setMessage(R.string.freeze_no_backend_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                    is com.example.link2sdclone.freeze.FreezeResult.PermissionRequested -> { /* ننتظر رد الصلاحية */ }
                    else -> Toast.makeText(this, R.string.freeze_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // إنشاء اختصار (نسخة مبنية من packageNameArg مباشرة)
    // ---------------------------------------------------------------------
    private fun createLaunchShortcutForCurrent() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageNameArg) ?: run {
            Toast.makeText(this, "لا يمكن إنشاء اختصار لهذا التطبيق", Toast.LENGTH_SHORT).show()
            return
        }
        val appIcon = try { packageManager.getApplicationIcon(packageNameArg) } catch (e: Exception) { null }
        val icon = appIcon?.let {
            androidx.core.graphics.drawable.IconCompat.createWithBitmap(drawableToBitmapLocal(it))
        } ?: androidx.core.graphics.drawable.IconCompat.createWithResource(this, R.drawable.ic_android_default)
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageNameArg, 0))
        } catch (e: Exception) { packageNameArg }
        val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(this, packageNameArg)
            .setShortLabel(label)
            .setIcon(icon)
            .setIntent(launchIntent.apply { action = Intent.ACTION_MAIN })
            .build()
        androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
    }

    private fun drawableToBitmapLocal(drawable: android.graphics.drawable.Drawable): android.graphics.Bitmap {
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private class DetailsPagerAdapter(activity: FragmentActivity, private val packageName: String) :
        FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2
        override fun createFragment(position: Int): Fragment {
            val bundle = Bundle().apply { putString(EXTRA_PACKAGE_NAME, packageName) }
            val fragment = if (position == 0) StorageFragment() else FilesFragment()
            fragment.arguments = bundle
            return fragment
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }
}
