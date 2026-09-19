package com.example.link2sdclone.util

import android.content.Context
import com.example.link2sdclone.R
import com.example.link2sdclone.util.PrivilegedShell.Mode

/** عمليات جماعية فوق LinkEngine (Root فقط). تُستدعى خارج الخيط الرئيسي. */
object LinkBatch {

    private val PKG_OK = Regex("[A-Za-z0-9_.]+")
    private val PATH_OK = Regex("[A-Za-z0-9_.=+~@/-]+")
    private val DEV_OK = Regex("/dev/block/[A-Za-z0-9_./:,-]+")

    private fun sh(cmd: String, ms: Long = 60_000) = PrivilegedShell.run(Mode.ROOT, cmd, ms)

    /** يعيد ربط التطبيقات المحفوظة في قائمة المربوطين (مثلًا بعد تحديثها). */
    fun relinkAll(ctx: Context, mp: String, apk: Boolean, dex: Boolean?, lib: Boolean?): String {
        val pkgs = LinkEngine.linkedPackages(ctx).sorted()
        if (pkgs.isEmpty()) return ctx.getString(R.string.fin_no_linked)
        val pm = ctx.packageManager
        var done = 0
        var skipped = 0
        val fails = StringBuilder()
        for (p in pkgs) {
            val src = try { pm.getApplicationInfo(p, 0).sourceDir } catch (e: Exception) { null }
            if (src == null) {
                skipped++
                continue
            }
            val r = LinkEngine.link(ctx, p, src, mp, apk, dex, lib)
            if (r.ok) {
                done++
            } else if (r.message == ctx.getString(R.string.lk_already, p)) {
                skipped++
            } else {
                fails.append("• ").append(p).append(": ").append(r.message).append("\n")
            }
        }
        val head = ctx.getString(R.string.fin_relink_result, done, skipped)
        return if (fails.isEmpty()) head else head + "\n\n" + fails.toString().trim()
    }

    /** ينسخ (بلا حذف) مجلدات Android/data وAndroid/obb للتطبيقات المربوطة مسبقًا. آمن وقابل للتكرار. */
    fun copyAllExternal(ctx: Context, mp: String): String {
        val pkgs = LinkEngine.linkedPackages(ctx).sorted()
        if (pkgs.isEmpty()) return ctx.getString(R.string.fin_no_linked)
        var done = 0
        var skipped = 0
        val fails = StringBuilder()
        for (p in pkgs) {
            val r = LinkEngine.copyExternalFolders(ctx, p, mp)
            if (r.ok) done++
            else if (r.message == ctx.getString(R.string.fin3_nothing_to_copy, p)) skipped++
            else fails.append("• ").append(p).append(": ").append(r.message).append("\n")
        }
        val head = ctx.getString(R.string.fin_relink_result, done, skipped)
        return if (fails.isEmpty()) head else head + "\n\n" + fails.toString().trim()
    }

    /** يحذف الأصل بعد التحقق ويربط النسخة مكانه، فقط للتطبيقات التي نُسخت مسبقًا. */
    fun finalizeAllExternal(ctx: Context, mp: String): String {
        val pkgs = LinkEngine.copiedExternalPackages(ctx).sorted()
        if (pkgs.isEmpty()) return ctx.getString(R.string.fin3_no_copied)
        var done = 0
        var skipped = 0
        val fails = StringBuilder()
        for (p in pkgs) {
            val r = LinkEngine.finalizeExternalFolders(ctx, p, mp)
            if (r.ok) done++
            else if (r.message == ctx.getString(R.string.fin3_nothing_to_finalize, p)) skipped++
            else fails.append("• ").append(p).append(": ").append(r.message).append("\n")
        }
        val head = ctx.getString(R.string.fin_relink_result, done, skipped)
        return if (fails.isEmpty()) head else head + "\n\n" + fails.toString().trim()
    }

    class BootPlan(val ok: Boolean, val message: String, val path: String, val content: String)

    /** يجهّز سكربت إقلاع (Magisk/KernelSU) يركّب القسم الثاني مبكرًا. لا يكتب شيئًا. */
    fun planBootScript(ctx: Context, mp: String): BootPlan {
        val path = "/data/adb/post-fs-data.d/l2sd_mount.sh"
        fun bad(msg: String) = BootPlan(false, msg, path, "")
        if (!PATH_OK.matches(mp)) return bad(ctx.getString(R.string.lk_bad_path, mp))
        if (!sh("test -d /data/adb/modules").ok) return bad(ctx.getString(R.string.fin_script_no_magisk))
        val line = sh("grep -F ' $mp ' /proc/mounts").output.lineSequence().firstOrNull { it.isNotBlank() }
            ?: return bad(ctx.getString(R.string.lk_no_partition))
        val f = line.trim().split(Regex("\\s+"))
        val dev = f.getOrNull(0) ?: ""
        val fs = f.getOrNull(2) ?: ""
        if (!DEV_OK.matches(dev) || dev.contains("vold") || dev.contains("public:")) {
            return bad(ctx.getString(R.string.fin_script_bad_dev, dev))
        }
        if (!Regex("ext[234]").matches(fs)) return bad(ctx.getString(R.string.lk_no_partition))
        val content = listOf(
            "#!/system/bin/sh",
            "# Link2SD Clone: mounts the second partition early. Delete this file to disable.",
            "DEV=$dev",
            "MP=$mp",
            "i=0",
            "while [ ! -b \"\$DEV\" ] && [ \$i -lt 20 ]; do sleep 1; i=\$((i+1)); done",
            "[ -b \"\$DEV\" ] || exit 0",
            "mkdir -p \"\$MP\"",
            "grep -q \" \$MP \" /proc/mounts || mount -t $fs -o rw,noatime \"\$DEV\" \"\$MP\"",
            "exit 0"
        ).joinToString("\n")
        return BootPlan(true, ctx.getString(R.string.fin_script_confirm, path, dev, mp), path, content)
    }

    fun writeBootScript(plan: BootPlan): Boolean {
        val p = "'" + plan.path + "'"
        val cmd = "mkdir -p /data/adb/post-fs-data.d\n" +
            "cat > " + p + " << 'L2SD_EOF'\n" + plan.content + "\nL2SD_EOF\n" +
            "chmod 755 " + p + " && test -x " + p
        return sh(cmd).ok
    }

    /** مجلدات في القسم الثاني لتطبيقات غير مثبتة إطلاقًا. null = تعذّر التحقق فلا يُحذف شيء. */
    fun findOrphans(ctx: Context, mp: String): List<String>? {
        if (!PATH_OK.matches(mp)) return null
        val known = sh("pm list packages -u").output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .toSet()
        if (ctx.packageName !in known) return null
        val names = sh("ls -1A '$mp'").output.lineSequence()
            .map { it.trim() }
            .filter { it.contains('.') && PKG_OK.matches(it) && it !in known }
            .toList()
        return names.filter { sh("test -d '$mp/$it'").ok }
    }

    fun deleteOrphans(mp: String, names: List<String>): Int {
        if (!PATH_OK.matches(mp)) return 0
        var n = 0
        for (name in names) {
            if (!PKG_OK.matches(name) || !name.contains('.')) continue
            if (sh("rm -rf '$mp/$name'").ok) n++
        }
        return n
    }
}
