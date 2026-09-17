package com.example.link2sdclone

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.link2sdclone.model.AppEntry
import com.example.link2sdclone.ui.AppListAdapter
import com.example.link2sdclone.ui.DrawerActions
import com.example.link2sdclone.ui.OverflowActions
import com.example.link2sdclone.ui.handleOverflowMenuClick
import com.example.link2sdclone.ui.inflateOverflowMenu
import com.example.link2sdclone.ui.showAppContextMenu
import com.example.link2sdclone.ui.showFilterDialog
import com.example.link2sdclone.ui.showSortDialog
import com.example.link2sdclone.ui.StorageInfoActivity
import com.example.link2sdclone.ui.setupDrawer
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
            onLongClick = { app, view -> showAppContextMenu(this, view) { actionId -> onContextAction(app, actionId) } },
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

    private fun loadInstalledApps() {
        val pm = packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        allApps = installed.map { info ->
            val apkFile = File(info.sourceDir)
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            AppEntry(
                packageName = info.packageName,
                label = pm.getApplicationLabel(info).toString(),
                apkPath = info.sourceDir,
                icon = try { pm.getApplicationIcon(info) } catch (e: Exception) { null },
                apkSizeBytes = if (apkFile.exists()) apkFile.length() else 0L,
                dataSizeBytes = 0L,  // requires PackageStats / StorageStatsManager (API 26+) with usage-access
                cacheSizeBytes = 0L,
                isSystemApp = isSystem,
                isOnSdCard = info.sourceDir.contains("/mnt/") || info.sourceDir.contains("/storage/")
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

    private fun onContextAction(app: AppEntry, actionId: Int) {
        // TODO: wire each id (R.id.ctx_reinstall, R.id.ctx_clean_dalvik_cache, ...)
        // to the real Link2SD-style operation for `app.packageName`.
    }

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
