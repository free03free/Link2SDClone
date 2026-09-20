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
    // تُحفظ في التخزين فتبقى بعد إغلاق التطبيق
    private val uiPrefs by lazy { getSharedPreferences("ui_state", MODE_PRIVATE) }
    private var currentFilterIndex: Int
        get() = uiPrefs.getInt("main_filter_index", 5)
        set(value) { uiPrefs.edit().putInt("main_filter_index", value).apply() }
    private var currentSortIndex: Int
        get() = uiPrefs.getInt("main_sort_index", 0)
        set(value) { uiPrefs.edit().putInt("main_sort_index", value).apply() }

    private var isSelectionMode = false
    private val selectedPackages = mutableSetOf<String>()

    private var isSearchMode = false
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        FreezeManager.registerShizukuListener(this)
        com.example.link2sdclone.ui.applySavedFreezeBackend(this)
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
        // navHeaderInset: ارتفاع الترويسة = 48dp + ارتفاع شريط الحالة الفعلي
        navView.post {
            val header = navView.getHeaderView(0)
            val top = androidx.core.view.ViewCompat.getRootWindowInsets(navView)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top ?: 0
            val base = (48 * resources.displayMetrics.density).toInt()
            header.layoutParams = header.layoutParams.apply { height = base + top }
        }
        headerCount = findViewById(R.id.list_header_count)

        val recyclerView = findViewById<RecyclerView>(R.id.app_list)
        com.example.link2sdclone.EdgeToEdge.apply(this, toolbar, recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.addItemDecoration(
            androidx.recyclerview.widget.DividerItemDecoration(
                this, androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
            ).apply {
                androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.divider_thin)
                    ?.let { setDrawable(it) }
            }
        )
        adapter = AppListAdapter(
            items = emptyList(),
            onClick = { app -> openAppDetails(app) },
            onLongClick = { app, view -> showAppContextMenu(this, view, app) { actionId -> onContextAction(app, actionId) } },
            onFavoriteClick = { app -> toggleFavorite(app) },
            onSelectToggle = { app -> toggleSelection(app) }
        )
        recyclerView.adapter = adapter

        findViewById<ImageButton>(R.id.icon_filter).setOnClickListener {
            showFilterDialog(this, it, currentFilterIndex) { index ->
                currentFilterIndex = index
                applyFilterAndSort()
            }
        }
        findViewById<ImageButton>(R.id.icon_refresh).setOnClickListener {
            loadInstalledApps()
            Toast.makeText(
                this,
                "تم التحديث: ${allApps.size} تطبيق، ${allApps.count { it.isFrozen }} مجمّد",
                Toast.LENGTH_SHORT
            ).show()
        }

        findViewById<ImageButton>(R.id.icon_sort).setOnClickListener {
            showSortDialog(this, it, currentSortIndex) { index ->
                currentSortIndex = index
                applyFilterAndSort()
            }
        }

        lifecycle.addObserver(resumeObserver)
        loadInstalledApps()
    }

    // إعادة تحميل القائمة عند طلبها (أدوات الروت / بعد الحذف)، وفحص Root لإتاحته وسيطًا للتجميد.
    private val resumeObserver = object : androidx.lifecycle.LifecycleEventObserver {
        override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: androidx.lifecycle.Lifecycle.Event) {
            if (event != androidx.lifecycle.Lifecycle.Event.ON_RESUME) return
            if (com.example.link2sdclone.util.ReloadFlag.consume()) loadInstalledApps()
            if (com.example.link2sdclone.util.PrivilegedShell.rootKnown != true) {
                com.example.link2sdclone.util.PrivilegedShell.warmUp()
            }
        }
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
        // MATCH_DISABLED_COMPONENTS is required here for the exact same reason
        // as in GroupAppsActivity: without it, PackageManager silently drops
        // disabled (frozen) apps, so the "مجمّدة" (frozen) filter in the nav
        // drawer always showed an empty list even when apps were genuinely
        // frozen at the system level.
        val installed = pm.getInstalledApplications(
            PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS or
                (if (com.example.link2sdclone.groups.GroupsManager.showHiddenFrozen(this)) PackageManager.MATCH_UNINSTALLED_PACKAGES else 0)
        ).filter { (it.flags and ApplicationInfo.FLAG_INSTALLED) != 0 ||
            com.example.link2sdclone.groups.GroupsManager.isPackageFrozen(this, it.packageName) }

        val hasUsageAccess = StorageStatsHelper.hasUsageAccess(this)

        allApps = installed.map { info ->
            val apkFile = File(info.sourceDir)
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isFrozen = com.example.link2sdclone.groups.GroupsManager.isPackageFrozen(this, info.packageName)
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
                isUpdatedSystem = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                isOnSdCard = info.sourceDir.contains("/mnt/") || info.sourceDir.contains("/storage/"),
                isFrozen = isFrozen,
                isFavorite = com.example.link2sdclone.util.FavoritesManager.isFavorite(this, info.packageName),
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
        if (currentFilterIndex == 10) {
            // "مرتبط" و"مشفّر": التطبيق ما عنده نظام ربط حقيقي ولا وصول لحالة
            // التشفير بدون Root، فما فيه فلترة صحيحة نسويها -- بدل ما نعرض
            // نتيجة غلط، منبّه المستخدم ونرجع لعرض الكل.
            Toast.makeText(this, "هذا الفلتر يحتاج صلاحية Root وغير مدعوم حاليًا", Toast.LENGTH_SHORT).show()
            currentFilterIndex = 0
        }
        val linkedSet = com.example.link2sdclone.util.LinkEngine.linkedPackages(this)
        var list = when (currentFilterIndex) {
            3 -> allApps.filter { it.packageName in linkedSet }
            1 -> allApps.filter { it.isSystemApp }
            2 -> allApps.filter { !it.isSystemApp }
            4 -> allApps.filter { it.isOnSdCard || it.packageName in linkedSet }
            5 -> allApps.filter { !it.isOnSdCard && it.packageName !in linkedSet }
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

    override fun onResume() {
        super.onResume()
        // النجمة قد تتغير من شاشة معلومات التطبيق
        allApps.forEach { it.isFavorite = com.example.link2sdclone.util.FavoritesManager.isFavorite(this, it.packageName) }
        applyFilterAndSort()
    }

    private fun toggleFavorite(app: AppEntry) {
        app.isFavorite = !app.isFavorite
        com.example.link2sdclone.util.FavoritesManager.set(this, app.packageName, app.isFavorite)
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
        val titles = com.example.link2sdclone.ui.BatchActions.labels(this)
        com.example.link2sdclone.ui.showActionPopup(this, anchor, titles) { index ->
            if (selectedPackages.isEmpty()) {
                Toast.makeText(this, R.string.batch_no_selection, Toast.LENGTH_SHORT).show()
            } else if (index == 4) {
                batchSetFrozen(true)
            } else if (index == 5) {
                batchSetFrozen(false)
            } else {
                val targets = allApps.filter { it.packageName in selectedPackages }
                com.example.link2sdclone.ui.BatchActions.execute(this, index, targets) { exitSelectionMode(); loadInstalledApps() }
            }
        }
    }

    private fun batchSetFrozen(target: Boolean) {
        val backends = FreezeManager.availableBackends(this)
        if (backends.isEmpty()) {
            showNoFreezeBackendDialog()
            return
        }
        val backend = FreezeManager.preferredBackend?.takeIf { it in backends } ?: backends.first()
        val targets = allApps.filter { it.packageName in selectedPackages }
        if (targets.isEmpty()) return
        val total = targets.size
        var remaining = total
        var okCount = 0

        targets.forEach { app ->
            FreezeManager.setFrozen(this, backend, app.packageName, target) { result ->
                // طلب الصلاحية ليس نتيجة نهائية: ننتظر النتيجة الحقيقية.
                if (result is FreezeResult.PermissionRequested) return@setFrozen
                runOnUiThread {
                    if (result is FreezeResult.Success) {
                        app.isFrozen = target
                        okCount++
                    }
                    remaining--
                    if (remaining == 0) {
                        applyFilterAndSort()
                        exitSelectionMode()
                        Toast.makeText(this, getString(R.string.st3_batch_result, okCount, total), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
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
                com.example.link2sdclone.ui.BatchActions.execute(this, 2, listOf(app)) { loadInstalledApps() }
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
                val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${app.packageName}"))
                    .apply { putExtra(Intent.EXTRA_RETURN_RESULT, true) }
                Toast.makeText(this, "جاري الحذف...", Toast.LENGTH_SHORT).show()
                startActivityForResult(uninstallIntent, 7301)
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
        val auto = FreezeManager.preferredBackend?.takeIf { it in backends } ?: backends.firstOrNull() ?: return
        startFreeze(app, auto)
    }

    @Suppress("unused")
    private fun showChooseBackendDialogManual(app: AppEntry, backends: List<FreezeBackend>) {
        val labels = backends.map {
            when (it) {
                FreezeBackend.SHIZUKU -> getString(R.string.freeze_backend_shizuku)
                FreezeBackend.ISLAND -> getString(R.string.freeze_backend_island)
                FreezeBackend.ROOT -> getString(R.string.freeze_backend_root)
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
        Toast.makeText(this, R.string.rt_running, Toast.LENGTH_SHORT).show()
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
        if (requestCode == com.example.link2sdclone.ui.BatchActions.REQ_QUEUE) {
            com.example.link2sdclone.ui.BatchActions.nextInQueue(this)
            return
        }
        if (requestCode == 7301) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "تم الحذف", Toast.LENGTH_SHORT).show()
                loadInstalledApps()
            } else {
                Toast.makeText(this, "لم يتم الحذف", Toast.LENGTH_SHORT).show()
            }
            return
        }
        FreezeManager.onActivityResult(requestCode, resultCode) { result ->
            val app = pendingFreezeTarget
            if (app != null) onFreezeResult(app, !app.isFrozen, result)
        }
    }

    // ---------------------------------------------------------------------

    @android.annotation.SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        inflateOverflowMenu(menu, this)
        (menu as? androidx.appcompat.view.menu.MenuBuilder)?.setOptionalIconsVisible(true)
        val tv = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.textColorSecondary, tv, true)
        val tint = if (tv.resourceId != 0) androidx.core.content.ContextCompat.getColor(this, tv.resourceId) else tv.data
        for (id in listOf(R.id.action_search, R.id.action_batch_select, R.id.action_storage_info, R.id.action_settings, R.id.action_about)) {
            val item = menu.findItem(id) ?: continue
            item.icon = item.icon?.mutate()?.apply { setTint(tint) }
        }
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
    override fun onGroups() = onGroupsAction()
}
