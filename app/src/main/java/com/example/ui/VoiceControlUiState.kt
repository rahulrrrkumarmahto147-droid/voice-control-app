package com.example.ui

import com.example.action.ParsedCommand
import com.example.data.model.CommandHistoryEntity
import com.example.voice.VoiceState

data class VoiceControlUiState(
    val voiceState: VoiceState = VoiceState.IDLE,
    val liveTranscript: String = "",
    val interpretedCommand: ParsedCommand? = null,
    val currentActionDescription: String = "",
    val statusMessage: String = "Ready for voice commands",
    val isAccessibilityActive: Boolean = false,
    val isMicPermissionGranted: Boolean = false,
    val isContinuousMode: Boolean = false,
    val isTtsEnabled: Boolean = true,
    val speechRate: Float = 1.0f,
    val languageCode: String = "en-US",
    val confirmationForRiskyActions: Boolean = true,
    val pendingConfirmation: ParsedCommand? = null,
    val historyItems: List<CommandHistoryEntity> = emptyList(),
    val showSettingsDialog: Boolean = false,
    val audioRms: Float = 0f
)
