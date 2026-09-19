package com.example.link2sdclone.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.example.link2sdclone.R
import com.example.link2sdclone.util.PrivilegedShell
import com.example.link2sdclone.util.PrivilegedShell.Mode

class RootToolsActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private var mode = Mode.NONE

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val toolbar = Toolbar(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@RootToolsActivity, R.color.colorPrimary))
            setNavigationIcon(R.drawable.ic_back)
            setTitle(R.string.rt_title)
            setTitleTextColor(Color.WHITE)
            setNavigationOnClickListener { finish() }
        }
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        statusView = TextView(this).apply {
            setText(R.string.rt_mode_checking)
            textSize = 14f
            setTextColor(Color.parseColor("#616161"))
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        root.addView(statusView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        addRow(list, R.string.rt_item_reload) { com.example.link2sdclone.util.ReloadFlag.pending = true; finish() }
        addRow(list, R.string.rt_item_recreate_scripts) { partitionAction(R.string.rt_item_recreate_scripts) }
        addRow(list, R.string.rt_item_relink_apps) { partitionAction(R.string.rt_item_relink_apps) }
        addRow(list, R.string.rt_item_rebind_folders) { partitionAction(R.string.rt_item_rebind_folders) }
        addRow(list, R.string.rt_item_relink_lib) { partitionAction(R.string.rt_item_relink_lib) }
        addRow(list, R.string.rt_item_link_dalvik) { partitionAction(R.string.rt_item_link_dalvik) }
        addRow(list, R.string.rt_item_clean_sd2) { partitionAction(R.string.rt_item_clean_sd2) }
        addRow(list, R.string.rt_item_clean_dalvik) {
            requireAccess(true) { showMessage(R.string.rt_dalvik_not_impl, R.string.rt_item_clean_dalvik) }
        }
        addRow(list, R.string.rt_item_clear_caches) {
            requireAccess(false) { m -> execute(m, "pm trim-caches 999G", R.string.rt_item_clear_caches) }
        }
        addRow(list, R.string.rt_item_reboot) {
            requireAccess(false) { m ->
                AlertDialog.Builder(this)
                    .setMessage(R.string.rt_reboot_confirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val cmd = if (m == Mode.ROOT) "reboot" else "svc power reboot"
                        execute(m, cmd, R.string.rt_item_reboot)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }

        root.addView(ScrollView(this).apply { addView(list) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshMode()
    }

    private fun refreshMode() {
        Thread {
            val m = PrivilegedShell.detect()
            runOnUiThread {
                mode = m
                statusView.setText(when (m) {
                    Mode.ROOT -> R.string.rt_mode_root
                    Mode.SHIZUKU -> R.string.rt_mode_shizuku
                    Mode.SHIZUKU_NEEDS_PERMISSION -> R.string.rt_mode_shizuku_perm
                    Mode.NONE -> R.string.rt_mode_none
                })
            }
        }.start()
    }

    private fun addRow(parent: LinearLayout, titleRes: Int, onClick: () -> Unit) {
        val row = TextView(this).apply {
            setText(titleRes)
            textSize = 16f
            setTextColor(Color.parseColor("#212121"))
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            isClickable = true
            val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            val bg = ta.getResourceId(0, 0)
            ta.recycle()
            if (bg != 0) setBackgroundResource(bg)
            setOnClickListener { onClick() }
        }
        parent.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val line = View(this).apply { setBackgroundColor(Color.parseColor("#CCCCCC")) }
        parent.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
    }

    private fun requireAccess(needRoot: Boolean, block: (Mode) -> Unit) {
        when {
            mode == Mode.ROOT -> block(mode)
            needRoot -> showMessage(R.string.rt_need_root)
            mode == Mode.SHIZUKU -> block(mode)
            mode == Mode.SHIZUKU_NEEDS_PERMISSION -> {
                PrivilegedShell.requestShizuku(5502)
                Toast.makeText(this, R.string.rt_shizuku_asked, Toast.LENGTH_LONG).show()
            }
            else -> showMessage(R.string.rt_need_access)
        }
    }

    private fun partitionAction(titleRes: Int) {
        requireAccess(true) { m ->
            Thread {
                val r = PrivilegedShell.run(m, "mount | grep -i sdext; ls -d /data/sdext* 2>/dev/null")
                val found = r.output.isNotBlank()
                runOnUiThread {
                    showMessage(if (found) R.string.rt_sd2_found_not_impl else R.string.sd2_not_found_msg, titleRes)
                }
            }.start()
        }
    }

    private fun execute(m: Mode, cmd: String, titleRes: Int) {
        Toast.makeText(this, R.string.rt_running, Toast.LENGTH_SHORT).show()
        Thread {
            val r = PrivilegedShell.run(m, cmd)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val head = getString(if (r.ok) R.string.rt_done else R.string.rt_failed)
                val body = if (r.output.isNotBlank()) head + "\n\n" + r.output else head
                AlertDialog.Builder(this)
                    .setTitle(titleRes)
                    .setMessage(body)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }.start()
    }

    private fun showMessage(msgRes: Int, titleRes: Int? = null) {
        val b = AlertDialog.Builder(this)
            .setMessage(msgRes)
            .setPositiveButton(android.R.string.ok, null)
        if (titleRes != null) b.setTitle(titleRes)
        b.show()
    }
}
