package com.example.link2sdclone.lock

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.link2sdclone.databinding.ActivityLockBinding

class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val type = LockManager.getType(this)

        if (type == LockManager.TYPE_PATTERN) {
            binding.containerPin.visibility = View.GONE
            binding.patternView.visibility = View.VISIBLE
            binding.textLockTitle.text = "ارسم النمط لفتح القفل"
            binding.patternView.listener = object : PatternLockView.PatternListener {
                override fun onPatternStart() {
                    binding.textLockError.visibility = View.INVISIBLE
                }
                override fun onPatternComplete(pattern: List<Int>) {
                    val code = pattern.joinToString("-")
                    if (LockManager.verify(this@LockActivity, code)) {
                        unlockAndFinish()
                    } else {
                        binding.textLockError.visibility = View.VISIBLE
                    }
                }
            }
        } else {
            binding.containerPin.visibility = View.VISIBLE
            binding.patternView.visibility = View.GONE
            binding.textLockTitle.text = "أدخل رمز القفل"
            binding.btnUnlockPin.setOnClickListener {
                val code = binding.editPin.text.toString()
                if (LockManager.verify(this, code)) {
                    unlockAndFinish()
                } else {
                    binding.textLockError.visibility = View.VISIBLE
                    binding.editPin.text?.clear()
                }
            }
        }

        if (LockManager.isFingerprintEnabled(this) && canUseBiometrics()) {
            binding.btnFingerprint.visibility = View.VISIBLE
            binding.btnFingerprint.setOnClickListener { showBiometricPrompt() }
        }
    }

    private fun canUseBiometrics(): Boolean {
        val manager = BiometricManager.from(this)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                unlockAndFinish()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("فتح القفل بالبصمة")
            .setNegativeButtonText("إلغاء")
            .build()
        prompt.authenticate(info)
    }

    private fun unlockAndFinish() {
        LockManager.markUnlocked()
        finish()
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}
