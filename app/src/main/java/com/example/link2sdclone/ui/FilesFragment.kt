package com.example.link2sdclone.ui

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.link2sdclone.R
import java.text.DateFormat
import java.util.Date

class FilesFragment : Fragment(R.layout.fragment_files) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val packageName = arguments?.getString(AppDetailsActivity.EXTRA_PACKAGE_NAME) ?: return
        val pm = requireContext().packageManager
        val info = try { pm.getApplicationInfo(packageName, 0) } catch (e: Exception) { return }
        val pkgInfo = try { pm.getPackageInfo(packageName, 0) } catch (e: Exception) { null }

        view.findViewById<TextView>(R.id.files_apk_path).text =
            "${getString(R.string.files_apk_path)}: ${info.sourceDir}"

        view.findViewById<TextView>(R.id.files_native_lib).text =
            "${getString(R.string.files_native_lib)}: ${info.nativeLibraryDir ?: "-"}"

        val abis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            info.splitSourceDirs?.joinToString() ?: android.os.Build.SUPPORTED_ABIS.joinToString()
        } else "-"
        view.findViewById<TextView>(R.id.files_abi).text = "${getString(R.string.files_abi)}: $abis"

        val installerPackage = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        } catch (e: Exception) { null } ?: "غير معروف"
        view.findViewById<TextView>(R.id.files_install_source).text =
            "${getString(R.string.files_install_source)}: $installerPackage"

        val df = DateFormat.getDateTimeInstance()
        view.findViewById<TextView>(R.id.files_first_install).text =
            "${getString(R.string.files_first_install)}: ${pkgInfo?.firstInstallTime?.let { df.format(Date(it)) } ?: "-"}"
        view.findViewById<TextView>(R.id.files_last_update).text =
            "${getString(R.string.files_last_update)}: ${pkgInfo?.lastUpdateTime?.let { df.format(Date(it)) } ?: "-"}"
    }
}
