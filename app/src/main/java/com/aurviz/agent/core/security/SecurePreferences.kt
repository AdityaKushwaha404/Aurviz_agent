package com.aurviz.agent.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aurviz.agent.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    /** Key the user typed in Settings; empty when none was saved. */
    fun getUserGeminiApiKey(): String =
        securePreferences?.getString(KEY_GEMINI_API_KEY, null)?.takeIf { it.isNotBlank() }.orEmpty()

    /** True when this build ships a fallback key (set via local.properties at build time). */
    val hasBuiltInGeminiKey: Boolean get() = BuildConfig.GEMINI_API_KEY_DEFAULT.isNotBlank()

    /** Key actually used for requests: the user's own key, else the build-time fallback. */
    fun getGeminiApiKey(): String =
        getUserGeminiApiKey().ifBlank { BuildConfig.GEMINI_API_KEY_DEFAULT }

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

    private val _themeMode = MutableStateFlow(preferences.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM")
    /** One of SYSTEM, LIGHT, DARK. */
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        preferences.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    private val _demoMode = MutableStateFlow(preferences.getBoolean(KEY_DEMO_MODE, false))
    /** When true the Gmail tools use the built-in sample mailbox instead of a real account. */
    val demoMode: StateFlow<Boolean> = _demoMode.asStateFlow()

    fun isDemoMode(): Boolean = _demoMode.value

    fun setDemoMode(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_DEMO_MODE, enabled).apply()
        _demoMode.value = enabled
    }

    fun isOnboardingDone(): Boolean = preferences.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone() {
        preferences.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DEMO_MODE = "demo_mode"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val PREFS_FILENAME = "secure_agent_prefs"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_DEFAULT_EMAIL = "default_email"
        private const val KEY_BACKEND_ACCESS_TOKEN = "backend_access_token"
        private const val KEY_BACKEND_SESSION_EXPIRES_AT = "backend_session_expires_at"
        private const val KEY_VOICE_FEEDBACK = "voice_feedback"
    }
}
