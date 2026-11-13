package com.aurviz.agent.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages encrypted key-value storage using Android Keystore and EncryptedSharedPreferences.
 * Sensitive data such as API keys and credentials are never stored in plaintext.
 */
class SecurePreferences(context: Context) {

    private val securePreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()
    private val preferences = context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)

    val isSecureStorageAvailable: Boolean get() = securePreferences != null

    fun getGeminiApiKey(): String {
        val storedKey = securePreferences?.getString(KEY_GEMINI_API_KEY, null)
        if (!storedKey.isNullOrBlank()) {
            return storedKey
        }
        return ""
    }

    fun saveGeminiApiKey(apiKey: String) {
        checkNotNull(securePreferences) { "Encrypted storage is unavailable. The API key was not saved." }
            .edit()
            .putString(KEY_GEMINI_API_KEY, apiKey.trim())
            .apply()
    }

    fun getDefaultEmail(): String {
        return securePreferences?.getString(KEY_DEFAULT_EMAIL, null) ?: "user@example.com"
    }

    fun saveDefaultEmail(email: String) {
        checkNotNull(securePreferences) { "Encrypted storage is unavailable. The email address was not saved." }
            .edit()
            .putString(KEY_DEFAULT_EMAIL, email.trim())
            .apply()
    }

    fun getBackendAccessToken(): String? = securePreferences?.getString(KEY_BACKEND_ACCESS_TOKEN, null)

    fun getBackendSessionExpiresAt(): Long = securePreferences?.getLong(KEY_BACKEND_SESSION_EXPIRES_AT, 0L) ?: 0L

    fun saveBackendSession(token: String, expiresAtEpochMillis: Long) {
        checkNotNull(securePreferences) { "Encrypted storage is unavailable. The backend session was not saved." }
            .edit()
            .putString(KEY_BACKEND_ACCESS_TOKEN, token)
            .putLong(KEY_BACKEND_SESSION_EXPIRES_AT, expiresAtEpochMillis)
            .apply()
    }

    fun clearBackendAccessToken() {
        securePreferences?.edit()
            ?.remove(KEY_BACKEND_ACCESS_TOKEN)
            ?.remove(KEY_BACKEND_SESSION_EXPIRES_AT)
            ?.apply()
    }

    fun isVoiceFeedbackEnabled(): Boolean {
        return preferences.getBoolean(KEY_VOICE_FEEDBACK, true)
    }

    fun setVoiceFeedbackEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_VOICE_FEEDBACK, enabled)
            .apply()
    }

    companion object {
        private const val PREFS_FILENAME = "secure_agent_prefs"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_DEFAULT_EMAIL = "default_email"
        private const val KEY_BACKEND_ACCESS_TOKEN = "backend_access_token"
        private const val KEY_BACKEND_SESSION_EXPIRES_AT = "backend_session_expires_at"
        private const val KEY_VOICE_FEEDBACK = "voice_feedback"
    }
}
