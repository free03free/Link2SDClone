package com.example.link2sdclone

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * تفاصيل تطبيق واحد: الحزمة، الإصدار، حجم APK، حجم البيانات،
 * وحالة الربط الحالية. مكافئ لشاشة تفاصيل التطبيق في Link2SD.
 */
class AppDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_detail)

        val packageName = intent.getStringExtra("package_name") ?: return
        val textDetails = findViewById<TextView>(R.id.textAppDetails)

        try {
            val pm = packageManager
            val pkgInfo = pm.getPackageInfo(packageName, 0)
            val appInfo = pkgInfo.applicationInfo

            val label = pm.getApplicationLabel(appInfo).toString()
            val apkSize = RootUtils.getFolderSizeBytes(appInfo.sourceDir)
            val dataSize = RootUtils.getFolderSizeBytes("/data/data/$packageName")

            textDetails.text = getString(
                R.string.app_detail_format,
                label,
                packageName,
                pkgInfo.versionName ?: "-",
                apkSize / (1024 * 1024),
                dataSize / (1024 * 1024)
            )
        } catch (e: Exception) {
            textDetails.text = getString(R.string.error_reading_storage)
        }
    }
}
