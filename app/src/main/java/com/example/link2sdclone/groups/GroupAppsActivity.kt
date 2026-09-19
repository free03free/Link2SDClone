package com.example.link2sdclone.groups

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
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
        // MATCH_DISABLED_COMPONENTS مطلوب هنا -- بدونه، PackageManager بيسقط
        // التطبيقات المجمدة من القائمة بصمت، فتختفي التطبيقات اللي جمدتها
        // للتو في نفس المجموعة.
        allInstalledApps = pm.getInstalledApplications(
            PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS or (if (GroupsManager.showHiddenFrozen(this)) PackageManager.MATCH_UNINSTALLED_PACKAGES else 0)
        ).filter { (it.flags and ApplicationInfo.FLAG_INSTALLED) != 0 || GroupsManager.isPackageFrozen(this, it.packageName) }
        .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        recyclerView = findViewById(R.id.group_apps_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        applyFilter()

        findViewById<Button>(R.id.btn_filter_group).setOnClickListener {
            showFilterDialog(this, currentFilterIndex, R.array.group_filter_options) { index ->
                currentFilterIndex = index
                applyFilter()
            }
        }

        findViewById<Button>(R.id.btn_freeze_group).setOnClickListener { applyToGroup(freeze = true) }
        findViewById<Button>(R.id.btn_unfreeze_group).setOnClickListener { applyToGroup(freeze = false) }
    }

    // نفس السبب الموجود في GroupsActivity: بدونها رد Island يضيع والعملية
    // الجماعية ما بتكملش.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        FreezeManager.onActivityResult(requestCode, resultCode)
    }

    private fun applyFilter() {
        val filtered = when (currentFilterIndex) {
            1 -> allInstalledApps.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
            2 -> allInstalledApps.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            4 -> allInstalledApps.filter { it.sourceDir.contains("/mnt/") || it.sourceDir.contains("/storage/") }
            5 -> allInstalledApps.filter { !(it.sourceDir.contains("/mnt/") || it.sourceDir.contains("/storage/")) }
            7 -> allInstalledApps.filter { GroupsManager.isPackageFrozen(this, it.packageName) }
            // خاص بهذه الشاشة فقط: التطبيقات المجمدة داخل هذه المجموعة تحديداً
            11 -> allInstalledApps.filter { it.packageName in selected && GroupsManager.isPackageFrozen(this, it.packageName) }
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

    /** يشترك فيه زرّا "تجميد المحدد"/"إلغاء تجميد المحدد" هنا وسويتش
     *  المجموعة في GroupsActivity عبر GroupFreezeHelper -- مسار واحد فقط. */
    private fun applyToGroup(freeze: Boolean) {
        val packages = GroupsManager.getPackages(this, groupName)
        GroupFreezeHelper.apply(this, packages, freeze) { }
    }

    private inner class AppsAdapter(
        private val apps: List<ApplicationInfo>,
        private val onToggle: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.Holder>() {

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.exclude_icon)
            val label: TextView = view.findViewById(R.id.exclude_label)
            val checkbox: CheckBox = view.findViewById(R.id.exclude_checkbox)
            val remove: ImageButton = view.findViewById(R.id.exclude_remove_from_group)
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

            val isInGroup = selected.contains(info.packageName)
            holder.checkbox.isChecked = isInGroup
            holder.remove.visibility = if (isInGroup) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener {
                holder.checkbox.isChecked = !holder.checkbox.isChecked
                holder.remove.visibility = if (holder.checkbox.isChecked) View.VISIBLE else View.GONE
                onToggle(info.packageName, holder.checkbox.isChecked)
            }
            holder.remove.setOnClickListener {
                holder.checkbox.isChecked = false
                holder.remove.visibility = View.GONE
                onToggle(info.packageName, false)
            }
        }

        override fun getItemCount(): Int = apps.size
    }

    companion object {
        const val EXTRA_GROUP_NAME = "extra_group_name"
    }
}
