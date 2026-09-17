package com.example.link2sdclone.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.example.link2sdclone.R
import com.example.link2sdclone.model.AppEntry

/**
 * Shows the "Filter" single-choice dialog, exactly like Link2SD's filter drawer
 * (list of RadioButtons, one selected at a time).
 *
 * @param currentSelection index of the currently active filter
 * @param onFilterSelected callback fired with the chosen index; dialog closes automatically
 */
fun showFilterDialog(
    context: Context,
    currentSelection: Int,
    onFilterSelected: (Int) -> Unit
) {
    val options = context.resources.getStringArray(R.array.filter_options)
    AlertDialog.Builder(context)
        .setSingleChoiceItems(options, currentSelection) { dialog, which ->
            onFilterSelected(which)
            dialog.dismiss()
        }
        .show()
}

/**
 * Shows the "Sort" single-choice dialog, same pattern as filter.
 */
fun showSortDialog(
    context: Context,
    currentSelection: Int,
    onSortSelected: (Int) -> Unit
) {
    val options = context.resources.getStringArray(R.array.sort_options)
    AlertDialog.Builder(context)
        .setSingleChoiceItems(options, currentSelection) { dialog, which ->
            onSortSelected(which)
            dialog.dismiss()
        }
        .show()
}

/**
 * Inflates the toolbar overflow (3-dot) menu and routes clicks.
 * Call this from onCreateOptionsMenu(menu: Menu) in your Activity.
 */
fun inflateOverflowMenu(menu: Menu, context: Context) {
    context.let {
        android.view.MenuInflater(it).inflate(R.menu.menu_toolbar_overflow, menu)
    }
}

/**
 * Call from onOptionsItemSelected(item: MenuItem) in your Activity.
 * Returns true if the click was handled.
 */
fun handleOverflowMenuClick(item: MenuItem, actions: OverflowActions): Boolean {
    return when (item.itemId) {
        R.id.action_search -> { actions.onSearch(); true }
        R.id.action_batch_select -> { actions.onBatchSelect(); true }
        R.id.action_storage_info -> { actions.onStorageInfo(); true }
        R.id.action_settings -> { actions.onSettings(); true }
        R.id.action_about -> { actions.onAbout(); true }
        else -> false
    }
}

interface OverflowActions {
    fun onSearch()
    fun onBatchSelect()
    fun onStorageInfo()
    fun onSettings()
    fun onAbout()
}

/**
 * Shows the long-press context menu on an app row: an app-name header
 * followed by a plain vertical list of actions, exactly matching the
 * screenshots (Move to SD / Run / Manage / Reinstall / Delete / Freeze /
 * Convert to system app / Clear data / Clear cache / View on Google Play /
 * Share / Share app / Create shortcut).
 *
 * A plain PopupMenu can't relabel "Move to SD" vs "Move to phone" or
 * "Freeze" vs "Unfreeze" per row, so this builds a small custom PopupWindow
 * instead, using the ids declared in res/menu/menu_app_context.xml.
 */
fun showAppContextMenu(
    context: Context,
    anchorView: View,
    app: AppEntry,
    onAction: (Int) -> Unit
) {
    val inflater = LayoutInflater.from(context)

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_popup_menu)
        elevation = 16f
    }

    val header = inflater.inflate(R.layout.popup_context_header, container, false) as TextView
    header.text = app.label
    container.addView(header)

    val divider = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        setBackgroundResource(R.color.divider)
    }
    container.addView(divider)

    val popupWindow = PopupWindow(
        container,
        (anchorView.resources.displayMetrics.widthPixels * 0.72f).toInt(),
        ViewGroup.LayoutParams.WRAP_CONTENT,
        true
    ).apply {
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(0))
    }

    fun addRow(id: Int, title: String) {
        val row = inflater.inflate(R.layout.popup_context_row, container, false) as TextView
        row.id = id
        row.text = title
        row.setOnClickListener {
            popupWindow.dismiss()
            onAction(id)
        }
        container.addView(row)
    }

    val res = context.resources
    addRow(
        R.id.ctx_move_sd,
        if (app.isOnSdCard) res.getString(R.string.ctx_move_to_phone) else res.getString(R.string.ctx_move_to_sd)
    )
    addRow(R.id.ctx_run, res.getString(R.string.ctx_run))
    addRow(R.id.ctx_manage, res.getString(R.string.ctx_manage))
    addRow(R.id.ctx_reinstall, res.getString(R.string.ctx_reinstall))
    addRow(R.id.ctx_delete, res.getString(R.string.ctx_delete))
    addRow(
        R.id.ctx_freeze,
        if (app.isFrozen) res.getString(R.string.ctx_unfreeze) else res.getString(R.string.ctx_freeze)
    )
    addRow(R.id.ctx_convert_system, res.getString(R.string.ctx_convert_system))
    addRow(R.id.ctx_clear_data, res.getString(R.string.ctx_clear_data))
    addRow(R.id.ctx_clear_cache, res.getString(R.string.ctx_clear_cache))
    addRow(R.id.ctx_view_play, res.getString(R.string.ctx_view_play))
    addRow(R.id.ctx_share, res.getString(R.string.ctx_share))
    addRow(R.id.ctx_share_apk, res.getString(R.string.ctx_share_apk))
    addRow(R.id.ctx_create_shortcut, res.getString(R.string.ctx_create_shortcut))

    popupWindow.showAsDropDown(anchorView, 0, -anchorView.height)
}
