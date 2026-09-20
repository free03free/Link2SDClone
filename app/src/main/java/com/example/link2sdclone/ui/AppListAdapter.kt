package com.example.link2sdclone.ui

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.link2sdclone.R
import com.example.link2sdclone.model.AppEntry
import java.util.Locale

class AppListAdapter(
    private var items: List<AppEntry>,
    private val onClick: (AppEntry) -> Unit,
    private val onLongClick: (AppEntry, View) -> Unit,
    private val onFavoriteClick: (AppEntry) -> Unit,
    private val onSelectToggle: (AppEntry) -> Unit
) : RecyclerView.Adapter<AppListAdapter.RowHolder>() {

    private var isSelectionMode = false
    private var selectedPackages: Set<String> = emptySet()

    class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.row_icon)
        val selectBadge: ImageView = view.findViewById(R.id.row_select_badge)
        val name: TextView = view.findViewById(R.id.row_name)
        val path: TextView = view.findViewById(R.id.row_path)
        val sizes: TextView = view.findViewById(R.id.row_sizes)
        val favorite: ImageView = view.findViewById(R.id.row_favorite)
        val sdIndicator: ImageView = view.findViewById(R.id.row_sd_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_row, parent, false)
        return RowHolder(view)
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        val app = items[position]
        holder.icon.setImageDrawable(app.icon ?: holder.icon.context.getDrawable(R.drawable.ic_android_default))
        holder.name.text = run<CharSequence> {
            val ctx = holder.itemView.context
            val tagList = ArrayList<String>()
            if (app.isFrozen) tagList.add(ctx.getString(R.string.filter_frozen))
            if (app.isUpdatedSystem) tagList.add(ctx.getString(R.string.filter_updated))
            if (app.packageName in com.example.link2sdclone.util.LinkEngine.linkedPackages(ctx)) tagList.add(ctx.getString(R.string.filter_linked))
            if (tagList.isEmpty()) {
                app.label
            } else {
                val sb = SpannableStringBuilder("\u2068" + app.label + "\u2069 ")
                val start = sb.length
                sb.append("\u2067" + tagList.joinToString("  ") { "-" + it + "-" } + "\u2069")
                sb.setSpan(
                    ForegroundColorSpan(Color.parseColor("#03A9F4")),
                    start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sb
            }
        }
        holder.path.text = app.apkPath
        // rowMirror: العربية = أيقونة يسارًا ونجمة يمينًا، الإنجليزية = العكس، والنصوص تبدأ من جهة لغتها
        val isRtlUi = holder.itemView.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        holder.itemView.layoutDirection = if (isRtlUi) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
        for (tv in listOf(holder.name, holder.path, holder.sizes)) {
            tv.textDirection = if (isRtlUi) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
            tv.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        }
        holder.sizes.text = holder.itemView.context.getString(
            R.string.row_sizes_format,
            formatSize(app.apkSizeBytes),
            formatSize(app.dataSizeBytes),
            formatSize(app.cacheSizeBytes),
            formatSize(app.totalSizeBytes)
        )

        val selected = selectedPackages.contains(app.packageName)
        holder.selectBadge.visibility = if (isSelectionMode && selected) View.VISIBLE else View.GONE
        holder.favorite.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
        holder.favorite.setImageResource(
            if (app.isFavorite) R.drawable.ic_star else R.drawable.ic_star_outline
        )
        holder.sdIndicator.visibility = if (app.isOnSdCard && !isSelectionMode) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            if (isSelectionMode) onSelectToggle(app) else onClick(app)
        }
        holder.itemView.setOnLongClickListener { onLongClick(app, it); true }
        holder.favorite.setOnClickListener { onFavoriteClick(app) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<AppEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun currentItems(): List<AppEntry> = items

    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        notifyDataSetChanged()
    }

    fun setSelectedPackages(packages: Set<String>) {
        selectedPackages = packages
        notifyDataSetChanged()
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0.00B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        return String.format(Locale.getDefault(), "%.2f%s", value, units[unitIndex])
    }
}
