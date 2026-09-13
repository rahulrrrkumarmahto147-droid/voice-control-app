package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceRecognitionManager(
    private val context: Context,
    private val onSpeechRecognized: (String) -> Unit,
    private val onErrorOccurred: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _audioRmsLevel = MutableStateFlow(0f)
    val audioRmsLevel: StateFlow<Float> = _audioRmsLevel.asStateFlow()

    fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun updateVoiceState(state: VoiceState) {
        _voiceState.value = state
    }

    fun startListening(languageCode: String = "en-US") {
        if (!hasMicrophonePermission()) {
            _voiceState.value = VoiceState.ERROR
            onErrorOccurred("Microphone permission is not granted. Please allow microphone access.")
            return
        }

        if (!isRecognitionAvailable()) {
            _voiceState.value = VoiceState.ERROR
            onErrorOccurred("Speech recognition is not available on this device.")
            return
        }

        mainHandler.post {
            try {
                destroyRecognizer()

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                _liveTranscript.value = ""
                _voiceState.value = VoiceState.LISTENING
                speechRecognizer?.startListening(intent)
                Log.d(TAG, "Started speech listening with language: $languageCode")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognizer", e)
                _voiceState.value = VoiceState.ERROR
                onErrorOccurred("Could not start speech recognizer: ${e.localizedMessage}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                if (_voiceState.value == VoiceState.LISTENING) {
                    _voiceState.value = VoiceState.PROCESSING
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping speech recognizer", e)
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                _voiceState.value = VoiceState.IDLE
                _audioRmsLevel.value = 0f
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling speech recognizer", e)
            }
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speech recognizer", e)
        }
    }

    fun destroy() {
        mainHandler.post {
            destroyRecognizer()
            _voiceState.value = VoiceState.IDLE
            _audioRmsLevel.value = 0f
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "onReadyForSpeech")
                _voiceState.value = VoiceState.LISTENING
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech")
                _voiceState.value = VoiceState.LISTENING
            }

            override fun onRmsChanged(rmsdB: Float) {
                _audioRmsLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech")
                _voiceState.value = VoiceState.PROCESSING
                _audioRmsLevel.value = 0f
            }

            override fun onError(errorCode: Int) {
                val message = getErrorMessage(errorCode)
                Log.e(TAG, "SpeechRecognizer error: $errorCode - $message")
                _voiceState.value = VoiceState.ERROR
                _audioRmsLevel.value = 0f
                onErrorOccurred(message)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull()?.trim().orEmpty()
                Log.d(TAG, "onResults: '$recognizedText'")
                _audioRmsLevel.value = 0f

                if (recognizedText.isNotBlank()) {
                    _liveTranscript.value = recognizedText
                    _voiceState.value = VoiceState.PROCESSING
                    onSpeechRecognized(recognizedText)
                } else {
                    _voiceState.value = VoiceState.ERROR
                    onErrorOccurred("No speech was detected. Please try speaking again.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = partialMatches?.firstOrNull()?.trim().orEmpty()
                if (text.isNotBlank()) {
                    _liveTranscript.value = text
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
            SpeechRecognizer.ERROR_CLIENT -> "Client-side recognition error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
            SpeechRecognizer.ERROR_NETWORK -> "Network connection error for speech recognition."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network connection timed out."
            SpeechRecognizer.ERROR_NO_MATCH -> "No matching speech heard. Please speak clearly."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Please try again."
            SpeechRecognizer.ERROR_SERVER -> "Recognition server error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected before timeout."
            else -> "Speech recognition error ($errorCode)."
        }
    }

    companion object {
        private const val TAG = "VoiceRecognitionMgr"
    }
}
