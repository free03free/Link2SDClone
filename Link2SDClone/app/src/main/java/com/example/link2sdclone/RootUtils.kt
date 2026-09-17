package com.example.link2sdclone

import com.topjohnwu.superuser.Shell

/**
 * هذه الطبقة هي المكافئ لِـ "المحرك" الذي يستخدمه Link2SD داخلياً:
 * تنفيذ أوامر عبر صلاحية root للتعامل مع mount/symlink بين
 * /data/app (الذاكرة الداخلية) و partition الثاني على بطاقة SD.
 *
 * ملاحظة مهمة: هذا كود توضيحي مبسّط لغرض تعليمي. التعامل الفعلي مع
 * partitions ونظام الملفات الخاص بالتطبيقات يختلف بين إصدارات أندرويد
 * وقد يحتاج تعديلات إضافية (SELinux contexts، إلخ) ليعمل بشكل حقيقي على جهاز.
 */
object RootUtils {

    init {
        Shell.enableVerboseLogging = true
        Shell.setDefaultBuilder(
            Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR)
        )
    }

    /** يتحقق إن كانت صلاحيات root متاحة على الجهاز */
    fun hasRootAccess(): Boolean {
        return Shell.getShell().isRoot
    }

    /** ينفذ أمر شل واحد ويعيد نتيجته كسطور نصية */
    fun runCommand(command: String): List<String> {
        val result = Shell.cmd(command).exec()
        return result.out
    }

    /**
     * يبحث عن partition ثانٍ على بطاقة SD (يفترض أنه تم إنشاؤه مسبقاً
     * بواسطة أداة تقسيم مثل parted/gparted خارج التطبيق).
     */
    fun findSecondPartitionPath(): String? {
        val output = runCommand("blkid | grep -i 'ext4\\|ext3\\|ext2' | grep -v 'mmcblk0p1\\|mmcblk1p1'")
        return output.firstOrNull()?.substringBefore(":")
    }

    /**
     * ينشئ mount bind أو symlink لربط مجلد بيانات تطبيق معين
     * بمساحة الـ SD. هذا مكافئ لزر "Create Link" في Link2SD.
     */
    fun createLink(packageName: String, sdMountPoint: String): Boolean {
        val internalPath = "/data/data/$packageName"
        val sdPath = "$sdMountPoint/data/$packageName"

        val commands = listOf(
            "mkdir -p $sdPath",
            "cp -a $internalPath/. $sdPath/",
            "rm -rf $internalPath",
            "ln -s $sdPath $internalPath",
            // على أنظمة أحدث تُستخدم mount --bind بدلاً من symlink أحياناً:
            // "mount --bind $sdPath $internalPath"
        )

        for (cmd in commands) {
            val result = Shell.cmd(cmd).exec()
            if (!result.isSuccess) return false
        }
        return true
    }

    /** يزيل الربط ويعيد البيانات إلى الذاكرة الداخلية (Remove Link) */
    fun removeLink(packageName: String, sdMountPoint: String): Boolean {
        val internalPath = "/data/data/$packageName"
        val sdPath = "$sdMountPoint/data/$packageName"

        val commands = listOf(
            "rm -f $internalPath",
            "mkdir -p $internalPath",
            "cp -a $sdPath/. $internalPath/",
            "rm -rf $sdPath"
        )

        for (cmd in commands) {
            val result = Shell.cmd(cmd).exec()
            if (!result.isSuccess) return false
        }
        return true
    }

    /** يحسب حجم مجلد معيّن بالبايت (لاستخدامه في عرض حجم APK/Data) */
    fun getFolderSizeBytes(path: String): Long {
        val output = runCommand("du -sb $path 2>/dev/null")
        return output.firstOrNull()?.split("\\s+".toRegex())?.firstOrNull()?.toLongOrNull() ?: 0L
    }
}
