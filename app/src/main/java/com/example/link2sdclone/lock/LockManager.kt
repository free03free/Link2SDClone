package com.example.link2sdclone.lock

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

object LockManager {

    private const val PREFS_NAME = "app_lock_prefs"
    private const val KEY_ENABLED = "lock_enabled"
    private const val KEY_TYPE = "lock_type"
    private const val KEY_HASH = "lock_hash"
    private const val KEY_SALT = "lock_salt"
    private const val KEY_FINGERPRINT = "fingerprint_enabled"

    const val TYPE_PIN = "PIN"
    const val TYPE_PATTERN = "PATTERN"

    @Volatile
    private var unlockedForSession = false

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun getType(context: Context): String =
        prefs(context).getString(KEY_TYPE, TYPE_PIN) ?: TYPE_PIN

    fun isFingerprintEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FINGERPRINT, false)

    fun setFingerprintEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FINGERPRINT, enabled).apply()
    }

    fun hasCodeSet(context: Context): Boolean = prefs(context).contains(KEY_HASH)

    fun enableWithCode(context: Context, type: String, code: String) {
        val salt = generateSalt()
        val hash = hashCode(code, salt)
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_TYPE, type)
            .putString(KEY_HASH, hash)
            .putString(KEY_SALT, salt)
            .apply()
    }

    fun changeCode(context: Context, newType: String, newCode: String) {
        val salt = generateSalt()
        val hash = hashCode(newCode, salt)
        prefs(context).edit()
            .putString(KEY_TYPE, newType)
            .putString(KEY_HASH, hash)
            .putString(KEY_SALT, salt)
            .apply()
    }

    fun disable(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, false)
            .remove(KEY_HASH)
            .remove(KEY_SALT)
            .putBoolean(KEY_FINGERPRINT, false)
            .apply()
    }

    fun verify(context: Context, code: String): Boolean {
        val salt = prefs(context).getString(KEY_SALT, null) ?: return false
        val savedHash = prefs(context).getString(KEY_HASH, null) ?: return false
        return hashCode(code, salt) == savedHash
    }

    fun isUnlockedForSession(): Boolean = unlockedForSession
    fun markUnlocked() { unlockedForSession = true }
    fun markLocked() { unlockedForSession = false }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashCode(code: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray())
        val hashBytes = digest.digest(code.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
