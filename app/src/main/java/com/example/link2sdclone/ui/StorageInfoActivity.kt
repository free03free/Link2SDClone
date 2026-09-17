package com.example.link2sdclone.ui

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.link2sdclone.R
import java.io.File
import java.util.Locale

/**
 * Full-screen "معلومات الذاكرة" (Storage info), matching Link2SD:
 *  - ذاكرة الهاتف   -> /data
 *  - ذاكرة البطاقة  -> external SD mount point
 *  - النظام         -> /system
 *  - المخبئ         -> /data/cache
 *
 * Each row: title, path, progress bar (used vs free), and a detail line:
 * "الإجمالي: X غ.ب المستعمل: Y غ.ب المتاح: Z غ.ب (N% المتاح)"
 */
class StorageInfoActivity : AppCompatActivity() {

    private data class StorageRow(
        val containerId: Int,
        val title: String,
        val path: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_storage_info)

        val toolbar = findViewById<Toolbar>(R.id.toolbar_storage)
        toolbar.setNavigationOnClickListener { finish() }

        findViewById<ImageButton>(R.id.icon_share).setOnClickListener {
            shareStorageSummary()
        }

        val rows = listOf(
            StorageRow(R.id.storage_phone, getString(R.string.storage_phone_memory), "/data"),
            StorageRow(R.id.storage_sdcard, getString(R.string.storage_sd_memory), sdCardPath()),
            StorageRow(R.id.storage_system, "النظام", "/system"),
            StorageRow(R.id.storage_cache, "المخبئ", "/data/cache")
        )

        rows.forEach { row -> bindStorageRow(row) }
    }

    private fun bindStorageRow(row: StorageRow) {
        val container = findViewById<android.view.View>(row.containerId)
        val title = container.findViewById<TextView>(R.id.text_storage_title)
        val path = container.findViewById<TextView>(R.id.text_storage_path)
        val bar = container.findViewById<ProgressBar>(R.id.bar_storage)
        val detail = container.findViewById<TextView>(R.id.text_storage_detail)

        title.text = row.title
        path.text = row.path

        val file = File(row.path)
        if (!file.exists()) {
            bar.progress = 0
            detail.text = "—"
            return
        }

        val stat = StatFs(row.path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        val used = total - free
        val freePercent = if (total > 0) ((free.toDouble() / total) * 100).toInt() else 0
        val usedPercent = 100 - freePercent

        bar.max = 100
        bar.progress = usedPercent

        detail.text = String.format(
            Locale.getDefault(),
            "الإجمالي: %s المستعمل: %s المتاح: %s (%d%% المتاح)",
            formatGb(total), formatGb(used), formatGb(free), freePercent
        )
    }

    private fun formatGb(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.getDefault(), "%.2f غ.ب", gb)
    }

    /** Returns the first mounted external (removable) storage path, or /data as fallback. */
    private fun sdCardPath(): String {
        val dirs = getExternalFilesDirs(null)
        for (dir in dirs) {
            if (dir != null && Environment.isExternalStorageRemovable(dir)) {
                // walk up from .../Android/data/<pkg>/files to the mount root
                var root = dir
                while (root.parentFile != null && root.name != "Android") {
                    root = root.parentFile!!
                }
                return root.parentFile?.path ?: dir.path
            }
        }
        return "/data"
    }

    private fun shareStorageSummary() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "معلومات الذاكرة")
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
    }
}
