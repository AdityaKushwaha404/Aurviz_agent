package com.aurviz.agent.presentation.settings

import androidx.lifecycle.ViewModel
import com.aurviz.agent.core.security.SecurePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val _apiKey = MutableStateFlow(securePreferences.getGeminiApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _defaultEmail = MutableStateFlow(securePreferences.getDefaultEmail())
    val defaultEmail: StateFlow<String> = _defaultEmail.asStateFlow()

    private val _voiceFeedback = MutableStateFlow(securePreferences.isVoiceFeedbackEnabled())
    val voiceFeedback: StateFlow<Boolean> = _voiceFeedback.asStateFlow()

    fun updateApiKey(key: String) {
        _apiKey.value = key
        securePreferences.saveGeminiApiKey(key)
    }

    fun updateDefaultEmail(email: String) {
        _defaultEmail.value = email
        securePreferences.saveDefaultEmail(email)
    }

    fun setVoiceFeedback(enabled: Boolean) {
        _voiceFeedback.value = enabled
        securePreferences.setVoiceFeedbackEnabled(enabled)
    }
}
