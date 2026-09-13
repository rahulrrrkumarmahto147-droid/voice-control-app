package com.example.data.model

data class VoiceSettings(
    val ttsEnabled: Boolean = true,
    val speechRate: Float = 1.0f,
    val languageCode: String = "en-US", // or "hi-IN"
    val confirmationForRiskyActions: Boolean = true,
    val continuousMode: Boolean = false
)
