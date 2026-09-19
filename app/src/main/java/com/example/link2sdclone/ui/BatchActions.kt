package com.example.link2sdclone.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.widget.PopupWindowCompat
import com.example.link2sdclone.R
import com.example.link2sdclone.model.AppEntry
import com.example.link2sdclone.util.PrivilegedShell
import com.example.link2sdclone.util.PrivilegedShell.Mode
import java.io.File

/** قائمة مسطّحة بنص فقط (مثل الأصلي): بيضاء، بلا فواصل، ملتصقة بأسفل الشريط وبالحافة اليمنى. */
fun showActionPopup(
    context: Context,
    anchor: View,
    titles: List<String>,
    onSelected: (Int) -> Unit
) {
    val metrics = context.resources.displayMetrics
    fun dp(v: Int): Int = (v * metrics.density).toInt()

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setBackgroundColor(Color.WHITE)
        setPadding(0, dp(4), 0, dp(4))
    }
    val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
    val rowBg = ta.getResourceId(0, 0)
    ta.recycle()

    lateinit var popup: PopupWindow

    titles.forEachIndexed { i, title ->
        val row = TextView(context).apply {
            text = title
            textSize = 16f
            setTextColor(Color.parseColor("#212121"))
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            minimumHeight = dp(48)
            setPadding(dp(16), 0, dp(16), 0)
            isClickable = true
            if (rowBg != 0) setBackgroundResource(rowBg)
            setOnClickListener {
                popup.dismiss()
                onSelected(i)
            }
        }
        container.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    container.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    val popupWidth = minOf(
        maxOf(container.measuredWidth, (metrics.widthPixels * 0.55f).toInt()),
        metrics.widthPixels
    )
    val popupHeight = minOf(container.measuredHeight, (metrics.heightPixels * 0.8f).toInt())

    val scroll = ScrollView(context).apply {
        addView(container, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    popup = PopupWindow(scroll, popupWidth, popupHeight, true).apply {
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(Color.WHITE))
        if (android.os.Build.VERSION.SDK_INT >= 21) elevation = dp(8).toFloat()
    }

    var host: View = anchor
    var v: View? = anchor.parent as? View
    while (v != null) {
        if (v is androidx.appcompat.widget.Toolbar) { host = v; break }
        v = v.parent as? View
    }
    PopupWindowCompat.showAsDropDown(popup, host, 0, 0, Gravity.RIGHT)
}

/** إجراءات قائمة التحديد الجماعي. التجميد وإبطاله في MainActivity. */
object BatchActions {

    const val REQ_QUEUE = 5510
    private val queue = java.util.ArrayDeque<Intent>()

    fun labels(ctx: Context): List<String> = listOf(
        R.string.st3_b_link, R.string.st3_b_unlink,
        R.string.st3_b_move_sd, R.string.st3_b_move_phone,
        R.string.st3_b_freeze, R.string.st3_b_unfreeze,
        R.string.st3_b_reinstall, R.string.st3_b_delete,
        R.string.st3_b_clear_data, R.string.st3_b_clear_cache,
        R.string.st3_b_share
    ).map { ctx.getString(it) }

    fun execute(activity: Activity, index: Int, apps: List<AppEntry>, onDone: () -> Unit) {
        when (index) {
            0 -> linkAction(activity, apps, true, onDone)
            1 -> linkAction(activity, apps, false, onDone)
            2 -> move(activity, apps, true, onDone)
            3 -> move(activity, apps, false, onDone)
            6 -> reinstall(activity, apps, onDone)
            7 -> delete(activity, apps, onDone)
            8 -> clearData(activity, apps, onDone)
            9 -> clearCache(activity, apps, onDone)
            10 -> share(activity, apps, onDone)
        }
    }

    // ---- طابور نوافذ النظام (حذف / إعادة تثبيت): واحدة بعد الأخرى ----
    fun nextInQueue(activity: Activity) {
        val i = queue.poll() ?: run { com.example.link2sdclone.util.ReloadFlag.pending = true; return }
        try {
            activity.startActivityForResult(i, REQ_QUEUE)
        } catch (e: Exception) {
            nextInQueue(activity)
        }
    }

    private fun startQueue(activity: Activity, intents: List<Intent>) {
        queue.clear()
        queue.addAll(intents)
        nextInQueue(activity)
    }

    // ---- أدوات مساعدة ----
    private fun info(activity: Activity, msgRes: Int) {
        AlertDialog.Builder(activity)
            .setMessage(msgRes)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun infoText(activity: Activity, msg: String) {
        AlertDialog.Builder(activity)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun withAccess(activity: Activity, block: (Mode) -> Unit) {
        Thread {
            val m = PrivilegedShell.detect()
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                when (m) {
                    Mode.ROOT, Mode.SHIZUKU -> block(m)
                    Mode.SHIZUKU_NEEDS_PERMISSION -> {
                        PrivilegedShell.requestShizuku(5505)
                        Toast.makeText(activity, R.string.st3_shizuku_asked, Toast.LENGTH_LONG).show()
                    }
                    Mode.NONE -> info(activity, R.string.st3_need_access)
                }
            }
        }.start()
    }

    private fun runEach(
        activity: Activity, mode: Mode, apps: List<AppEntry>,
        cmd: (AppEntry) -> String, onDone: () -> Unit
    ) {
        Thread {
            var ok = 0
            val fails = StringBuilder()
            apps.forEach { a ->
                val r = PrivilegedShell.run(mode, cmd(a), 120_000)
                if (r.ok && !r.output.contains("Failure", true) && !r.output.contains("Error", true)) {
                    ok++
                } else {
                    fails.append("• ").append(a.label).append(": ").append(r.output.take(120)).append("\n")
                }
            }
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                val head = activity.getString(R.string.st3_batch_result, ok, apps.size)
                infoText(activity, if (fails.isEmpty()) head else head + "\n\n" + fails.toString().trim())
                onDone()
            }
        }.start()
    }

    private fun runOnce(activity: Activity, mode: Mode, cmd: String, onDone: () -> Unit) {
        Thread {
            val r = PrivilegedShell.run(mode, cmd, 120_000)
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                val head = activity.getString(if (r.ok) R.string.rt_done else R.string.rt_failed)
                infoText(activity, if (r.output.isNotBlank()) head + "\n\n" + r.output else head)
                onDone()
            }
        }.start()
    }

    private fun others(activity: Activity, apps: List<AppEntry>): List<AppEntry> =
        apps.filter { it.packageName != activity.packageName }

    // ---- الإجراءات ----
    private fun move(activity: Activity, apps: List<AppEntry>, toSd: Boolean, onDone: () -> Unit) {
        val list = others(activity, apps)
        if (list.isEmpty()) {
            Toast.makeText(activity, R.string.batch_no_selection, Toast.LENGTH_SHORT).show()
            return
        }
        withAccess(activity) { m ->
            Thread {
                var target = "internal"
                if (toSd) {
                    val out = PrivilegedShell.run(m, "sm list-volumes private").output
                    val line = out.lineSequence().map { it.trim() }.firstOrNull { it.contains("mounted") }
                    val uuid = line?.split(Regex("\\s+"))?.lastOrNull()
                    if (uuid.isNullOrBlank()) {
                        activity.runOnUiThread {
                            if (!activity.isFinishing) info(activity, R.string.st3_no_adopted)
                        }
                        return@Thread
                    }
                    target = uuid
                }
                val t = target
                activity.runOnUiThread {
                    if (activity.isFinishing) return@runOnUiThread
                    runEach(activity, m, list, { "pm move-package ${it.packageName} $t" }, onDone)
                }
            }.start()
        }
    }

    private fun reinstall(activity: Activity, apps: List<AppEntry>, onDone: () -> Unit) {
        try {
            val intents = apps.map { a ->
                val uri = FileProvider.getUriForFile(
                    activity, "${activity.packageName}.fileprovider", File(a.apkPath))
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            startQueue(activity, intents)
            onDone()
        } catch (e: Exception) {
            Toast.makeText(activity, R.string.freeze_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun delete(activity: Activity, apps: List<AppEntry>, onDone: () -> Unit) {
        val list = others(activity, apps)
        if (list.isEmpty()) {
            Toast.makeText(activity, R.string.batch_no_selection, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(activity)
            .setMessage(activity.getString(R.string.st3_confirm_delete, list.size))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                Thread {
                    val m = PrivilegedShell.detect()
                    activity.runOnUiThread {
                        if (activity.isFinishing) return@runOnUiThread
                        if (m == Mode.ROOT || m == Mode.SHIZUKU) {
                            runEach(activity, m, list, { "pm uninstall ${it.packageName}" }, onDone)
                        } else {
                            startQueue(activity, list.map {
                                Intent(Intent.ACTION_DELETE, Uri.parse("package:${it.packageName}"))
                            })
                            onDone()
                        }
                    }
                }.start()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun clearData(activity: Activity, apps: List<AppEntry>, onDone: () -> Unit) {
        val list = others(activity, apps)
        if (list.isEmpty()) {
            Toast.makeText(activity, R.string.batch_no_selection, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(activity)
            .setMessage(activity.getString(R.string.st3_confirm_clear_data, list.size))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                withAccess(activity) { m ->
                    runEach(activity, m, list, { "pm clear ${it.packageName}" }, onDone)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun clearCache(activity: Activity, apps: List<AppEntry>, onDone: () -> Unit) {
        withAccess(activity) { m ->
            if (m == Mode.ROOT) {
                runEach(activity, m, apps, { a ->
                    "rm -rf /data/data/${a.packageName}/cache/* /data/data/${a.packageName}/code_cache/* /sdcard/Android/data/${a.packageName}/cache/*"
                }, onDone)
            } else {
                AlertDialog.Builder(activity)
                    .setMessage(R.string.st3_confirm_clear_cache_shizuku)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        runOnce(activity, m, "pm trim-caches 999G", onDone)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    // ---- الربط وإزالته (Root فقط، مع فحص جاهزية وتأكيد صريح) ----
    private fun linkAction(activity: Activity, apps: List<AppEntry>, link: Boolean, onDone: () -> Unit) {
        val list = others(activity, apps).filter { !it.isSystemApp }
        if (list.isEmpty()) {
            infoText(activity, activity.getString(R.string.lk_none_eligible))
            return
        }
        val appCtx = activity.applicationContext
        Thread {
            val ready = com.example.link2sdclone.util.LinkEngine.check(appCtx)
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                val mp = ready.mountPoint
                if (!ready.ok || mp == null) {
                    infoText(activity, ready.message)
                    return@runOnUiThread
                }
                var msg = activity.getString(
                    if (link) R.string.lk_confirm_link else R.string.lk_confirm_unlink, list.size, mp)
                if (link && ready.selinuxEnforcing) msg += "\n\n" + activity.getString(R.string.lk_selinux_warn)
                AlertDialog.Builder(activity)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok) { _, _ -> runLink(activity, list, link, mp, onDone) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }.start()
    }

    private fun runLink(activity: Activity, list: List<AppEntry>, link: Boolean, mp: String, onDone: () -> Unit) {
        Toast.makeText(activity, R.string.rt_running, Toast.LENGTH_SHORT).show()
        val appCtx = activity.applicationContext
        Thread {
            var ok = 0
            val fails = StringBuilder()
            list.forEach { a ->
                val r = if (link) com.example.link2sdclone.util.LinkEngine.link(appCtx, a.packageName, a.apkPath, mp)
                else com.example.link2sdclone.util.LinkEngine.unlink(appCtx, a.packageName, a.apkPath, mp)
                if (r.ok) ok++ else fails.append("• ").append(a.label).append(": ").append(r.message).append("\n")
            }
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                val head = activity.getString(R.string.st3_batch_result, ok, list.size)
                infoText(activity, if (fails.isEmpty()) head else head + "\n\n" + fails.toString().trim())
                onDone()
            }
        }.start()
    }

    private fun share(activity: Activity, apps: List<AppEntry>, onDone: () -> Unit) {
        try {
            val uris = ArrayList<Uri>()
            apps.forEach {
                uris.add(FileProvider.getUriForFile(
                    activity, "${activity.packageName}.fileprovider", File(it.apkPath)))
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/vnd.android.package-archive"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, null))
            onDone()
        } catch (e: Exception) {
            Toast.makeText(activity, R.string.freeze_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
