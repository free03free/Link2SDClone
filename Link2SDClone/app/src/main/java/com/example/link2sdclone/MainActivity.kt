package com.example.link2sdclone

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * الشاشة الرئيسية: تعرض كل التطبيقات المثبتة مع إمكانية الفلترة
 * (الكل / تطبيقات المستخدم / تطبيقات النظام / المرتبطة بالـ SD)
 * تماماً كما في الشاشة الرئيسية لتطبيق Link2SD الأصلي.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppListAdapter
    private var allApps: MutableList<AppInfo> = mutableListOf()
    private var currentFilter: Filter = Filter.ALL

    enum class Filter { ALL, USER_APPS, SYSTEM_APPS, LINKED_TO_SD, NOT_LINKED }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.title = getString(R.string.app_name)

        recyclerView = findViewById(R.id.recyclerViewApps)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AppListAdapter(
            items = emptyList(),
            iconLoader = { pkg ->
                try {
                    packageManager.getApplicationIcon(pkg)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            },
            onItemClick = { app -> showAppOptionsDialog(app) }
        )
        recyclerView.adapter = adapter

        loadInstalledApps()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_filter_all -> { applyFilter(Filter.ALL); true }
            R.id.action_filter_user -> { applyFilter(Filter.USER_APPS); true }
            R.id.action_filter_system -> { applyFilter(Filter.SYSTEM_APPS); true }
            R.id.action_filter_linked -> { applyFilter(Filter.LINKED_TO_SD); true }
            R.id.action_filter_not_linked -> { applyFilter(Filter.NOT_LINKED); true }
            R.id.action_storage_info -> {
                startActivity(Intent(this, StorageInfoActivity::class.java)); true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java)); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** يقرأ قائمة التطبيقات المثبتة على الجهاز عبر PackageManager */
    private fun loadInstalledApps() {
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        allApps = installedApps.map { appInfo ->
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val versionName = try {
                pm.getPackageInfo(appInfo.packageName, 0).versionName
            } catch (e: Exception) { null }

            AppInfo(
                packageName = appInfo.packageName,
                label = pm.getApplicationLabel(appInfo).toString(),
                versionName = versionName,
                isSystemApp = isSystem,
                apkSizeBytes = 0L, // في نسخة حقيقية: يُحسب عبر RootUtils.getFolderSizeBytes
                dataSizeBytes = 0L,
                linkStatus = LinkStatus.NOT_LINKED
            )
        }.sortedBy { it.label.lowercase() }.toMutableList()

        applyFilter(currentFilter)
    }

    private fun applyFilter(filter: Filter) {
        currentFilter = filter
        val filtered = when (filter) {
            Filter.ALL -> allApps
            Filter.USER_APPS -> allApps.filter { !it.isSystemApp }
            Filter.SYSTEM_APPS -> allApps.filter { it.isSystemApp }
            Filter.LINKED_TO_SD -> allApps.filter { it.linkStatus != LinkStatus.NOT_LINKED }
            Filter.NOT_LINKED -> allApps.filter { it.linkStatus == LinkStatus.NOT_LINKED }
        }
        adapter.updateData(filtered)
        supportActionBar?.subtitle = getString(R.string.apps_count_format, filtered.size)
    }

    /** قائمة الخيارات عند الضغط على تطبيق: Create Link / Remove Link / Move to SD / Uninstall */
    private fun showAppOptionsDialog(app: AppInfo) {
        val options = arrayOf(
            getString(R.string.action_create_link),
            getString(R.string.action_remove_link),
            getString(R.string.action_move_to_sd),
            getString(R.string.action_app_info),
            getString(R.string.action_uninstall)
        )

        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> performCreateLink(app)
                    1 -> performRemoveLink(app)
                    2 -> performMoveToSd(app)
                    3 -> startActivity(
                        Intent(this, AppDetailActivity::class.java)
                            .putExtra("package_name", app.packageName)
                    )
                    4 -> uninstallApp(app.packageName)
                }
            }
            .show()
    }

    private fun performCreateLink(app: AppInfo) {
        if (!RootUtils.hasRootAccess()) {
            Toast.makeText(this, R.string.error_no_root, Toast.LENGTH_LONG).show()
            return
        }
        val sdPath = RootUtils.findSecondPartitionPath()
        if (sdPath == null) {
            Toast.makeText(this, R.string.error_no_second_partition, Toast.LENGTH_LONG).show()
            return
        }
        val success = RootUtils.createLink(app.packageName, sdPath)
        val msg = if (success) R.string.link_success else R.string.link_failed
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        if (success) {
            app.linkStatus = LinkStatus.LINKED_ALL
            adapter.notifyDataSetChanged()
        }
    }

    private fun performRemoveLink(app: AppInfo) {
        if (!RootUtils.hasRootAccess()) {
            Toast.makeText(this, R.string.error_no_root, Toast.LENGTH_LONG).show()
            return
        }
        val sdPath = RootUtils.findSecondPartitionPath() ?: return
        val success = RootUtils.removeLink(app.packageName, sdPath)
        val msg = if (success) R.string.unlink_success else R.string.link_failed
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        if (success) {
            app.linkStatus = LinkStatus.NOT_LINKED
            adapter.notifyDataSetChanged()
        }
    }

    private fun performMoveToSd(app: AppInfo) {
        // آلية أندرويد الرسمية: PackageManager.MOVE_TO_SD (تعمل فقط للتطبيقات
        // التي تدعم android:installLocation="auto" أو "preferExternal")
        Toast.makeText(this, R.string.move_to_sd_hint, Toast.LENGTH_LONG).show()
    }

    private fun uninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = android.net.Uri.parse("package:$packageName")
        startActivity(intent)
    }
}
