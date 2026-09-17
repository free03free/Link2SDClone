package com.example.link2sdclone

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private var items: List<AppInfo>,
    private val iconLoader: (String) -> Drawable?,
    private val onItemClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iconAppIcon)
        val name: TextView = view.findViewById(R.id.textAppName)
        val status: TextView = view.findViewById(R.id.textLinkStatus)
        val size: TextView = view.findViewById(R.id.textAppSize)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = items[position]
        holder.name.text = app.label
        holder.icon.setImageDrawable(iconLoader(app.packageName))

        holder.status.text = when (app.linkStatus) {
            LinkStatus.NOT_LINKED -> holder.itemView.context.getString(R.string.status_not_linked)
            LinkStatus.LINKED_APK -> holder.itemView.context.getString(R.string.status_linked_apk)
            LinkStatus.LINKED_DATA -> holder.itemView.context.getString(R.string.status_linked_data)
            LinkStatus.LINKED_DALVIK_CACHE -> holder.itemView.context.getString(R.string.status_linked_dalvik)
            LinkStatus.LINKED_ALL -> holder.itemView.context.getString(R.string.status_linked_all)
        }

        val totalMb = (app.apkSizeBytes + app.dataSizeBytes) / (1024 * 1024)
        holder.size.text = holder.itemView.context.getString(R.string.size_format, totalMb)

        holder.itemView.setOnClickListener { onItemClick(app) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<AppInfo>) {
        items = newItems
        notifyDataSetChanged()
    }
}
