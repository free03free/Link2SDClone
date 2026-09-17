package com.example.link2sdclone.ui

import android.app.AlertDialog
import android.content.Context
import android.view.Menu
import android.view.MenuItem
import android.widget.PopupMenu
import com.example.link2sdclone.R

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
 * Shows the long-press context menu on an app row (anchored PopupMenu,
 * matches the plain list of actions like "Rebind all external folders",
 * "Clear all app caches", etc.)
 */
fun showAppContextMenu(
    context: Context,
    anchorView: android.view.View,
    onAction: (Int) -> Unit
) {
    val popup = PopupMenu(context, anchorView)
    popup.menuInflater.inflate(R.menu.menu_app_context, popup.menu)
    popup.setOnMenuItemClickListener { item ->
        onAction(item.itemId)
        true
    }
    popup.show()
}
