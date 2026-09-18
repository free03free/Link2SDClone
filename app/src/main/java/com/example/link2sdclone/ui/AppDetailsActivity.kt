package com.example.link2sdclone.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.link2sdclone.R
import com.example.link2sdclone.databinding.ActivityAppDetailsBinding
import com.example.link2sdclone.freeze.FreezeManager
import com.example.link2sdclone.freeze.FreezeResult
import java.io.File
import java.util.Locale

class AppDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDetailsBinding
    private lateinit var packageNameArg: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        packageNameArg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            finish(); return
        }
        binding.toolbarDetails.setNavigationOnClickListener { finish() }
        loadAndRender()
    }

    override fun onResume() {
        super.onResume()
        if (::packageNameArg.isInitialized) loadAndRender()
    }

    private fun loadAndRender() {
        val pm = packageManager
        val info: ApplicationInfo = try {
            pm.getApplicationInfo(packageNameArg, PackageManager.GET_META_DATA)
        } catch (e: PackageManager.NameNotFoundException) {
            Toast.makeText(this, "التطبيق غير موجود", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val label = pm.getApplicationLabel(info).toString()
        val icon = try { pm.getApplicationIcon(info) } catch (e: Exception) { null }
        val apkFile = File(info.sourceDir)
        val apkSize = if (apkFile.exists()) apkFile.length() else 0L
        val isFrozen = !info.enabled

        binding.toolbarDetails.title = label
        icon?.let { binding.detailsIcon.setImageDrawable(it) }
        binding.detailsName.text = label
        binding.detailsPackage.text = packageNameArg
        binding.detailsApkPath.text = info.sourceDir
        binding.detailsSizeSummary.text = "apk: ${formatSize(apkSize)}"

        binding.btnFreeze.text = if (isFrozen) getString(R.string.ctx_unfreeze) else getString(R.string.ctx_freeze)
        binding.btnFreeze.setOnClickListener { handleFreezeToggle(isFrozen) }

        binding.btnRun.setOnClickListener {
            val launchIntent = pm.getLaunchIntentForPackage(packageNameArg)
            if (launchIntent != null) startActivity(launchIntent)
            else Toast.makeText(this, "لا يمكن تشغيل هذا التطبيق", Toast.LENGTH_SHORT).show()
        }

        binding.btnDelete.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageNameArg")))
        }

        binding.btnShare.setOnClickListener {
            val text = "$label - $packageNameArg\nhttps://play.google.com/store/apps/details?id=$packageNameArg"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, label))
        }

        binding.btnViewPlay.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageNameArg")))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageNameArg")))
            }
        }
    }

    private fun handleFreezeToggle(currentlyFrozen: Boolean) {
        val backends = FreezeManager.availableBackends(this)
        if (backends.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.freeze_no_backend_title)
                .setMessage(R.string.freeze_no_backend_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val backend = FreezeManager.preferredBackend?.takeIf { it in backends } ?: backends.first()
        FreezeManager.setFrozen(this, backend, packageNameArg, !currentlyFrozen) { result ->
            runOnUiThread {
                when (result) {
                    is FreezeResult.Success -> {
                        loadAndRender()
                        Toast.makeText(
                            this,
                            if (!currentlyFrozen) R.string.freeze_success_frozen else R.string.freeze_success_unfrozen,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is FreezeResult.Failed -> Toast.makeText(this, R.string.freeze_failed, Toast.LENGTH_SHORT).show()
                    else -> { /* permission requested, wait for callback */ }
                }
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0.00B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024; unitIndex++
        }
        return String.format(Locale.getDefault(), "%.2f%s", value, units[unitIndex])
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }
}
