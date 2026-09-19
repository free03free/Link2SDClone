package com.example.link2sdclone.util

/** علامة بسيطة: إن كانت true تعيد MainActivity تحميل قائمة التطبيقات عند عودتها للواجهة. */
object ReloadFlag {
    @Volatile
    var pending = false

    fun consume(): Boolean {
        val p = pending
        pending = false
        return p
    }
}
