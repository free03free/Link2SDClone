package com.example.link2sdclone.util

import android.content.pm.ApplicationInfo
import java.io.File

/** حساب أحجام مجلدات التطبيق (Dex وLib) بدون Root. أي خطأ يُرجع 0. */
object AppFilesHelper {

    fun dirSize(f: File?, depth: Int = 0): Long {
        if (f == null || depth > 6) return 0L
        return try {
            if (f.isFile) {
                f.length()
            } else {
                var total = 0L
                f.listFiles()?.forEach { total += dirSize(it, depth + 1) }
                total
            }
        } catch (e: Exception) {
            0L
        }
    }

    fun libSize(info: ApplicationInfo): Long =
        info.nativeLibraryDir?.let { dirSize(File(it)) } ?: 0L

    fun dexSize(info: ApplicationInfo): Long {
        val parent = File(info.sourceDir).parentFile ?: return 0L
        return dirSize(File(parent, "oat"))
    }
}
