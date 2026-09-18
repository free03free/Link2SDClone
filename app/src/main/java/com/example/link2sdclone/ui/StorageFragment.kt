package com.example.link2sdclone.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.link2sdclone.R
import com.example.link2sdclone.storage.StorageStatsHelper
import java.util.Locale

class StorageFragment : Fragment(R.layout.fragment_storage) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val packageName = arguments?.getString(AppDetailsActivity.EXTRA_PACKAGE_NAME) ?: return
        val pm = requireContext().packageManager
        val info = try { pm.getApplicationInfo(packageName, 0) } catch (e: Exception) { return }
        val apkFile = java.io.File(info.sourceDir)
        val apkSize = if (apkFile.exists()) apkFile.length() else 0L
        val isOnSd = info.sourceDir.contains("/mnt/") || info.sourceDir.contains("/storage/")

        val permissionBanner = view.findViewById<LinearLayout>(R.id.permission_banner)
        val hasAccess = StorageStatsHelper.hasUsageAccess(requireContext())

        val realSizes = if (hasAccess) StorageStatsHelper.queryRealSizes(requireContext(), info.uid) else null

        if (!hasAccess) {
            permissionBanner.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.permission_message).text =
                getString(R.string.usage_access_required_message)
            view.findViewById<Button>(R.id.btn_grant_permission).apply {
                text = getString(R.string.grant_permission)
                setOnClickListener { StorageStatsHelper.openUsageAccessSettings(requireContext()) }
            }
        }

        val dataSize = realSizes?.dataBytes ?: 0L
        val cacheSize = realSizes?.cacheBytes ?: 0L
        val totalSize = apkSize + dataSize + cacheSize

        val internalBytes = if (isOnSd) 0L else totalSize
        val sdBytes = if (isOnSd) totalSize else 0L
        val internalPercent = if (totalSize > 0) (internalBytes * 100 / totalSize).toInt() else 100

        val proportionBar = view.findViewById<LinearLayout>(R.id.proportion_bar)
        proportionBar.removeAllViews()
        if (internalPercent > 0) {
            proportionBar.addView(colorBar(internalPercent, "#1976D2"))
        }
        if (internalPercent < 100) {
            proportionBar.addView(colorBar(100 - internalPercent, "#FF9800"))
        }

        view.findViewById<TextView>(R.id.label_internal).text =
            "${getString(R.string.internal_storage)}: ${formatSize(internalBytes)}"
        view.findViewById<TextView>(R.id.label_sd).text =
            "${getString(R.string.sd_storage)}: ${formatSize(sdBytes)}"

        view.findViewById<TextView>(R.id.row_apk).text =
            "${getString(R.string.app_size_apk)}: ${formatSize(apkSize)}"
        view.findViewById<TextView>(R.id.row_data).text =
            "${getString(R.string.app_size_data)}: ${if (hasAccess) formatSize(dataSize) else getString(R.string.size_unavailable)}"
        view.findViewById<TextView>(R.id.row_cache).text =
            "${getString(R.string.app_size_cache)}: ${if (hasAccess) formatSize(cacheSize) else getString(R.string.size_unavailable)}"
        view.findViewById<TextView>(R.id.row_total).text =
            "${getString(R.string.app_size_total)}: ${formatSize(totalSize)}"
    }

    private fun colorBar(weightPercent: Int, colorHex: String): View {
        return View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weightPercent.toFloat())
            setBackgroundColor(android.graphics.Color.parseColor(colorHex))
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0.00B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) { value /= 1024; unitIndex++ }
        return String.format(Locale.getDefault(), "%.2f%s", value, units[unitIndex])
    }
}
