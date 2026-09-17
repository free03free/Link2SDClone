package com.example.link2sdclone

/**
 * يمثل تطبيقاً مثبتاً على الجهاز مع حالة "الربط" الخاصة به بنظام SD.
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val isSystemApp: Boolean,
    val apkSizeBytes: Long,
    val dataSizeBytes: Long,
    var linkStatus: LinkStatus = LinkStatus.NOT_LINKED,
    var isOnInternal: Boolean = true
)

/**
 * حالة الربط بين بيانات التطبيق ومساحة الـ SD (partition الثاني).
 * يقابل هذا في Link2SD الأصلي حالات: "Linked" / "Not linked" / "Moved to SD".
 */
enum class LinkStatus {
    NOT_LINKED,
    LINKED_APK,
    LINKED_DATA,
    LINKED_DALVIK_CACHE,
    LINKED_ALL
}
