package com.example.link2sdclone.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.link2sdclone.R
import com.example.link2sdclone.storage.StorageStatsHelper
import com.example.link2sdclone.util.AppFilesHelper
import java.util.Locale

class StorageFragment : Fragment(R.layout.fragment_storage) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val packageName = arguments?.getString(AppDetailsActivity.EXTRA_PACKAGE_NAME) ?: return
        val pm = ctx.packageManager
        val info = try {
            pm.getApplicationInfo(
                packageName,
                PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
            )
        } catch (e: Exception) { return }

        val apkFile = java.io.File(info.sourceDir)
        val apkSize = if (apkFile.exists()) apkFile.length() else 0L
        val libSize = AppFilesHelper.libSize(info)
        val dexSize = AppFilesHelper.dexSize(info)
        val appSize = apkSize + dexSize + libSize
        val isOnSd = info.sourceDir.contains("/mnt/") || info.sourceDir.contains("/storage/")

        val hasAccess = StorageStatsHelper.hasUsageAccess(ctx)
        val realSizes = if (hasAccess) StorageStatsHelper.queryRealSizes(ctx, info.uid) else null

        if (!hasAccess) {
            view.findViewById<LinearLayout>(R.id.permission_banner).visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.permission_message).text =
                getString(R.string.usage_access_required_message)
            view.findViewById<Button>(R.id.btn_grant_permission).apply {
                text = getString(R.string.grant_permission)
                setOnClickListener { StorageStatsHelper.openUsageAccessSettings(ctx) }
            }
        }

        val dataSize = realSizes?.dataBytes ?: 0L
        val cacheSize = realSizes?.cacheBytes ?: 0L
        val totalSize = appSize + dataSize + cacheSize

        val internalBytes = if (isOnSd) 0L else totalSize
        val sdBytes = if (isOnSd) totalSize else 0L
        val internalPercent = if (totalSize > 0) (internalBytes * 100 / totalSize).toInt() else 100

        view.findViewById<PieView>(R.id.storage_pie).setInternalPercent(internalPercent)
        view.findViewById<TextView>(R.id.total_big).text = formatSize(totalSize)

        fun chip(id: Int, bytes: Long, icon: Int, tint: Int) {
            view.findViewById<TextView>(id).apply {
                text = formatSize(bytes)
                val d = ContextCompat.getDrawable(ctx, icon)?.mutate()
                val s = (18 * resources.displayMetrics.density).toInt()
                d?.setBounds(0, 0, s, s)
                d?.setTint(tint)
                setCompoundDrawablesRelative(null, null, d, null)
            }
        }
        chip(R.id.chip_internal, internalBytes, R.drawable.ic_phone_orig, Color.parseColor("#616161"))
        chip(R.id.chip_sd, sdBytes, R.drawable.ic_sd_card, Color.WHITE)

        fun setRow(id: Int, text: String) { view.findViewById<TextView>(id).text = text }
        val unavailable = getString(R.string.size_unavailable)
        setRow(R.id.row_app, formatSize(appSize))
        setRow(R.id.row_apk, formatSize(apkSize))
        setRow(R.id.row_dex, formatSize(dexSize))
        setRow(R.id.row_lib, formatSize(libSize))
        setRow(R.id.row_data, if (hasAccess) formatSize(dataSize) else unavailable)
        setRow(R.id.row_cache, if (hasAccess) formatSize(cacheSize) else unavailable)

        view.findViewById<ImageView>(R.id.apk_location_icon)
            .setImageResource(if (isOnSd) R.drawable.ic_sd_card else R.drawable.ic_phone_orig)

        view.findViewById<Button>(R.id.btn_move_storage).apply {
            text = getString(if (isOnSd) R.string.storage_move_to_phone else R.string.storage_move_to_sd)
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                } catch (e: Exception) {
                    Toast.makeText(ctx, R.string.appinfo_cannot_open, Toast.LENGTH_SHORT).show()
                }
            }
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
