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
import android.widget.ScrollView
import android.widget.TextView
import com.example.link2sdclone.R
import com.example.link2sdclone.model.AppEntry

/**
 * Shows the "Filter" single-choice dialog, exactly like Link2SD's filter drawer
 * (list of RadioButtons, one selected at a time).
 */
fun showFilterDialog(
    context: Context,
    anchor: View,
    currentSelection: Int,
    optionsArrayRes: Int = R.array.filter_options,
    onFilterSelected: (Int) -> Unit
) {
    showChoicePopup(
        context, anchor,
        context.resources.getStringArray(optionsArrayRes),
        currentSelection, onFilterSelected
    )
}

/**
 * Shows the "Sort" single-choice dialog, same pattern as filter.
 */
fun showSortDialog(
    context: Context,
    anchor: View,
    currentSelection: Int,
    onSortSelected: (Int) -> Unit
) {
    showChoicePopup(
        context, anchor,
        context.resources.getStringArray(R.array.sort_options),
        currentSelection, onSortSelected
    )
}

/**
 * قائمة اختيار مفرد مخصصة مثل Link2SD الأصلي: دائرة Radio ثم نص،
 * عرض يتبع أطول نص، بلا خطوط فاصلة، ملتصقة بأسفل الشريط وبالحافة اليمنى.
 */
fun showChoicePopup(
    context: Context,
    anchor: View,
    options: Array<String>,
    currentSelection: Int,
    onSelected: (Int) -> Unit
) {
    val metrics = context.resources.displayMetrics
    fun dp(v: Int): Int = (v * metrics.density).toInt()

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setBackgroundColor(android.graphics.Color.WHITE)
        setPadding(0, dp(4), 0, dp(4))
    }

    val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
    val rowBgRes = ta.getResourceId(0, 0)
    ta.recycle()

    lateinit var popup: PopupWindow

    options.forEachIndexed { i, title ->
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setPadding(dp(16), 0, dp(16), 0)
            isClickable = true
            if (rowBgRes != 0) setBackgroundResource(rowBgRes)
            setOnClickListener {
                popup.dismiss()
                onSelected(i)
            }
        }
        val radio = androidx.appcompat.widget.AppCompatRadioButton(context).apply {
            isChecked = (i == currentSelection)
            isClickable = false
            isFocusable = false
        }
        val label = TextView(context).apply {
            text = title
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#212121"))
            setPaddingRelative(dp(4), 0, 0, 0)
        }
        row.addView(radio)
        row.addView(label)
        container.addView(row)
    }

    container.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    val popupWidth = minOf(container.measuredWidth, metrics.widthPixels)
    val popupHeight = minOf(container.measuredHeight, (metrics.heightPixels * 0.7f).toInt())

    val scrollView = ScrollView(context).apply {
        addView(container, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    popup = PopupWindow(scrollView, popupWidth, popupHeight, true).apply {
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(android.graphics.Color.WHITE))
        if (android.os.Build.VERSION.SDK_INT >= 21) elevation = dp(8).toFloat()
    }

    // ابحث عن الشريط العلوي لنلتصق بأسفله وبحافته اليمنى؛ وإلا فبالزر نفسه.
    var host: View = anchor
    var v: View? = anchor.parent as? View
    while (v != null) {
        if (v is androidx.appcompat.widget.Toolbar) { host = v; break }
        v = v.parent as? View
    }
    androidx.core.widget.PopupWindowCompat.showAsDropDown(
        popup, host, 0, 0, android.view.Gravity.RIGHT
    )
}

/**
 * Inflates the toolbar overflow (3-dot) menu and routes clicks.
 */
fun inflateOverflowMenu(menu: Menu, context: Context) {
    context.let {
        android.view.MenuInflater(it).inflate(R.menu.menu_toolbar_overflow, menu)
    }
}

/**
 * Call from onOptionsItemSelected(item: MenuItem) in your Activity.
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
 * Shows the long-press context menu on an app row.
 *
 * FIX: the row list is now wrapped in a ScrollView, and the popup's height
 * is capped at min(actual content height, 65% of screen height). Short
 * lists still show at their natural (wrap_content) size; long lists become
 * scrollable instead of getting cut off / unreachable at the bottom.
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

    lateinit var popupWindow: PopupWindow

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

    val popupWidth = (anchorView.resources.displayMetrics.widthPixels * 0.72f).toInt()

    // Measure the real (unbounded) content height first.
    container.measure(
        View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    val contentHeight = container.measuredHeight
    val maxHeight = (anchorView.resources.displayMetrics.heightPixels * 0.65f).toInt()
    val popupHeight = minOf(contentHeight, maxHeight)

    val scrollView = ScrollView(context).apply {
        isFillViewport = false
        addView(container, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    popupWindow = PopupWindow(
        scrollView,
        popupWidth,
        popupHeight,
        true
    ).apply {
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(0))
    }

    popupWindow.showAsDropDown(anchorView, 0, -anchorView.height)
}
