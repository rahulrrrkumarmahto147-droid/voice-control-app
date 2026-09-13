package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.VoiceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("voice_control_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<VoiceSettings> = _settings.asStateFlow()

    private fun loadSettings(): VoiceSettings {
        return VoiceSettings(
            ttsEnabled = prefs.getBoolean(KEY_TTS_ENABLED, true),
            speechRate = prefs.getFloat(KEY_SPEECH_RATE, 1.0f),
            languageCode = prefs.getString(KEY_LANGUAGE, "en-US") ?: "en-US",
            confirmationForRiskyActions = prefs.getBoolean(KEY_CONFIRM_RISKY, true),
            continuousMode = prefs.getBoolean(KEY_CONTINUOUS_MODE, false)
        )
    }

    fun setTtsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TTS_ENABLED, enabled).apply()
        _settings.value = _settings.value.copy(ttsEnabled = enabled)
    }

    fun setSpeechRate(rate: Float) {
        prefs.edit().putFloat(KEY_SPEECH_RATE, rate).apply()
        _settings.value = _settings.value.copy(speechRate = rate)
    }

    fun setLanguage(languageCode: String) {
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
        _settings.value = _settings.value.copy(languageCode = languageCode)
    }

    fun setConfirmationForRiskyActions(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONFIRM_RISKY, enabled).apply()
        _settings.value = _settings.value.copy(confirmationForRiskyActions = enabled)
    }

    fun setContinuousMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONTINUOUS_MODE, enabled).apply()
        _settings.value = _settings.value.copy(continuousMode = enabled)
    }

    companion object {
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_CONFIRM_RISKY = "confirm_risky"
        private const val KEY_CONTINUOUS_MODE = "continuous_mode"
    }
}
