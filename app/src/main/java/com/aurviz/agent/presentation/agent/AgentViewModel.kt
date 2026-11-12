package com.aurviz.agent.presentation.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurviz.agent.core.security.SecurePreferences
import com.aurviz.agent.domain.model.AgentMessage
import com.aurviz.agent.domain.model.SensitiveAction
import com.aurviz.agent.domain.orchestrator.AgentOrchestrator
import com.aurviz.agent.service.voice.VoiceManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AgentViewModel(
    private val orchestrator: AgentOrchestrator,
    private val voiceManager: VoiceManager,
    private val securePreferences: SecurePreferences
) : ViewModel() {

    val messages: StateFlow<List<AgentMessage>> = orchestrator.messages
    val isProcessing: StateFlow<Boolean> = orchestrator.isProcessing
    val pendingConfirmation: StateFlow<SensitiveAction?> = orchestrator.pendingConfirmation
    val isListening: StateFlow<Boolean> = voiceManager.isListening
    val recognizedText: StateFlow<String?> = voiceManager.recognizedText
    val rmsLevel: StateFlow<Float> = voiceManager.rmsLevel
    val isSpeaking: StateFlow<Boolean> = voiceManager.isSpeaking

    init {
        // Collect spoken agent responses if voice feedback is enabled
        viewModelScope.launch {
            orchestrator.speechEvents.collect { text ->
                if (securePreferences.isVoiceFeedbackEnabled()) {
                    voiceManager.speak(text)
                }
            }
        }
    }

    fun sendPrompt(prompt: String, isVoice: Boolean = false) {
        viewModelScope.launch {
            orchestrator.processUserPrompt(prompt, isVoice)
        }
    }

    fun clearConversation() {
        orchestrator.clearConversation()
    }

    fun startVoiceInput() {
        voiceManager.startListening { recognizedText ->
            sendPrompt(recognizedText, isVoice = true)
        }
    }

    fun stopVoiceInput() {
        voiceManager.stopListening()
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    fun onConfirmAction(approved: Boolean, editedArguments: Map<String, Any?>? = null) {
        orchestrator.confirmPendingAction(approved, editedArguments)
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.shutdown()
    }
}
