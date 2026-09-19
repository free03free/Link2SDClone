package com.example.link2sdclone.groups

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.link2sdclone.R
import com.example.link2sdclone.freeze.FreezeManager
import com.google.android.material.floatingactionbutton.FloatingActionButton

class GroupsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_groups)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_groups)
            .setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.groups_list)
        emptyView = findViewById(R.id.groups_empty)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<FloatingActionButton>(R.id.fab_add_group).setOnClickListener {
            showCreateGroupDialog()
        }

        findViewById<Button>(R.id.btn_sort_groups).setOnClickListener {
            showSortDialog(it)
        }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    // بدون هذه الدالة، رد Island (نجاح/فشل التجميد) يضيع تماماً ولا يوصل
    // أبداً لـ FreezeManager، فالتجميد الفعلي ما بيحصلش رغم ظهور رسالة "تم".
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        FreezeManager.onActivityResult(requestCode, resultCode)
    }

    private fun refreshList() {
        val names = GroupsManager.listGroupNames(this)
        emptyView.visibility = if (names.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (names.isEmpty()) View.GONE else View.VISIBLE
        recyclerView.adapter = GroupsAdapter(names,
            onOpen = { name ->
                startActivity(
                    android.content.Intent(this, GroupAppsActivity::class.java)
                        .putExtra(GroupAppsActivity.EXTRA_GROUP_NAME, name)
                )
            },
            onToggle = { name -> toggleGroupFreeze(name) },
            onDelete = { name -> confirmDelete(name) },
            onTogglePin = { name -> togglePin(name) },
            onRename = { name -> showRenameDialog(name) }
        )
    }

    /** يستخدم بالضبط نفس منطق زرّي "تجميد المحدد"/"إلغاء تجميد المحدد" في
     *  GroupAppsActivity عبر GroupFreezeHelper، فلا يوجد أي مسار منفصل يمكن
     *  أن يتعارض معه. */
    private fun toggleGroupFreeze(name: String) {
        val packages = GroupsManager.getPackages(this, name)
        val freezeTarget = !GroupsManager.areAllFrozen(this, name)
        GroupFreezeHelper.apply(this, packages, freezeTarget) {
            refreshList()
        }
    }

    private fun togglePin(name: String) {
        val newState = !GroupsManager.isPinned(this, name)
        GroupsManager.setPinned(this, name, newState)
        refreshList()
    }

    private fun showCreateGroupDialog() {
        val input = EditText(this).apply { hint = getString(R.string.group_name_hint) }
        AlertDialog.Builder(this)
            .setTitle(R.string.group_new)
            .setView(input)
            .setPositiveButton(R.string.group_create) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (!GroupsManager.createGroup(this, name)) {
                        Toast.makeText(this, R.string.group_name_taken, Toast.LENGTH_SHORT).show()
                    }
                    refreshList()
                }
            }
            .setNegativeButton(R.string.group_cancel, null)
            .show()
    }

    private fun showRenameDialog(name: String) {
        val input = EditText(this).apply {
            setText(name)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.group_rename)
            .setView(input)
            .setPositiveButton(R.string.group_rename_confirm) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) return@setPositiveButton
                if (!GroupsManager.renameGroup(this, name, newName)) {
                    Toast.makeText(this, R.string.group_name_taken, Toast.LENGTH_SHORT).show()
                } else {
                    refreshList()
                }
            }
            .setNegativeButton(R.string.group_cancel, null)
            .show()
    }

    private fun showSortDialog(anchor: android.view.View) {
        val labels = arrayOf(
            getString(R.string.group_sort_name),
            getString(R.string.group_sort_created),
            getString(R.string.group_sort_modified),
            getString(R.string.group_sort_count)
        )
        val values = arrayOf(
            GroupsManager.GroupSort.NAME,
            GroupsManager.GroupSort.CREATED,
            GroupsManager.GroupSort.MODIFIED,
            GroupsManager.GroupSort.COUNT
        )
        val current = GroupsManager.getSortMode(this)
        val checkedIndex = values.indexOf(current).coerceAtLeast(0)

        com.example.link2sdclone.ui.showChoicePopup(
            this, anchor, labels.toList().toTypedArray(), checkedIndex
        ) { index ->
            GroupsManager.setSortMode(this, values[index])
            refreshList()
        }
    }

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(this)
            .setMessage(R.string.group_delete_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                GroupsManager.deleteGroup(this, name)
                refreshList()
            }
            .setNegativeButton(R.string.group_cancel, null)
            .show()
    }

    private inner class GroupsAdapter(
        private val names: List<String>,
        private val onOpen: (String) -> Unit,
        private val onToggle: (String) -> Unit,
        private val onDelete: (String) -> Unit,
        private val onTogglePin: (String) -> Unit,
        private val onRename: (String) -> Unit
    ) : RecyclerView.Adapter<GroupsAdapter.Holder>() {

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.group_name)
            val count: TextView = view.findViewById(R.id.group_count)
            val toggle: SwitchCompat = view.findViewById(R.id.group_toggle)
            val delete: ImageButton = view.findViewById(R.id.group_delete)
            val pin: ImageButton = view.findViewById(R.id.group_pin)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group_row, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val name = names[position]
            val (frozen, total) = GroupsManager.frozenCount(this@GroupsActivity, name)
            holder.name.text = name
            holder.count.text = if (total == 0)
                getString(R.string.group_apps_count, 0)
            else
                getString(R.string.group_frozen_count_format, frozen, total)

            val pinned = GroupsManager.isPinned(this@GroupsActivity, name)
            holder.pin.setImageResource(
                if (pinned) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
            )

            // إزالة المستمع قبل ضبط الحالة حتى لا يُطلَق onCheckedChanged بشكل
            // زائف أثناء ضبط الحالة الحقيقية.
            holder.toggle.setOnCheckedChangeListener(null)
            holder.toggle.isChecked = GroupsManager.areAllFrozen(this@GroupsActivity, name)
            holder.toggle.setOnCheckedChangeListener { _, _ -> onToggle(name) }

            holder.itemView.setOnClickListener { onOpen(name) }
            holder.itemView.setOnLongClickListener { onRename(name); true }
            holder.delete.setOnClickListener { onDelete(name) }
            holder.pin.setOnClickListener { onTogglePin(name) }
        }

        override fun getItemCount(): Int = names.size
    }
}
