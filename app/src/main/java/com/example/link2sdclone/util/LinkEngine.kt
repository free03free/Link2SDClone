package com.example.link2sdclone.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.example.link2sdclone.R
import com.example.link2sdclone.util.PrivilegedShell.Mode

/**
 * محرك الربط (Root فقط): ينسخ ملفات التطبيق إلى القسم الثاني ويستبدلها برابط رمزي.
 * لكل ملف: نسخ ← تحقق ← تبديل ← تحقق عبر الرابط ← حذف الأصل. أي فشل يُرجع الملف كما كان.
 * كل الدوال تُستدعى خارج الخيط الرئيسي.
 */
object LinkEngine {

    data class Readiness(
        val ok: Boolean,
        val message: String,
        val mountPoint: String?,
        val selinuxEnforcing: Boolean
    )

    data class Outcome(val ok: Boolean, val message: String)

    private const val PREF_LINKED = "l2sd_linked"
    private const val LONG_MS = 1_800_000L
    private val PATH_OK = Regex("[A-Za-z0-9_.=+~@/-]+")
    private val PKG_OK = Regex("[A-Za-z0-9_.]+")
    private val CTX_OK = Regex("u:object_r:[A-Za-z0-9_]+:s0[A-Za-z0-9_:,]*")

    private fun q(s: String) = "'" + s + "'"

    private fun sh(cmd: String, ms: Long = 60_000) = PrivilegedShell.run(Mode.ROOT, cmd, ms)

    /** يشغّل سكربت داخل subshell ويعيد رمز الخروج الحقيقي. */
    private fun runScript(script: String): Int {
        val r = sh("(\n$script\n)\necho L2SD_RC=\$?", LONG_MS)
        return Regex("L2SD_RC=(\\d+)").find(r.output)?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    private fun isDirName(name: String) = name == "oat" || name == "lib"

    fun linkedPackages(ctx: Context): Set<String> =
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .getStringSet(PREF_LINKED, emptySet<String>()) ?: emptySet<String>()

    private fun mark(ctx: Context, pkg: String, linked: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        val set = HashSet<String>(sp.getStringSet(PREF_LINKED, emptySet<String>()) ?: emptySet<String>())
        if (linked) set.add(pkg) else set.remove(pkg)
        sp.edit().putStringSet(PREF_LINKED, set).apply()
    }

    /** جاهزية الجهاز: Root ثم قسم ext مركّب وقابل للكتابة. لا ينقل شيئًا. */
    fun check(ctx: Context): Readiness {
        if (PrivilegedShell.detect() != Mode.ROOT) {
            return Readiness(false, ctx.getString(R.string.lk_need_root), null, false)
        }
        val manual = android.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
            .getString("pref_manual_sd2_path", null)?.trim()
        if (!manual.isNullOrEmpty()) {
            if (!PATH_OK.matches(manual)) {
                return Readiness(false, ctx.getString(R.string.lk_bad_path, manual), null, false)
            }
            if (sh("[ -d ${q(manual)} ]").ok) {
                val probeM = q("$manual/.l2sd_w")
                if (!sh("touch $probeM && rm -f $probeM").ok) {
                    return Readiness(false, ctx.getString(R.string.lk_not_writable, manual), null, false)
                }
                val enforcingM = sh("getenforce").output.trim().equals("Enforcing", true)
                return Readiness(true, "", manual, enforcingM)
            }
            return Readiness(false, ctx.getString(R.string.fin2_manual_path_missing, manual), null, false)
        }
        val mounts = sh("grep -E ' ext[234] ' /proc/mounts").output
        val mp = mounts.lineSequence()
            .map { it.trim().split(Regex("\\s+")) }
            .filter { it.size >= 3 }
            .map { it[1] }
            .firstOrNull { it.contains("sdext", true) || it.contains("sd-ext", true) }
        if (mp == null || !PATH_OK.matches(mp)) {
            return Readiness(false, ctx.getString(R.string.lk_no_partition), null, false)
        }
        val probe = q("$mp/.l2sd_w")
        if (!sh("touch $probe && rm -f $probe").ok) {
            return Readiness(false, ctx.getString(R.string.lk_not_writable, mp), null, false)
        }
        val enforcing = sh("getenforce").output.trim().equals("Enforcing", true)
        return Readiness(true, "", mp, enforcing)
    }

    private fun listNames(dir: String): List<String> =
        sh("ls -1A ${q(dir)}").output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains('/') && PATH_OK.matches(it) }
            .toList()

    private fun freeKb(mp: String): Long {
        val line = sh("df -k ${q(mp)}").output.lines().lastOrNull { it.isNotBlank() } ?: return -1
        val f = line.trim().split(Regex("\\s+"))
        return f.getOrNull(if (f.size >= 6) 3 else 2)?.toLongOrNull() ?: -1
    }

    private fun neededKb(dir: String, names: List<String>): Long {
        if (names.isEmpty()) return 0
        val out = sh("cd ${q(dir)} && du -sk ${names.joinToString(" ") { q(it) }}").output
        return out.lineSequence()
            .mapNotNull { it.trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() }
            .sum()
    }

    fun link(ctx: Context, pkg: String, apkPath: String, mp: String, apk: Boolean = true, dex: Boolean? = null, lib: Boolean? = null): Outcome {
        if (!PKG_OK.matches(pkg) || !PATH_OK.matches(apkPath) || !PATH_OK.matches(mp)) {
            return Outcome(false, ctx.getString(R.string.lk_bad_path, pkg))
        }
        if (!apkPath.startsWith("/data/app/")) {
            return Outcome(false, ctx.getString(R.string.lk_not_data_app, pkg))
        }
        val dir = apkPath.substringBeforeLast('/')
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        val wantDex = dex ?: sp.getBoolean("auto_link_dex", false)
        val wantLib = lib ?: sp.getBoolean("auto_link_lib", false)

        val names = listNames(dir)
            .filter { (apk && it.endsWith(".apk")) || (wantDex && it == "oat") || (wantLib && it == "lib") }
            .filter { !sh("test -L ${q("$dir/$it")}").ok }
        if (names.isEmpty()) return Outcome(false, ctx.getString(R.string.lk_already, pkg))

        val need = neededKb(dir, names)
        val free = freeKb(mp)
        if (free >= 0 && free < need * 12 / 10 + 10240) {
            return Outcome(false, ctx.getString(R.string.lk_no_space, pkg))
        }

        val done = mutableListOf<String>()
        for (n in names) {
            val code = linkItem(dir, mp, pkg, n)
            if (code == 0) {
                done.add(n)
            } else if (code != 3) {
                done.forEach { unlinkItem(dir, mp, it) }
                return Outcome(false, ctx.getString(R.string.lk_item_failed, pkg, n, code))
            }
        }
        if (done.isEmpty()) return Outcome(false, ctx.getString(R.string.lk_already, pkg))
        mark(ctx, pkg, true)
        return Outcome(true, "")
    }

    fun unlink(ctx: Context, pkg: String, apkPath: String, mp: String): Outcome {
        if (!PKG_OK.matches(pkg) || !PATH_OK.matches(apkPath) || !PATH_OK.matches(mp)) {
            return Outcome(false, ctx.getString(R.string.lk_bad_path, pkg))
        }
        val dir = apkPath.substringBeforeLast('/')
        val names = listNames(dir).filter { it.endsWith(".apk") || isDirName(it) }
        var any = false
        for (n in names) {
            val code = unlinkItem(dir, mp, n)
            if (code == 0) {
                any = true
            } else if (code != 3) {
                return Outcome(false, ctx.getString(R.string.lk_item_failed, pkg, n, code))
            }
        }
        if (!any) {
            mark(ctx, pkg, false)
            return Outcome(false, ctx.getString(R.string.lk_not_linked, pkg))
        }
        sh("rmdir ${q("$mp/$pkg")} 2>/dev/null")
        mark(ctx, pkg, false)
        return Outcome(true, "")
    }

    /** رموز الخروج: 0 تم، 3 تخطّي، 4 مسار، 5 فشل النسخ، 6 فشل التحقق، 7-9 فشل التبديل (أُرجع الأصل)، 10-11 بقايا سابقة. */
    private fun linkItem(dir: String, mp: String, pkg: String, name: String): Int {
        val s = "$dir/$name"
        val b = "$s.l2sd_bak"
        val dstDir = "$mp/$pkg"
        val d = "$dstDir/$name"
        val isDir = isDirName(name)

        val ctxRaw = sh("ls -dZ ${q(s)}").output.trim().split(Regex("\\s+"))
            .firstOrNull { CTX_OK.matches(it) } ?: ""
        val chcon = if (ctxRaw.isNotEmpty()) "chcon -R $ctxRaw ${q(d)} >/dev/null 2>&1 || true" else "true"
        val same = if (isDir) "diff -rq" else "cmp -s"
        val viaLink = if (isDir) "diff -rq ${q(b)} ${q("$s/")}" else "cmp -s ${q(b)} ${q(s)}"

        val script = """
            [ -e ${q(s)} ] && [ ! -L ${q(s)} ] || exit 3
            [ ! -e ${q(b)} ] || exit 10
            rm -rf ${q(d)}
            mkdir -p ${q(dstDir)} || exit 4
            cp -a ${q(s)} ${q(d)} || { rm -rf ${q(d)}; exit 5; }
            $chcon
            $same ${q(s)} ${q(d)} >/dev/null 2>&1 || { rm -rf ${q(d)}; exit 6; }
            mv ${q(s)} ${q(b)} || { rm -rf ${q(d)}; exit 7; }
            ln -s ${q(d)} ${q(s)} || { mv ${q(b)} ${q(s)}; rm -rf ${q(d)}; exit 8; }
            $viaLink >/dev/null 2>&1 || { rm -f ${q(s)}; mv ${q(b)} ${q(s)}; rm -rf ${q(d)}; exit 9; }
            rm -rf ${q(b)}
            exit 0
        """.trimIndent()
        return runScript(script)
    }

    private fun unlinkItem(dir: String, mp: String, name: String): Int {
        val s = "$dir/$name"
        val r = sh("readlink ${q(s)}")
        val d = r.output.trim()
        if (!r.ok || d.isEmpty()) return 3
        if (!d.startsWith("$mp/") || !PATH_OK.matches(d) || d.contains("..")) return 4
        val b = "$s.l2sd_tmp"
        val same = if (isDirName(name)) "diff -rq" else "cmp -s"

        val script = """
            [ -e ${q(d)} ] || exit 5
            [ ! -e ${q(b)} ] || exit 10
            cp -a ${q(d)} ${q(b)} || { rm -rf ${q(b)}; exit 6; }
            $same ${q(d)} ${q(b)} >/dev/null 2>&1 || { rm -rf ${q(b)}; exit 7; }
            rm -f ${q(s)} || { rm -rf ${q(b)}; exit 8; }
            mv ${q(b)} ${q(s)} || { ln -s ${q(d)} ${q(s)}; rm -rf ${q(b)}; exit 9; }
            rm -rf ${q(d)}
            exit 0
        """.trimIndent()
        return runScript(script)
    }

    // ---- نسخ (لا نقل) مجلدات Android/data وAndroid/obb الخارجية، وحذف يدوي منفصل لاحقًا (Root فقط) ----
    // غير مُجرَّب على جهاز حقيقي. يعمل فقط على التطبيقات المربوطة مسبقًا (linkedPackages) لتقليل نطاق الخطر.
    private const val PREF_COPIED_EXT = "l2sd_copied_ext"

    fun copiedExternalPackages(ctx: Context): Set<String> =
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .getStringSet(PREF_COPIED_EXT, emptySet<String>()) ?: emptySet<String>()

    private fun markCopiedExt(ctx: Context, pkg: String, copied: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        val set = HashSet<String>(sp.getStringSet(PREF_COPIED_EXT, emptySet<String>()) ?: emptySet<String>())
        if (copied) set.add(pkg) else set.remove(pkg)
        sp.edit().putStringSet(PREF_COPIED_EXT, set).apply()
    }

    /** نسخ فقط: لا يلمس المصدر إطلاقًا، قابل للتكرار بأمان. */
    private fun copyKind(pkg: String, mp: String, kind: String): Int {
        val src = "/data/media/0/Android/$kind/$pkg"
        val dest = "$mp/external/$pkg/$kind"
        val script = """
            [ -d ${q(src)} ] || exit 3
            find ${q(src)} -mindepth 1 -print -quit | grep -q . || exit 3
            mkdir -p ${q(dest)} || exit 4
            cp -a ${q(src)}/. ${q(dest)}/ || exit 5
            diff -rq ${q(src)} ${q(dest)} >/dev/null 2>&1 || exit 6
            exit 0
        """.trimIndent()
        return runScript(script)
    }

    fun copyExternalFolders(ctx: Context, pkg: String, mp: String): Outcome {
        if (!PKG_OK.matches(pkg) || !PATH_OK.matches(mp)) return Outcome(false, ctx.getString(R.string.lk_bad_path, pkg))
        var any = false
        for (kind in listOf("data", "obb")) {
            val code = copyKind(pkg, mp, kind)
            if (code == 0) any = true
            else if (code != 3) return Outcome(false, ctx.getString(R.string.lk_item_failed, pkg, kind, code))
        }
        if (!any) return Outcome(false, ctx.getString(R.string.fin3_nothing_to_copy, pkg))
        markCopiedExt(ctx, pkg, true)
        return Outcome(true, "")
    }

    /** حذف يدوي: يعيد التحقق من مطابقة النسخة قبل حذف الأصل، ثم يربط النسخة مكانه. يتراجع تلقائيًا عند أي فشل. */
    private fun finalizeKind(pkg: String, mp: String, kind: String): Int {
        val src = "/data/media/0/Android/$kind/$pkg"
        val dest = "$mp/external/$pkg/$kind"
        val bak = "$src.l2sd_bak_ext"
        val script = """
            [ -d ${q(dest)} ] || exit 3
            find ${q(dest)} -mindepth 1 -print -quit | grep -q . || exit 3
            mount | grep -qF ${q(" on $src ")} && exit 3
            [ -d ${q(src)} ] || exit 3
            diff -rq ${q(src)} ${q(dest)} >/dev/null 2>&1 || exit 6
            [ ! -e ${q(bak)} ] || exit 10
            mv ${q(src)} ${q(bak)} || exit 7
            mkdir -p ${q(src)}
            mount --bind ${q(dest)} ${q(src)} || { rm -rf ${q(src)}; mv ${q(bak)} ${q(src)}; exit 8; }
            diff -rq ${q(src)} ${q(dest)} >/dev/null 2>&1 || { umount ${q(src)} 2>/dev/null; rm -rf ${q(src)}; mv ${q(bak)} ${q(src)}; exit 9; }
            rm -rf ${q(bak)}
            exit 0
        """.trimIndent()
        return runScript(script)
    }

    fun finalizeExternalFolders(ctx: Context, pkg: String, mp: String): Outcome {
        if (!PKG_OK.matches(pkg) || !PATH_OK.matches(mp)) return Outcome(false, ctx.getString(R.string.lk_bad_path, pkg))
        var any = false
        for (kind in listOf("data", "obb")) {
            val code = finalizeKind(pkg, mp, kind)
            if (code == 0) any = true
            else if (code == 6) return Outcome(false, ctx.getString(R.string.fin3_copy_outdated, pkg, kind))
            else if (code != 3) return Outcome(false, ctx.getString(R.string.lk_item_failed, pkg, kind, code))
        }
        if (!any) return Outcome(false, ctx.getString(R.string.fin3_nothing_to_finalize, pkg))
        return Outcome(true, "")
    }
}
