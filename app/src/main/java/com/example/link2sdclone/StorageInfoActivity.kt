package com.example.link2sdclone

import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import androidx.appcompat.app.AppCompatActivity
import com.example.link2sdclone.databinding.ActivityStorageInfoBinding
import com.example.link2sdclone.databinding.ItemStorageBarBinding

/**
 * تعرض معلومات المساحة: ذاكرة الهاتف، ذاكرة البطاقة، النظام، والمخبأ.
 */
class StorageInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStorageInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStorageInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarStorage)
        binding.toolbarStorage.setNavigationOnClickListener { finish() }
        binding.iconShare.setOnClickListener { finish() }

        fillStorageBar(
            binding.storagePhone,
            Environment.getDataDirectory().path,
            getString(R.string.internal_storage)
        )

        fillStorageBar(
            binding.storageSdcard,
            Environment.getExternalStorageDirectory().path,
            getString(R.string.sd_storage)
        )

        fillStorageBar(
            binding.storageSystem,
            "/system",
            "النظام"
        )

        fillStorageBar(
            binding.storageCache,
            cacheDir.path,
            "المخبأ"
        )
    }

    private fun fillStorageBar(itemBinding: ItemStorageBarBinding, path: String, title: String) {
        itemBinding.textStorageTitle.text = title
        itemBinding.textStoragePath.text = path

        try {
            val stat = StatFs(path)
            val totalBytes = stat.blockSizeLong * stat.blockCountLong
            val availableBytes = stat.blockSizeLong * stat.availableBlocksLong
            val usedBytes = totalBytes - availableBytes

            val totalMb = totalBytes / (1024 * 1024)
            val usedMb = usedBytes / (1024 * 1024)
            val availableMb = availableBytes / (1024 * 1024)

            val percentUsed = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 0
            itemBinding.barStorage.progress = percentUsed

            itemBinding.textStorageDetail.text = getString(
                R.string.storage_format, title, usedMb, totalMb, availableMb
            )
        } catch (e: Exception) {
            itemBinding.textStorageDetail.text = getString(R.string.error_reading_storage)
        }
    }
}
