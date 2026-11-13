package com.aurviz.agent.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.aurviz.agent.core.security.SecurePreferences
import com.aurviz.agent.data.remote.BackendAuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import com.aurviz.agent.data.local.metrics.RoomPerformanceRecorder
import com.aurviz.agent.domain.metrics.MetricSummary

class SettingsViewModel(
    private val securePreferences: SecurePreferences,
    private val backendAuthManager: BackendAuthManager,
    private val performanceRecorder: RoomPerformanceRecorder? = null
) : ViewModel() {

    /** Measured on-device latency (median / p95) per metric, newest 1000 samples. */
    val performance: StateFlow<List<MetricSummary>> =
        (performanceRecorder?.summaries() ?: flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearPerformanceSamples() {
        viewModelScope.launch { performanceRecorder?.clear() }
    }

    val gmailConnectionState: StateFlow<String> = backendAuthManager.connectionState

    init {
        viewModelScope.launch { backendAuthManager.refreshConnectionState() }
    }

    fun refreshGmailStatus() {
        viewModelScope.launch { backendAuthManager.refreshConnectionState() }
    }

    fun disconnectGmail() {
        viewModelScope.launch { backendAuthManager.disconnect() }
    }

    val themeMode: StateFlow<String> = securePreferences.themeMode
    val demoMode: StateFlow<Boolean> = securePreferences.demoMode
    val usesBuiltInKey: Boolean get() = securePreferences.hasBuiltInGeminiKey

    fun setThemeMode(mode: String) = securePreferences.setThemeMode(mode)
    fun setDemoMode(enabled: Boolean) = securePreferences.setDemoMode(enabled)

    private val _apiKey = MutableStateFlow(securePreferences.getUserGeminiApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _defaultEmail = MutableStateFlow(securePreferences.getDefaultEmail())
    val defaultEmail: StateFlow<String> = _defaultEmail.asStateFlow()

    private val _voiceFeedback = MutableStateFlow(securePreferences.isVoiceFeedbackEnabled())
    val voiceFeedback: StateFlow<Boolean> = _voiceFeedback.asStateFlow()

    private val _storageMessage = MutableStateFlow(
        if (securePreferences.isSecureStorageAvailable) null
        else "Encrypted storage is unavailable. Secrets and email addresses cannot be saved."
    )
    val storageMessage: StateFlow<String?> = _storageMessage.asStateFlow()

    fun updateApiKey(key: String) {
        try {
            securePreferences.saveGeminiApiKey(key)
            _apiKey.value = key
            _storageMessage.value = "API key saved in encrypted storage."
        } catch (error: Exception) {
            _storageMessage.value = error.message ?: "Encrypted storage is unavailable; the key was not saved."
        }
    }

    fun updateDefaultEmail(email: String) {
        try {
            securePreferences.saveDefaultEmail(email)
            _defaultEmail.value = email
            _storageMessage.value = "Default email saved in encrypted storage."
        } catch (error: Exception) {
            _storageMessage.value = error.message ?: "Encrypted storage is unavailable; the email address was not saved."
        }
    }

    fun setVoiceFeedback(enabled: Boolean) {
        _voiceFeedback.value = enabled
        securePreferences.setVoiceFeedbackEnabled(enabled)
    }
}
