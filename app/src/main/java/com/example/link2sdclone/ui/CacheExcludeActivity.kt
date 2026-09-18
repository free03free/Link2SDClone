package com.example.link2sdclone.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.link2sdclone.R

class CacheExcludeActivity : AppCompatActivity() {

    private val prefsName = "cache_exclude_prefs"
    private lateinit var excluded: MutableSet<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cache_exclude)
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_exclude).apply {
            title = getString(R.string.settings_cache_exclude)
            setNavigationOnClickListener { finish() }
        }

        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        excluded = prefs.getStringSet("excluded_packages", emptySet())!!.toMutableSet()

        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        val recyclerView = findViewById<RecyclerView>(R.id.exclude_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ExcludeAdapter(apps) { packageName, isChecked ->
            if (isChecked) excluded.add(packageName) else excluded.remove(packageName)
            prefs.edit().putStringSet("excluded_packages", excluded).apply()
        }
    }

    private inner class ExcludeAdapter(
        private val apps: List<android.content.pm.ApplicationInfo>,
        private val onToggle: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<ExcludeAdapter.Holder>() {

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
            holder.checkbox.isChecked = excluded.contains(info.packageName)
            holder.itemView.setOnClickListener {
                holder.checkbox.isChecked = !holder.checkbox.isChecked
                onToggle(info.packageName, holder.checkbox.isChecked)
            }
        }

        override fun getItemCount(): Int = apps.size
    }
}
