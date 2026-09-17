package com.example.link2sdclone

import android.os.Bundle
import android.os.StatFs
import android.os.Environment
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * تعرض معلومات المساحة: الذاكرة الداخلية، مساحة الـ SD الأساسية،
 * ومساحة الـ Partition الثاني إن وُجد — تماماً كتبويب "Storage info" في Link2SD.
 */
class StorageInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_storage_info)

        val textInternal = findViewById<TextView>(R.id.textInternalStorage)
        val textSd = findViewById<TextView>(R.id.textSdStorage)
        val textSecondPartition = findViewById<TextView>(R.id.textSecondPartition)

        textInternal.text = formatStorageInfo(
            Environment.getDataDirectory().path,
            getString(R.string.internal_storage)
        )

        textSd.text = formatStorageInfo(
            Environment.getExternalStorageDirectory().path,
            getString(R.string.sd_storage)
        )

        val secondPartitionPath = RootUtils.findSecondPartitionPath()
        textSecondPartition.text = if (secondPartitionPath != null) {
            formatStorageInfo(secondPartitionPath, getString(R.string.second_partition))
        } else {
            getString(R.string.no_second_partition_found)
        }
    }

    private fun formatStorageInfo(path: String, label: String): String {
        return try {
            val stat = StatFs(path)
            val totalBytes = stat.blockSizeLong * stat.blockCountLong
            val availableBytes = stat.blockSizeLong * stat.availableBlocksLong
            val usedBytes = totalBytes - availableBytes

            val totalMb = totalBytes / (1024 * 1024)
            val usedMb = usedBytes / (1024 * 1024)
            val availableMb = availableBytes / (1024 * 1024)

            getString(R.string.storage_format, label, usedMb, totalMb, availableMb)
        } catch (e: Exception) {
            "$label: ${getString(R.string.error_reading_storage)}"
        }
    }
}
