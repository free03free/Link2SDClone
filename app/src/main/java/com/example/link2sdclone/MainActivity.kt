package com.example.link2sdclone

import android.Manifest
import com.example.link2sdclone.util.LocaleHelper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import com.example.link2sdclone.ui.AppDetailsActivity
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
import com.example.link2sdclone.storage.StorageStatsHelper
import com.google.android.material.navigation.NavigationView
import java.io.File

class MainActivity : AppCompatActivity(), OverflowActions, DrawerActions {

    private lateinit var adapter: AppListAdapter
    private lateinit var headerCount: TextView
    private lateinit var toolbar: Toolbar
    private lateinit var toolbarSelection: Toolbar
    private lateinit var selectionCountText: TextView
    private lateinit var toolbarSearch: Toolbar
    private lateinit var searchInput: EditText
    private var allApps: List<AppEntry> = emptyList()
    private var currentFilterIndex = 5
    private var currentSortIndex = 0

    private var isSelectionMode = false
    private val selectedPackages = mutableSetOf<String>()

    private var isSearchMode = false
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        FreezeManager.registerShizukuListener(this)
        requestNotificationPermissionIfNeeded()

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        toolbarSelection = findViewById(R.id.toolbar_selection)
        selectionCountText = findViewById(R.id.selection_count)
        toolbarSelection.setNavigationOnClickListener { exitSelectionMode() }
        findViewById<ImageButton>(R.id.icon_select_all).setOnClickListener { selectAll() }
        findViewById<ImageButton>(R.id.icon_select_none).setOnClickListener { selectNone() }
        findViewById<ImageButton>(R.id.icon_selection_overflow).setOnClickListener { showBatchActionsMenu(it) }

        toolbarSearch = findViewById(R.id.toolbar_search)
        searchInput = findViewById(R.id.search_input)
        toolbarSearch.setNavigationOnClickListener { exitSearchMode() }
        findViewById<ImageButton>(R.id.icon_search_clear).setOnClickListener { searchInput.setText("") }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                applyFilterAndSort()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val navView = findViewById<NavigationView>(R.id.nav_view)
        toolbar.setNavigationOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        setupDrawer(navView, drawerLayout, this)
        updateDrawerFreezeLabel() // يعكس اللقطة المحفوظة سابقًا فورًا عند فتح التطبيق
        headerCount = findViewById(R.id.list_header_count)

        val recyclerView = findViewById<RecyclerView>(R.id.app_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AppListAdapter(
            items = emptyList(),
            onClick = { app -> openAppDetails(app) },
            onLongClick = { app, view -> showAppContextMenu(this, view, app) { actionId -> onContextAction(app, actionId) } },
            onFavoriteClick = { app -> toggleFavorite(app) },
            onSelectToggle = { app -> toggleSelection(app) }
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

    override fun onBackPressed() {
        when {
            isSelectionMode -> exitSelectionMode()
            isSearchMode -> exitSearchMode()
            else -> super.onBackPressed()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
            }
        }
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        val hasUsageAccess = StorageStatsHelper.hasUsageAccess(this)

        allApps = installed.map { info ->
            val apkFile = File(info.sourceDir)
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isFrozen = !info.enabled
            val pkgInfo = try { pm.getPackageInfo(info.packageName, 0) } catch (e: Exception) { null }
            val realSizes = if (hasUsageAccess) StorageStatsHelper.queryRealSizes(this, info.uid) else null
            AppEntry(
                packageName = info.packageName,
                label = pm.getApplicationLabel(info).toString(),
                apkPath = info.sourceDir,
                icon = try { pm.getApplicationIcon(info) } catch (e: Exception) { null },
                uid = info.uid,
                apkSizeBytes = realSizes?.appBytes ?: (if (apkFile.exists()) apkFile.length() else 0L),
                dataSizeBytes = realSizes?.dataBytes ?: 0L,
                cacheSizeBytes = realSizes?.cacheBytes ?: 0L,
                hasRealSizes = realSizes != null,
                isSystemApp = isSystem,
                isOnSdCard = info.sourceDir.contains("/mnt/") || info.sourceDir.contains("/storage/"),
                isFrozen = isFrozen,
                firstInstallTime = pkgInfo?.firstInstallTime ?: 0L,
                lastUpdateTime = pkgInfo?.lastUpdateTime ?: 0L
            )
        }.sortedBy { it.label.lowercase() }

        applyFilterAndSort()
    }

    private fun applyFilterAndSort() {
        // الترتيب يطابق filter_options الحقيقي بـ arrays_filter.xml (11 عنصر):
        // 0 الكل,1 نظام,2 مستخدم,3 مرتبط,4 على SD,5 على الهاتف,
        // 6 مفضلة,7 مجمّد,8 قابل للنقل,9 محدّث,10 مشفّر
        if (currentFilterIndex == 3 || currentFilterIndex == 10) {
            // "مرتبط" و"مشفّر": التطبيق ما عنده نظام ربط حقيقي ولا وصول لحالة
            // التشفير بدون Root، فما فيه فلترة صحيحة نسويها -- بدل ما نعرض
            // نتيجة غلط، منبّه المستخدم ونرجع لعرض الكل.
            Toast.makeText(this, "هذا الفلتر يحتاج صلاحية Root وغير مدعوم حاليًا", Toast.LENGTH_SHORT).show()
            currentFilterIndex = 0
        }
        var list = when (currentFilterIndex) {
            1 -> allApps.filter { it.isSystemApp }
            2 -> allApps.filter { !it.isSystemApp }
            4 -> allApps.filter { it.isOnSdCard }
            5 -> allApps.filter { !it.isOnSdCard }
            6 -> allApps.filter { it.isFavorite }
            7 -> allApps.filter { it.isFrozen }
            8 -> allApps.filter { !it.isSystemApp } // تقريبي: قابل للنقل = تطبيق مستخدم عادةً
            9 -> allApps.filter { it.isRecentlyUpdated }
            else -> allApps
        }

        if (searchQuery.isNotBlank()) {
            list = list.filter { it.label.contains(searchQuery, ignoreCase = true) }
        }

        // الترتيب يطابق sort_options الحقيقي بـ arrays_filter.xml (12 عنصر):
        // 0 الاسم,1 التاريخ,2 apk,3 lib,4 dex,5 بيانات(إجمالي),6 بيانات(هاتف),
        // 7 بيانات(خارجي),8 كاش,9 obb,10 apk+dex+lib,11 الإجمالي
        //
        // ملاحظة: StorageStatsManager يرجّع apkSizeBytes كرقم واحد مجمّع
        // (apk+dex+lib سوا) وdataBytes كرقم واحد مجمّع (بدون تفريق هاتف/خارجي)،
        // فما فيه طريقة رسمية بدون Root نفرّق فيها lib عن dex عن apk، ولا
        // بيانات الهاتف عن الخارجية، ولا OBB. لهيك كل الخيارات المرتبطة
        // بنفس الفئة بترجع لنفس القيمة الحقيقية الأقرب بدل ما تتجاهل الفرز.
        list = when (currentSortIndex) {
            1 -> list.sortedByDescending { maxOf(it.firstInstallTime, it.lastUpdateTime) }
            2, 3, 4, 10 -> list.sortedByDescending { it.apkSizeBytes }   // apk / lib / dex / apk+dex+lib
            5, 6, 7 -> list.sortedByDescending { it.dataSizeBytes }      // بيانات إجمالي / هاتف / خارجي
            8 -> list.sortedByDescending { it.cacheSizeBytes }
            9 -> list                                                    // OBB: غير متاح، نسيب الترتيب الحالي
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

    private fun openAppDetails(app: AppEntry) {
        startActivity(Intent(this, AppDetailsActivity::class.java).apply {
            putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, app.packageName)
        })
    }

    // ---------------------------------------------------------------------
    // Search mode
    // ---------------------------------------------------------------------

    private fun enterSearchMode() {
        isSearchMode = true
        toolbar.visibility = View.GONE
        toolbarSearch.visibility = View.VISIBLE
        searchInput.requestFocus()
    }

    private fun exitSearchMode() {
        isSearchMode = false
        searchQuery = ""
        searchInput.setText("")
        toolbarSearch.visibility = View.GONE
        toolbar.visibility = View.VISIBLE
        applyFilterAndSort()
    }

    // ---------------------------------------------------------------------
    // Selection (batch) mode
    // ---------------------------------------------------------------------

    private fun enterSelectionMode(initialApp: AppEntry? = null) {
        isSelectionMode = true
        selectedPackages.clear()
        initialApp?.let { selectedPackages.add(it.packageName) }
        adapter.setSelectionMode(true)
        adapter.setSelectedPackages(selectedPackages.toSet())
        updateSelectionUi()
        toolbar.visibility = View.GONE
        toolbarSelection.visibility = View.VISIBLE
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedPackages.clear()
        adapter.setSelectionMode(false)
        adapter.setSelectedPackages(emptySet())
        toolbarSelection.visibility = View.GONE
        toolbar.visibility = View.VISIBLE
    }

    private fun toggleSelection(app: AppEntry) {
        if (selectedPackages.contains(app.packageName)) {
            selectedPackages.remove(app.packageName)
        } else {
            selectedPackages.add(app.packageName)
        }
        adapter.setSelectedPackages(selectedPackages.toSet())
        updateSelectionUi()
    }

    private fun selectAll() {
        selectedPackages.clear()
        selectedPackages.addAll(adapter.currentItems().map { it.packageName })
        adapter.setSelectedPackages(selectedPackages.toSet())
        updateSelectionUi()
    }

    private fun selectNone() {
        selectedPackages.clear()
        adapter.setSelectedPackages(emptySet())
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        selectionCountText.text = selectedPackages.size.toString()
    }

    private fun showBatchActionsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_batch_actions, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.batch_freeze -> { batchSetFrozen(true); true }
                R.id.batch_unfreeze -> { batchSetFrozen(false); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun batchSetFrozen(target: Boolean) {
        if (selectedPackages.isEmpty()) {
            Toast.makeText(this, R.string.batch_no_selection, Toast.LENGTH_SHORT).show()
            return
        }
        val backends = FreezeManager.availableBackends(this)
        if (backends.isEmpty()) {
            showNoFreezeBackendDialog()
            return
        }
        val backend = FreezeManager.preferredBackend?.takeIf { it in backends } ?: backends.first()
        val targets = allApps.filter { it.packageName in selectedPackages }
        var remaining = targets.size
        if (remaining == 0) return

        targets.forEach { app ->
            FreezeManager.setFrozen(this, backend, app.packageName, target) { result ->
                runOnUiThread {
                    if (result is FreezeResult.Success) app.isFrozen = target
                    remaining--
                    if (remaining == 0) {
                        applyFilterAndSort()
                        exitSelectionMode()
                        Toast.makeText(this, R.string.batch_action_done, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Freeze snapshot: single dynamic button. First press saves which apps
    // are currently frozen and unfreezes all of them; second press
    // re-freezes exactly that saved set and clears the snapshot. Never
    // touches any app that wasn't already frozen by the user beforehand.
    // ---------------------------------------------------------------------

    private fun onFreezeSnapshotToggleAction() {
        val backends = FreezeManager.availableBackends(this)
        if (backends.isEmpty()) { showNoFreezeBackendDialog(); return }
        val backend = FreezeManager.preferredBackend?.takeIf { it in backends } ?: backends.first()

        if (com.example.link2sdclone.freeze.FreezeSnapshotManager.hasSnapshot(this)) {
            val saved = com.example.link2sdclone.freeze.FreezeSnapshotManager.get(this)
            val targets = allApps.filter { it.packageName in saved }
            if (targets.isEmpty()) {
                com.example.link2sdclone.freeze.FreezeSnapshotManager.clear(this)
                updateDrawerFreezeLabel()
                return
            }
            var remaining = targets.size
            targets.forEach { app ->
                FreezeManager.setFrozen(this, backend, app.packageName, true) { result ->
                    runOnUiThread {
                        if (result is FreezeResult.Success) app.isFrozen = true
                        remaining--
                        if (remaining == 0) {
                            com.example.link2sdclone.freeze.FreezeSnapshotManager.clear(this)
                            applyFilterAndSort()
                            updateDrawerFreezeLabel()
                            Toast.makeText(this, R.string.freeze_snapshot_restored, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } else {
            val frozenNow = allApps.filter { it.isFrozen }.map { it.packageName }.toSet()
            if (frozenNow.isEmpty()) {
                Toast.makeText(this, R.string.freeze_snapshot_nothing_frozen, Toast.LENGTH_SHORT).show()
                return
            }
            com.example.link2sdclone.freeze.FreezeSnapshotManager.save(this, frozenNow)
            val targets = allApps.filter { it.packageName in frozenNow }
            var remaining = targets.size
            targets.forEach { app ->
                FreezeManager.setFrozen(this, backend, app.packageName, false) { result ->
                    runOnUiThread {
                        if (result is FreezeResult.Success) app.isFrozen = false
                        remaining--
                        if (remaining == 0) {
                            applyFilterAndSort()
                            updateDrawerFreezeLabel()
                            Toast.makeText(this, R.string.freeze_snapshot_saved, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    /** Call after setupDrawer(...) in onCreate, and this function updates
     *  itself after every snapshot action above -- no other wiring needed. */
    private fun updateDrawerFreezeLabel() {
        val navView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.nav_view) ?: return
        val item = navView.menu.findItem(R.id.nav_freeze_snapshot) ?: return
        item.title = if (com.example.link2sdclone.freeze.FreezeSnapshotManager.hasSnapshot(this))
            getString(R.string.nav_freeze_snapshot_restore)
        else
            getString(R.string.nav_freeze_snapshot_save)
    }

    private fun onGroupsAction() {
        startActivity(android.content.Intent(this, com.example.link2sdclone.groups.GroupsActivity::class.java))
    }

    // ---------------------------------------------------------------------
    // Long-press context menu actions
    // ---------------------------------------------------------------------

    private fun onContextAction(app: AppEntry, actionId: Int) {
        when (actionId) {
            R.id.ctx_move_sd -> {
                Toast.makeText(this, "TODO: move ${app.packageName}", Toast.LENGTH_SHORT).show()
            }
            R.id.ctx_run -> {
                val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) startActivity(launchIntent)
                else Toast.makeText(this, "لا يمكن تشغيل هذا التطبيق", Toast.LENGTH_SHORT).show()
            }
            R.id.ctx_manage -> openAppInfoScreen(app.packageName)
            R.id.ctx_reinstall -> {
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
                Toast.makeText(this, "يتطلب صلاحية Root", Toast.LENGTH_SHORT).show()
            }
            R.id.ctx_clear_data -> openAppInfoScreen(app.packageName)
            R.id.ctx_clear_cache -> openAppInfoScreen(app.packageName)
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

    private var pendingFreezeTarget: AppEntry? = null

    private fun handleFreezeToggle(app: AppEntry) {
        val backends = FreezeManager.availableBackends(this)
        when {
            backends.isEmpty() -> showNoFreezeBackendDialog()
            backends.size == 1 -> startFreeze(app, backends.first())
            else -> {
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
    override fun onSearch() { enterSearchMode() }
    override fun onBatchSelect() { enterSelectionMode() }
    override fun onStorageInfo() {
        startActivity(Intent(this, StorageInfoActivity::class.java))
    }
    override fun onSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
    override fun onAbout() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) { "1.0" }
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage(getString(R.string.about_message, versionName))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---- DrawerActions ----
    override fun onAllApps() { currentFilterIndex = 0; applyFilterAndSort() }
    override fun onOnSdCard() { currentFilterIndex = 4; applyFilterAndSort() }
    override fun onOnPhone() { currentFilterIndex = 5; applyFilterAndSort() }
    override fun onFrozen() { currentFilterIndex = 7; applyFilterAndSort() }
    override fun onFavorites() { currentFilterIndex = 6; applyFilterAndSort() }
    override fun onFreezeSnapshotToggle() = onFreezeSnapshotToggleAction()
    override fun onGroups() = onGroupsAction()
}
