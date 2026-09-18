package com.example.link2sdclone.lock

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import com.example.link2sdclone.databinding.ActivityLockSetupBinding

class LockSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockSetupBinding

    private enum class Step { IDLE, ENTER_OLD, ENTER_NEW, CONFIRM_NEW }
    private var step = Step.IDLE
    private var pendingType = LockManager.TYPE_PIN
    private var tempNewCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarLockSetup)
        binding.toolbarLockSetup.setNavigationOnClickListener { finish() }

        refreshUiFromState()

        binding.switchEnableLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !LockManager.isEnabled(this)) {
                pendingType = if (binding.radioPattern.isChecked) LockManager.TYPE_PATTERN else LockManager.TYPE_PIN
                startEnterNew()
            } else if (!isChecked && LockManager.isEnabled(this)) {
                LockManager.disable(this)
                refreshUiFromState()
            }
        }

        binding.btnSetCode.setOnClickListener {
            pendingType = if (binding.radioPattern.isChecked) LockManager.TYPE_PATTERN else LockManager.TYPE_PIN
            if (LockManager.hasCodeSet(this)) {
                startEnterOld()
            } else {
                startEnterNew()
            }
        }

        binding.switchFingerprint.setOnCheckedChangeListener { _, isChecked ->
            LockManager.setFingerprintEnabled(this, isChecked)
        }

        binding.btnCodeContinue.setOnClickListener { onCodeEntrySubmit() }

        binding.patternViewSetup.listener = object : PatternLockView.PatternListener {
            override fun onPatternStart() {
                binding.textCodeError.visibility = View.INVISIBLE
            }
            override fun onPatternComplete(pattern: List<Int>) {
                handleEnteredCode(pattern.joinToString("-"))
            }
        }
    }

    private fun refreshUiFromState() {
        val enabled = LockManager.isEnabled(this)
        binding.switchEnableLock.isChecked = enabled
        binding.groupLockOptions.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.radioPin.isChecked = LockManager.getType(this) != LockManager.TYPE_PATTERN
        binding.radioPattern.isChecked = LockManager.getType(this) == LockManager.TYPE_PATTERN

        val canBiometric = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        binding.switchFingerprint.isEnabled = enabled && canBiometric
        binding.switchFingerprint.isChecked = LockManager.isFingerprintEnabled(this)
        if (!canBiometric) {
            binding.switchFingerprint.text = "بصمة الإصبع غير مدعومة على هذا الجهاز"
        }

        hideCodeEntry()
    }

    // ملاحظة الإصلاح: خطوة "الرمز القديم" لازم تعرض شاشة إدخال بنوع
    // الرمز القديم المخزّن حالياً (LockManager.getType) وليس النوع الجديد
    // المطلوب (pendingType) — هذا كان سبب توقف تغيير النوع.
    private fun startEnterOld() {
        step = Step.ENTER_OLD
        val oldType = LockManager.getType(this)
        showCodeEntry(promptFor(oldType, "أدخل الرمز الحالي"), oldType)
    }

    private fun startEnterNew() {
        step = Step.ENTER_NEW
        tempNewCode = null
        showCodeEntry(promptFor(pendingType, "أدخل رمز جديد"), pendingType)
    }

    private fun startConfirmNew() {
        step = Step.CONFIRM_NEW
        showCodeEntry(promptFor(pendingType, "أعد إدخال الرمز للتأكيد"), pendingType)
    }

    private fun promptFor(type: String, base: String): String =
        if (type == LockManager.TYPE_PATTERN) "$base (نمط)" else "$base (رقمي)"

    private fun showCodeEntry(prompt: String, inputType: String) {
        binding.containerCodeEntry.visibility = View.VISIBLE
        binding.textCodePrompt.text = prompt
        binding.textCodeError.visibility = View.INVISIBLE
        binding.editPinSetup.text?.clear()
        binding.patternViewSetup.reset()

        val isPattern = inputType == LockManager.TYPE_PATTERN
        binding.editPinSetup.visibility = if (isPattern) View.GONE else View.VISIBLE
        binding.btnCodeContinue.visibility = if (isPattern) View.GONE else View.VISIBLE
        binding.patternViewSetup.visibility = if (isPattern) View.VISIBLE else View.GONE
    }

    private fun hideCodeEntry() {
        step = Step.IDLE
        binding.containerCodeEntry.visibility = View.GONE
    }

    private fun onCodeEntrySubmit() {
        val code = binding.editPinSetup.text.toString()
        if (code.isBlank()) return
        handleEnteredCode(code)
    }

    private fun handleEnteredCode(code: String) {
        when (step) {
            Step.ENTER_OLD -> {
                if (LockManager.verify(this, code)) {
                    startEnterNew()
                } else {
                    binding.textCodeError.text = "الرمز غير صحيح"
                    binding.textCodeError.visibility = View.VISIBLE
                }
            }
            Step.ENTER_NEW -> {
                tempNewCode = code
                startConfirmNew()
            }
            Step.CONFIRM_NEW -> {
                if (code == tempNewCode) {
                    if (LockManager.hasCodeSet(this)) {
                        LockManager.changeCode(this, pendingType, code)
                    } else {
                        LockManager.enableWithCode(this, pendingType, code)
                    }
                    Toast.makeText(this, "تم حفظ رمز القفل بنجاح", Toast.LENGTH_SHORT).show()
                    refreshUiFromState()
                } else {
                    binding.textCodeError.text = "الرمزان غير متطابقين، حاول مجدداً"
                    binding.textCodeError.visibility = View.VISIBLE
                    startEnterNew()
                }
            }
            Step.IDLE -> Unit
        }
    }
}
