package com.aurviz.agent.service.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceManager(private val context: Context) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText: StateFlow<String?> = _recognizedText.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private var onSpeechResultCallback: ((String) -> Unit)? = null

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@VoiceManager)
            }
        }
        textToSpeech = TextToSpeech(context, this).also { tts ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { _isSpeaking.value = true }
                override fun onDone(utteranceId: String?) { _isSpeaking.value = false }
                override fun onError(utteranceId: String?) { _isSpeaking.value = false }
            })
        }
    }

    fun startListening(onResult: (String) -> Unit) {
        onSpeechResultCallback = onResult
        _recognizedText.value = null
        _rmsLevel.value = 0f

        // Play Google Gemini / ChatGPT "tu-dum" wake sound
        SoundEffectPlayer.playStartListeningSound()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
        _isListening.value = true
    }

    fun stopListening() {
        if (_isListening.value) {
            SoundEffectPlayer.playStopListeningSound()
        }
        speechRecognizer?.stopListening()
        _isListening.value = false
    }

    fun speak(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AgentResponseUtterance")
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    // RecognitionListener Callbacks
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {
        _rmsLevel.value = rmsdB.coerceAtLeast(0f)
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        if (_isListening.value) {
            SoundEffectPlayer.playStopListeningSound()
        }
        _isListening.value = false
    }

    override fun onError(error: Int) {
        if (_isListening.value) {
            SoundEffectPlayer.playStopListeningSound()
        }
        _isListening.value = false
    }

    override fun onResults(results: Bundle?) {
        if (_isListening.value) {
            SoundEffectPlayer.playStopListeningSound()
        }
        _isListening.value = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()
        if (!text.isNullOrBlank()) {
            _recognizedText.value = text
            onSpeechResultCallback?.invoke(text)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()
        if (!text.isNullOrBlank()) {
            _recognizedText.value = text
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    // TextToSpeech.OnInitListener
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.getDefault()
        }
    }
}
