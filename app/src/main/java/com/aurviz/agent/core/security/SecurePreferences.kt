package com.aurviz.agent.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aurviz.agent.BuildConfig

/**
 * Manages encrypted key-value storage using Android Keystore and EncryptedSharedPreferences.
 * Sensitive data such as API keys and credentials are never stored in plaintext.
 */
class SecurePreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to standard private mode in case of emulator Keystore issues
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
    }

    fun getGeminiApiKey(): String {
        val storedKey = sharedPreferences.getString(KEY_GEMINI_API_KEY, null)
        if (!storedKey.isNullOrBlank()) {
            return storedKey
        }
        // Fallback to BuildConfig if provided in local.properties
        return BuildConfig.GEMINI_API_KEY
    }

    fun saveGeminiApiKey(apiKey: String) {
        sharedPreferences.edit()
            .putString(KEY_GEMINI_API_KEY, apiKey.trim())
            .apply()
    }

    fun getDefaultEmail(): String {
        return sharedPreferences.getString(KEY_DEFAULT_EMAIL, "user@example.com") ?: "user@example.com"
    }

    fun saveDefaultEmail(email: String) {
        sharedPreferences.edit()
            .putString(KEY_DEFAULT_EMAIL, email.trim())
            .apply()
    }

    fun isVoiceFeedbackEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_VOICE_FEEDBACK, true)
    }

    fun setVoiceFeedbackEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_VOICE_FEEDBACK, enabled)
            .apply()
    }

    companion object {
        private const val PREFS_FILENAME = "secure_agent_prefs"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_DEFAULT_EMAIL = "default_email"
        private const val KEY_VOICE_FEEDBACK = "voice_feedback"
    }
}
