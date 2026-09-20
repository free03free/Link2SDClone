package com.example.link2sdclone

import android.app.Activity
import android.util.TypedValue
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** يجعل الشاشة تمتد خلف أشرطة النظام بشكل موحّد على كل الإصدارات، ويضبط الهوامش. */
object EdgeToEdge {
    fun apply(activity: Activity, toolbar: View, list: View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        val tv = TypedValue()
        activity.theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)
        val base = TypedValue.complexToDimensionPixelSize(tv.data, activity.resources.displayMetrics)
        val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val bars = insets.getInsets(types)
            v.setPadding(bars.left, bars.top, bars.right, 0)
            val lp = v.layoutParams
            lp.height = base + bars.top
            v.layoutParams = lp
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(list) { v, insets ->
            val bars = insets.getInsets(types)
            v.setPadding(bars.left, v.paddingTop, bars.right, bars.bottom)
            insets
        }
        (list.parent as? android.view.ViewGroup)?.clipToPadding = false
        ViewCompat.requestApplyInsets(toolbar)
    }
}
