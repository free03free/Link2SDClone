package com.example.link2sdclone.groups

import android.app.Activity
import android.widget.Toast
import com.example.link2sdclone.R
import com.example.link2sdclone.freeze.FreezeBackend
import com.example.link2sdclone.freeze.FreezeManager
import com.example.link2sdclone.freeze.FreezeResult

/**
 * منطق واحد مشترك لتجميد/إلغاء تجميد مجموعة من الحزم (packages).
 * يستخدمه كل من:
 *  - زرّي "تجميد المحدد" / "إلغاء تجميد المحدد" في GroupAppsActivity
 *  - سويتش المجموعة في GroupsActivity
 * بحيث السويتش يتصرف بالضبط زي الضغط على الزرار المقابل، بدون أي
 * منطق منفصل يقدر يتعارض أو ينسى حالة (زي onActivityResult بتاع Island).
 */
object GroupFreezeHelper {

    fun apply(
        activity: Activity,
        packages: Set<String>,
        freeze: Boolean,
        onDone: () -> Unit
    ) {
        if (packages.isEmpty()) {
            Toast.makeText(activity, R.string.group_empty_selection, Toast.LENGTH_SHORT).show()
            return
        }
        val backends = FreezeManager.availableBackends(activity)
        if (backends.isEmpty()) {
            Toast.makeText(activity, R.string.freeze_no_backend_title, Toast.LENGTH_SHORT).show()
            return
        }
        val backend = FreezeManager.preferredBackend?.takeIf { it in backends } ?: backends.first()
        processNext(activity, backend, packages.toList(), 0, freeze, failures = 0, onDone)
    }

    // معالجة تطبيق واحد في كل مرة (sequential) عشان نتجنب مشكلة استبدال
    // الـ callback المعلّق لما يكون فيه أكتر من طلب صلاحية في نفس الوقت.
    private fun processNext(
        activity: Activity,
        backend: FreezeBackend,
        packages: List<String>,
        index: Int,
        freeze: Boolean,
        failures: Int,
        onDone: () -> Unit
    ) {
        if (index >= packages.size) {
            val msg = when {
                failures > 0 -> R.string.group_action_partial_failure
                freeze -> R.string.group_action_frozen_done
                else -> R.string.group_action_unfrozen_done
            }
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            onDone()
            return
        }
        val pkg = packages[index]
        FreezeManager.setFrozen(activity, backend, pkg, freeze) { result ->
            activity.runOnUiThread {
                when (result) {
                    is FreezeResult.PermissionRequested -> {
                        // انتظر النتيجة الحقيقية اللي هتوصل لاحقًا لنفس التطبيق
                    }
                    is FreezeResult.Success -> {
                        processNext(activity, backend, packages, index + 1, freeze, failures, onDone)
                    }
                    else -> {
                        processNext(activity, backend, packages, index + 1, freeze, failures + 1, onDone)
                    }
                }
            }
        }
    }
}
