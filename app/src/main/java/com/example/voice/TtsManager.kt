package com.example.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TtsManager(
    context: Context,
    private val onInitComplete: ((Boolean) -> Unit)? = null
) {
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                textToSpeech?.language = Locale.US
                Log.d(TAG, "TextToSpeech successfully initialized.")
                onInitComplete?.invoke(true)
            } else {
                isInitialized = false
                Log.e(TAG, "Failed to initialize TextToSpeech: status $status")
                onInitComplete?.invoke(false)
            }
        }
    }

    fun setLanguage(languageCode: String) {
        if (!isInitialized) return
        val locale = if (languageCode.startsWith("hi", ignoreCase = true)) {
            Locale("hi", "IN")
        } else {
            Locale.US
        }
        val result = textToSpeech?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Language $languageCode is not supported or missing data; falling back to US English.")
            textToSpeech?.language = Locale.US
        }
    }

    fun setSpeechRate(rate: Float) {
        if (isInitialized) {
            textToSpeech?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        }
    }

    fun speak(
        message: String,
        utteranceId: String = System.currentTimeMillis().toString(),
        onDone: (() -> Unit)? = null
    ) {
        if (!isInitialized || message.isBlank()) {
            onDone?.invoke()
            return
        }

        if (onDone != null) {
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId) {
                        onDone()
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId) {
                        onDone()
                    }
                }
            })
        }

        textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        if (isInitialized) {
            textToSpeech?.stop()
        }
    }

    fun shutdown() {
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }

    companion object {
        private const val TAG = "TtsManager"
    }
}
