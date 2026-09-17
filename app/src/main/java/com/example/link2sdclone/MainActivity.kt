package com.example.link2sdclone

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.link2sdclone.freeze.FreezeBackend
import com.example.link2sdclone.freeze.FreezeManager
import com.example.link2sdclone.freeze.FreezeResult
import com.example.link2sdclone.model.AppEntry
import com.example.link2sdclone.ui.AppListAdapter
import com.example.link2sdclone.ui.DrawerActions
import com.example.link2sdclone.ui.OverflowActions
import com.example.link2sdclone.ui.StorageInfoActivity
import com.example.link2sdclone.ui.handleOverflowMenuClick
import com.example.link2sdclone.ui.inflateOverflowMenu
import com.example.link2sdclone.ui.setupDrawer
import com.example.link2sdclone.ui.showAppContextMenu
import com.example.link2sdclone.ui.showFilterDialog
import com.example.link2sdclone.ui.showSortDialog
import com.google.android.material.navigation.NavigationView
import java.io.File

class MainActivity : AppCompatActivity(), OverflowActions, DrawerActions {

    private lateinit var adapter: AppListAdapter
    private lateinit var headerCount: TextView
    private var allApps: List<AppEntry> = emptyList()
    private var currentFilterIndex = 5   // "في ذاكرة الهاتف" to match your screenshots
    private var currentSortIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Freeze feature: Shizuku reports its permission result through this
        // listener rather than (only) onRequestPermissionsResult.
        FreezeManager.registerShizukuListener(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val navView = findViewById<NavigationView>(R.id.nav_view)
        toolbar.setNavigationOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        setupDrawer(navView, drawerLayout, this)
        headerCount = findViewById(R.id.list_header_count)

        val recyclerView = findViewById<RecyclerView>(R.id.app_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AppListAdapter(
            items = emptyList(),
            onClick = { /* TODO: open app detail screen */ },
            onLongClick = { app, view -> showAppContextMenu(this, view, app) { actionId -> onContextAction(app, actionId) } },
            onFavoriteClick = { app -> toggleFavorite(app) }
        )
        recyclerView.adapter = adapter

        findViewById<ImageButton>(R.id.icon_filter).setOnClickListener {
            showFilterDialog(this, currentFilterIndex) { index ->
                currentFilterIndex = index
                applyFilterAndSort()
            }
        }
        findViewById<ImageButton>(R.id.icon_sort).setOnClickListener {
            showSortDialog(this, currentSortIndex) { index ->
                currentSortIndex = index
                applyFilterAndSort()
            }
        }

        loadInstalledApps()
    }

    override fun onDestroy() {
        FreezeManager.unregisterShizukuListener()
        super.onDestroy()
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        allApps = installed.map { info ->
            val apkFile = File(info.sourceDir)
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isFrozen = !info.enabled
            AppEntry(
                packageName = info.packageName,
                label = pm.getApplicationLabel(info).toString(),
                apkPath = info.sourceDir,
                icon = try { pm.getApplicationIcon(info) } catch (e: Exception) { null },
                apkSizeBytes = if (apkFile.exists()) apkFile.length() else 0L,
                dataSizeBytes = 0L,  // requires PackageStats / StorageStatsManager (API 26+) with usage-access
                cacheSizeBytes = 0L,
                isSystemApp = isSystem,
                isOnSdCard = info.sourceDir.contains("/mnt/") || info.sourceDir.contains("/storage/"),
                isFrozen = isFrozen
            )
        }.sortedBy { it.label.lowercase() }

        applyFilterAndSort()
    }

    private fun applyFilterAndSort() {
        var list = when (currentFilterIndex) {
            1 -> allApps.filter { it.isSystemApp }
            2 -> allApps.filter { !it.isSystemApp }
            3 -> allApps.filter { it.isOnSdCard }
            4 -> allApps.filter { it.isOnSdCard }
            5 -> allApps.filter { !it.isOnSdCard }
            6 -> allApps.filter { it.isFavorite }
            7 -> allApps.filter { it.isFrozen }
            else -> allApps
        }

        list = when (currentSortIndex) {
            1 -> list  // date: needs PackageInfo.firstInstallTime
            2 -> list.sortedByDescending { it.apkSizeBytes }
            11 -> list.sortedByDescending { it.totalSizeBytes }
            else -> list.sortedBy { it.label.lowercase() }
        }

        adapter.submitList(list)
        headerCount.text = getString(R.string.in_phone_memory_apps, list.size)
    }

    private fun toggleFavorite(app: AppEntry) {
        app.isFavorite = !app.isFavorite
        applyFilterAndSort()
    }

    // ---------------------------------------------------------------------
    // Long-press context menu actions
    // ---------------------------------------------------------------------

    private fun onContextAction(app: AppEntry, actionId: Int) {
        when (actionId) {
            R.id.ctx_move_sd -> {
                // TODO: real Link2SD-style move needs root (bind-mount) — without
                // root there's no public API to relocate another app's APK/data.
                Toast.makeText(this, "TODO: move ${app.packageName}", Toast.LENGTH_SHORT).show()
            }
            R.id.ctx_run -> {
                val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) startActivity(launchIntent)
                else Toast.makeText(this, "لا يمكن تشغيل هذا التطبيق", Toast.LENGTH_SHORT).show()
            }
            R.id.ctx_manage -> openAppInfoScreen(app.packageName)
            R.id.ctx_reinstall -> {
                // Re-triggers install from the same APK path (works for sideloaded APKs
                // still on disk; Play-installed apps should instead deep-link to the Store).
                try {
                    val apkUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", File(app.apkPath))
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(installIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.freeze_failed, Toast.LENGTH_SHORT).show()
                }
            }
            R.id.ctx_delete -> {
                val uninstallIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                startActivity(uninstallIntent)
            }
            R.id.ctx_freeze -> handleFreezeToggle(app)
            R.id.ctx_convert_system -> {
                // Requires root (mount /system rw + `pm` remount) — not possible via any public API.
                Toast.makeText(this, "يتطلب صلاحية Root", Toast.LENGTH_SHORT).show()
            }
            R.id.ctx_clear_data -> openAppInfoScreen(app.packageName) // no public API to clear another app's data
            R.id.ctx_clear_cache -> openAppInfoScreen(app.packageName) // same restriction as clear data
            R.id.ctx_view_play -> {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}")))
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}")))
                }
            }
            R.id.ctx_share -> {
                val text = "${app.label} - ${app.packageName}\nhttps://play.google.com/store/apps/details?id=${app.packageName}"
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }, app.label))
            }
            R.id.ctx_share_apk -> {
                try {
                    val apkUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", File(app.apkPath))
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.android.package-archive"
                        putExtra(Intent.EXTRA_STREAM, apkUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, app.label))
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.freeze_failed, Toast.LENGTH_SHORT).show()
                }
            }
            R.id.ctx_create_shortcut -> createLaunchShortcut(app)
        }
    }

    private fun openAppInfoScreen(packageName: String) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun createLaunchShortcut(app: AppEntry) {
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName) ?: run {
            Toast.makeText(this, "لا يمكن إنشاء اختصار لهذا التطبيق", Toast.LENGTH_SHORT).show()
            return
        }
        val icon = app.icon?.let { IconCompat.createWithBitmap(drawableToBitmap(it)) }
            ?: IconCompat.createWithResource(this, R.drawable.ic_android_default)
        val shortcut = ShortcutInfoCompat.Builder(this, app.packageName)
            .setShortLabel(app.label)
            .setIcon(icon)
            .setIntent(launchIntent.apply { action = Intent.ACTION_MAIN })
            .build()
        ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
    }

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): android.graphics.Bitmap {
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    // ---------------------------------------------------------------------
    // Freeze (Shizuku / Island)
    // ---------------------------------------------------------------------

    /** Package name we're mid-freeze-toggle for, needed once Island's
     *  onActivityResult comes back (it only gives us a result code, not the package). */
    private var pendingFreezeTarget: AppEntry? = null

    private fun handleFreezeToggle(app: AppEntry) {
        val backends = FreezeManager.availableBackends(this)
        when {
            backends.isEmpty() -> showNoFreezeBackendDialog()
            backends.size == 1 -> startFreeze(app, backends.first())
            else -> {
                // Both installed: honor a saved preference, else ask once.
                val saved = FreezeManager.preferredBackend
                if (saved != null && saved in backends) startFreeze(app, saved)
                else showChooseBackendDialog(app, backends)
            }
        }
    }

    private fun showNoFreezeBackendDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.freeze_no_backend_title)
            .setMessage(R.string.freeze_no_backend_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showChooseBackendDialog(app: AppEntry, backends: List<FreezeBackend>) {
        val labels = backends.map {
            when (it) {
                FreezeBackend.SHIZUKU -> getString(R.string.freeze_backend_shizuku)
                FreezeBackend.ISLAND -> getString(R.string.freeze_backend_island)
            }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.freeze_choose_backend_title)
            .setItems(labels) { _, which -> startFreeze(app, backends[which]) }
            .show()
    }

    private fun startFreeze(app: AppEntry, backend: FreezeBackend) {
        pendingFreezeTarget = app
        val wantFrozen = !app.isFrozen
        FreezeManager.setFrozen(this, backend, app.packageName, wantFrozen) { result ->
            runOnUiThread { onFreezeResult(app, wantFrozen, result) }
        }
    }

    private fun onFreezeResult(app: AppEntry, wasSettingFrozenTo: Boolean, result: FreezeResult) {
        when (result) {
            is FreezeResult.Success -> {
                app.isFrozen = wasSettingFrozenTo
                applyFilterAndSort()
                Toast.makeText(
                    this,
                    if (wasSettingFrozenTo) R.string.freeze_success_frozen else R.string.freeze_success_unfrozen,
                    Toast.LENGTH_SHORT
                ).show()
            }
            is FreezeResult.PermissionRequested -> { /* wait for callback */ }
            is FreezeResult.BackendNotAvailable -> showNoFreezeBackendDialog()
            is FreezeResult.Failed -> Toast.makeText(this, R.string.freeze_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        FreezeManager.onRequestPermissionsResult(requestCode, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val app = pendingFreezeTarget ?: return
        FreezeManager.onActivityResult(requestCode, resultCode) { result ->
            onFreezeResult(app, !app.isFrozen, result)
        }
    }

    // ---------------------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        inflateOverflowMenu(menu, this)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        handleOverflowMenuClick(item, this) || super.onOptionsItemSelected(item)

    // ---- OverflowActions ----
    override fun onSearch() { /* TODO: show SearchView over the toolbar */ }
    override fun onBatchSelect() { /* TODO: enable multi-select mode on the adapter */ }
    override fun onStorageInfo() {
        startActivity(Intent(this, StorageInfoActivity::class.java))
    }
    override fun onSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
    override fun onAbout() { /* TODO: show About dialog */ }

    // ---- DrawerActions ----
    override fun onAllApps() { currentFilterIndex = 0; applyFilterAndSort() }
    override fun onOnSdCard() { currentFilterIndex = 4; applyFilterAndSort() }
    override fun onOnPhone() { currentFilterIndex = 5; applyFilterAndSort() }
    override fun onFrozen() { currentFilterIndex = 7; applyFilterAndSort() }
    override fun onFavorites() { currentFilterIndex = 6; applyFilterAndSort() }
}
