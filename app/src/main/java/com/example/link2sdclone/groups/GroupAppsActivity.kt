package com.example.link2sdclone.groups

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.link2sdclone.R
import com.example.link2sdclone.freeze.FreezeManager
import com.example.link2sdclone.ui.showFilterDialog

class GroupAppsActivity : AppCompatActivity() {

    private lateinit var groupName: String
    private lateinit var selected: MutableSet<String>
    private lateinit var recyclerView: RecyclerView
    private var allInstalledApps: List<ApplicationInfo> = emptyList()
    private var currentFilterIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_apps)

        groupName = intent.getStringExtra(EXTRA_GROUP_NAME) ?: run { finish(); return }
        selected = GroupsManager.getPackages(this, groupName).toMutableSet()

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_group_apps).apply {
            title = groupName
            setNavigationOnClickListener { finish() }
        }

        val pm = packageManager
        // MATCH_DISABLED_COMPONENTS is required here so apps the user has
        // frozen (disabled at the system level) still show up in this list --
        // without it, PackageManager silently drops disabled apps, which made
        // apps the user had just selected/frozen for a group vanish entirely
        // the next time this screen opened.
        allInstalledApps = pm.getInstalledApplications(
            PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS
        ).sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        recyclerView = findViewById(R.id.group_apps_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        applyFilter()

        findViewById<Button>(R.id.btn_filter_group).setOnClickListener {
            showFilterDialog(this, currentFilterIndex) { index ->
                currentFilterIndex = index
                applyFilter()
            }
        }

        findViewById<Button>(R.id.btn_freeze_group).setOnClickListener { applyToGroup(freeze = true) }
        findViewById<Button>(R.id.btn_unfreeze_group).setOnClickListener { applyToGroup(freeze = false) }
    }

    // Same reason as GroupsActivity: without this, Island's result for
    // applyToGroup() is dropped and the batch never completes.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        FreezeManager.onActivityResult(requestCode, resultCode)
    }

    /** Only the filter categories that make sense from raw ApplicationInfo
     *  (no root, no favorites/recent-update tracking on this screen) --
     *  anything else falls back to "All" with a short explanation, same
     *  honest-degradation pattern used elsewhere in this project. */
    private fun applyFilter() {
        val filtered = when (currentFilterIndex) {
            1 -> allInstalledApps.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
            2 -> allInstalledApps.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            4 -> allInstalledApps.filter { it.sourceDir.contains("/mnt/") || it.sourceDir.contains("/storage/") }
            5 -> allInstalledApps.filter { !(it.sourceDir.contains("/mnt/") || it.sourceDir.contains("/storage/")) }
            7 -> allInstalledApps.filter { !it.enabled }
            0 -> allInstalledApps
            else -> {
                Toast.makeText(this, R.string.group_filter_unsupported, Toast.LENGTH_SHORT).show()
                currentFilterIndex = 0
                allInstalledApps
            }
        }
        recyclerView.adapter = AppsAdapter(filtered) { packageName, isChecked ->
            if (isChecked) selected.add(packageName) else selected.remove(packageName)
            GroupsManager.setPackages(this, groupName, selected)
        }
    }

    private fun applyToGroup(freeze: Boolean) {
        val packages = GroupsManager.getPackages(this, groupName)
        if (packages.isEmpty()) {
            Toast.makeText(this, R.string.group_empty_selection, Toast.LENGTH_SHORT).show()
            return
        }
        val backends = FreezeManager.availableBackends(this)
        if (backends.isEmpty()) {
            Toast.makeText(this, R.string.freeze_no_backend_title, Toast.LENGTH_SHORT).show()
            return
        }
        val backend = FreezeManager.preferredBackend?.takeIf { it in backends } ?: backends.first()
        var remaining = packages.size
        packages.forEach { pkg ->
            FreezeManager.setFrozen(this, backend, pkg, freeze) { _ ->
                runOnUiThread {
                    remaining--
                    if (remaining == 0) Toast.makeText(this, R.string.group_action_done, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private inner class AppsAdapter(
        private val apps: List<ApplicationInfo>,
        private val onToggle: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.Holder>() {

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.exclude_icon)
            val label: TextView = view.findViewById(R.id.exclude_label)
            val checkbox: CheckBox = view.findViewById(R.id.exclude_checkbox)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_exclude_row, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val info = apps[position]
            val pm = packageManager
            holder.label.text = pm.getApplicationLabel(info)
            holder.icon.setImageDrawable(try { pm.getApplicationIcon(info) } catch (e: Exception) { null })
            holder.checkbox.isChecked = selected.contains(info.packageName)
            holder.itemView.setOnClickListener {
                holder.checkbox.isChecked = !holder.checkbox.isChecked
                onToggle(info.packageName, holder.checkbox.isChecked)
            }
        }

        override fun getItemCount(): Int = apps.size
    }

    companion object {
        const val EXTRA_GROUP_NAME = "extra_group_name"
    }
}
