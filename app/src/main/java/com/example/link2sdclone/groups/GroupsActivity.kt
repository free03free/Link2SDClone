package com.example.link2sdclone.groups

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.link2sdclone.R
import com.example.link2sdclone.freeze.FreezeManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.widget.SwitchCompat

class GroupsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_groups)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_groups)
            .apply {
                subtitle = "build: switch-v2"
                setNavigationOnClickListener { finish() }
            }

        recyclerView = findViewById(R.id.groups_list)
        emptyView = findViewById(R.id.groups_empty)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<FloatingActionButton>(R.id.fab_add_group).setOnClickListener {
            showCreateGroupDialog()
        }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    // Required so Island's freeze/unfreeze result actually reaches
    // FreezeManager -- without this override, Android drops the result
    // silently and toggleGroupFreeze() never completes (no Toast, no
    // guarantee the freeze even applied).
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
            onDelete = { name -> confirmDelete(name) }
        )
    }

    /** Freezes every app in the group if any is currently unfrozen; if the
     *  whole group is already frozen, unfreezes it instead. No need to open
     *  the group screen for this. */
    private fun toggleGroupFreeze(name: String) {
        val packages = GroupsManager.getPackages(this, name)
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
        val freezeTarget = !GroupsManager.areAllFrozen(this, name)
        var remaining = packages.size
        packages.forEach { pkg ->
            FreezeManager.setFrozen(this, backend, pkg, freezeTarget) { _ ->
                runOnUiThread {
                    remaining--
                    if (remaining == 0) {
                        refreshList()
                        Toast.makeText(this, R.string.group_action_done, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showCreateGroupDialog() {
        val input = EditText(this).apply { hint = getString(R.string.group_name_hint) }
        AlertDialog.Builder(this)
            .setTitle(R.string.group_new)
            .setView(input)
            .setPositiveButton(R.string.group_create) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    GroupsManager.createGroup(this, name)
                    refreshList()
                }
            }
            .setNegativeButton(R.string.group_cancel, null)
            .show()
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
        private val onDelete: (String) -> Unit
    ) : RecyclerView.Adapter<GroupsAdapter.Holder>() {

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.group_name)
            val count: TextView = view.findViewById(R.id.group_count)
            val toggle: SwitchCompat = view.findViewById(R.id.group_toggle)
            val delete: ImageButton = view.findViewById(R.id.group_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group_row, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val name = names[position]
            val count = GroupsManager.getPackages(this@GroupsActivity, name).size
            holder.name.text = name
            holder.count.text = getString(R.string.group_apps_count, count)

            // Clear the listener before setChecked() -- otherwise setting the
            // switch's state here (to reflect actual frozen status) would
            // itself fire onCheckedChanged and trigger a spurious toggle.
            holder.toggle.setOnCheckedChangeListener(null)
            holder.toggle.isChecked = GroupsManager.areAllFrozen(this@GroupsActivity, name)
            holder.toggle.setOnCheckedChangeListener { _, _ -> onToggle(name) }

            holder.itemView.setOnClickListener { onOpen(name) }
            holder.delete.setOnClickListener { onDelete(name) }
        }

        override fun getItemCount(): Int = names.size
    }
}
