package com.example.link2sdclone.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.link2sdclone.R
import com.example.link2sdclone.storage.StorageStatsHelper
import com.example.link2sdclone.util.AppFilesHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FilesFragment : Fragment(R.layout.fragment_files) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val packageName = arguments?.getString(AppDetailsActivity.EXTRA_PACKAGE_NAME) ?: return
        val ctx = requireContext()
        val pm = ctx.packageManager
        val info = try { pm.getApplicationInfo(packageName, 0) } catch (e: Exception) { return }
        val pkgInfo = try { pm.getPackageInfo(packageName, 0) } catch (e: Exception) { null }

        val apkFile = File(info.sourceDir)
        val apkSize = if (apkFile.exists()) apkFile.length() else 0L
        val libSize = AppFilesHelper.libSize(info)
        val dexSize = AppFilesHelper.dexSize(info)
        val oatDir = File(info.sourceDir).parentFile?.let { File(it, "oat") }
        val dexPath = if (oatDir != null && oatDir.exists()) oatDir.path else ""
        val dataPath = info.dataDir ?: "-"

        val hasAccess = StorageStatsHelper.hasUsageAccess(ctx)
        val realSizes = if (hasAccess) StorageStatsHelper.queryRealSizes(ctx, info.uid) else null
        val dataSize = if (hasAccess) formatSize(realSizes?.dataBytes ?: 0L)
        else getString(R.string.size_unavailable)

        fun setText(id: Int, text: String) { view.findViewById<TextView>(id).text = text }
        fun setRow(pathId: Int, sizeId: Int, path: String, size: String) {
            setText(pathId, path)
            setText(sizeId, size)
        }

        setRow(R.id.file_apk_path, R.id.file_apk_size, info.sourceDir, formatSize(apkSize))
        setRow(R.id.file_dex_path, R.id.file_dex_size, dexPath, formatSize(dexSize))
        setRow(R.id.file_lib_path, R.id.file_lib_size, info.nativeLibraryDir ?: "-", formatSize(libSize))
        setRow(R.id.file_data_path, R.id.file_data_size, dataPath, dataSize)

        val abis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.joinToString()
        } else "-"
        setText(R.id.row_file_abi, abis)

        val installerPackage = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        } catch (e: Exception) { null } ?: "غير معروف"
        setText(R.id.row_file_source, installerPackage)

        val df = SimpleDateFormat("HH:mm:ss dd-MM-yyyy", Locale.getDefault())
        setText(R.id.row_file_first, pkgInfo?.firstInstallTime?.let { df.format(Date(it)) } ?: "-")
        setText(R.id.row_file_last, pkgInfo?.lastUpdateTime?.let { df.format(Date(it)) } ?: "-")
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
