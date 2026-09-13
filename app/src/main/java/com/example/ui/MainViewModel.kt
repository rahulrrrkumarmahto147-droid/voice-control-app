package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VoiceControlApplication
import com.example.action.ParsedCommand
import com.example.executor.ActionExecutor
import com.example.executor.ExecutionResult
import com.example.service.VoiceControlAccessibilityService
import com.example.voice.VoiceRecognitionManager
import com.example.voice.VoiceState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VoiceControlApplication
    private val historyRepo = app.historyRepository
    private val settingsRepo = app.settingsRepository
    private val parser = app.commandParser
    private val ttsManager = app.ttsManager

    private val executor = ActionExecutor(
        context = application,
        appResolver = app.appResolver,
        historyRepository = historyRepo,
        ttsManager = ttsManager,
        isTtsEnabled = { _uiState.value.isTtsEnabled }
    )

    private val _uiState = MutableStateFlow(VoiceControlUiState())
    val uiState: StateFlow<VoiceControlUiState> = _uiState.asStateFlow()

    private var voiceRecognitionManager: VoiceRecognitionManager? = null
    private var executionJob: Job? = null

    init {
        // Collect history
        viewModelScope.launch {
            historyRepo.recentHistory.collect { history ->
                _uiState.update { it.copy(historyItems = history) }
            }
        }

        // Collect settings
        viewModelScope.launch {
            settingsRepo.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        isTtsEnabled = settings.ttsEnabled,
                        speechRate = settings.speechRate,
                        languageCode = settings.languageCode,
                        confirmationForRiskyActions = settings.confirmationForRiskyActions,
                        isContinuousMode = settings.continuousMode
                    )
                }
                ttsManager.setSpeechRate(settings.speechRate)
                ttsManager.setLanguage(settings.languageCode)
            }
        }

        // Observe accessibility service connection
        viewModelScope.launch {
            VoiceControlAccessibilityService.isServiceActive.collect { active ->
                _uiState.update { it.copy(isAccessibilityActive = active) }
            }
        }

        // Observe executor status
        viewModelScope.launch {
            executor.currentAction.collect { action ->
                _uiState.update {
                    it.copy(currentActionDescription = action?.description ?: "")
                }
            }
        }

        setupVoiceManager()
    }

    fun setupVoiceManager() {
        voiceRecognitionManager = VoiceRecognitionManager(
            context = getApplication(),
            onSpeechRecognized = { text ->
                onSpeechRecognized(text)
            },
            onErrorOccurred = { errorMsg ->
                _uiState.update {
                    it.copy(
                        voiceState = VoiceState.ERROR,
                        statusMessage = errorMsg
                    )
                }
                if (_uiState.value.isContinuousMode) {
                    scheduleContinuousRestart()
                }
            }
        )

        // Observe speech recognition state
        viewModelScope.launch {
            voiceRecognitionManager?.voiceState?.collect { state ->
                // Don't override EXECUTING state from speech recognizer
                if (_uiState.value.voiceState != VoiceState.EXECUTING || state == VoiceState.ERROR) {
                    _uiState.update { it.copy(voiceState = state) }
                }
            }
        }

        // Observe live transcript
        viewModelScope.launch {
            voiceRecognitionManager?.liveTranscript?.collect { transcript ->
                _uiState.update { it.copy(liveTranscript = transcript) }
            }
        }

        // Observe RMS audio levels
        viewModelScope.launch {
            voiceRecognitionManager?.audioRmsLevel?.collect { rms ->
                _uiState.update { it.copy(audioRms = rms) }
            }
        }
    }

    fun refreshStatus() {
        val context = getApplication<Application>()
        val serviceActive = VoiceControlAccessibilityService.currentInstance != null ||
                VoiceControlAccessibilityService.isAccessibilitySettingsEnabled(context)
        val micGranted = voiceRecognitionManager?.hasMicrophonePermission() == true

        _uiState.update {
            it.copy(
                isAccessibilityActive = serviceActive,
                isMicPermissionGranted = micGranted
            )
        }
    }

    fun toggleListening() {
        if (_uiState.value.voiceState == VoiceState.LISTENING) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun startListening() {
        refreshStatus()
        _uiState.update {
            it.copy(
                statusMessage = "Listening for commands…",
                voiceState = VoiceState.LISTENING,
                liveTranscript = ""
            )
        }
        voiceRecognitionManager?.startListening(_uiState.value.languageCode)
    }

    fun stopListening() {
        voiceRecognitionManager?.stopListening()
        _uiState.update {
            it.copy(
                statusMessage = "Processing speech…",
                voiceState = VoiceState.PROCESSING
            )
        }
    }

    private fun onSpeechRecognized(spokenText: String) {
        processCommand(spokenText)
    }

    fun processCommand(commandText: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    liveTranscript = commandText,
                    voiceState = VoiceState.PROCESSING,
                    statusMessage = "Analyzing: \"$commandText\""
                )
            }

            val parsed = parser.parse(commandText)
            _uiState.update { it.copy(interpretedCommand = parsed) }

            // Check if confirmation is required
            if (parsed.requiresConfirmation && _uiState.value.confirmationForRiskyActions) {
                _uiState.update {
                    it.copy(
                        voiceState = VoiceState.IDLE,
                        pendingConfirmation = parsed,
                        statusMessage = parsed.confirmationPrompt ?: "Confirmation required."
                    )
                }
                if (_uiState.value.isTtsEnabled) {
                    ttsManager.speak(parsed.confirmationPrompt ?: "Should I continue?")
                }
                return@launch
            }

            executeParsedCommand(parsed)
        }
    }

    fun confirmPendingAction() {
        val pending = _uiState.value.pendingConfirmation ?: return
        _uiState.update { it.copy(pendingConfirmation = null) }
        executeParsedCommand(pending)
    }

    fun cancelPendingAction() {
        val pending = _uiState.value.pendingConfirmation
        _uiState.update {
            it.copy(
                pendingConfirmation = null,
                voiceState = VoiceState.IDLE,
                statusMessage = "Action cancelled."
            )
        }
        if (_uiState.value.isTtsEnabled) {
            ttsManager.speak("Action cancelled.")
        }
        if (_uiState.value.isContinuousMode) {
            scheduleContinuousRestart()
        }
    }

    private fun executeParsedCommand(command: ParsedCommand) {
        executionJob?.cancel()
        executionJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    voiceState = VoiceState.EXECUTING,
                    statusMessage = "Executing ${command.actions.size} action(s)…"
                )
            }

            voiceRecognitionManager?.updateVoiceState(VoiceState.EXECUTING)

            val result = executor.executeCommand(command)

            when (result) {
                is ExecutionResult.Success -> {
                    _uiState.update {
                        it.copy(
                            voiceState = VoiceState.COMPLETED,
                            statusMessage = result.message
                        )
                    }
                }
                is ExecutionResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            voiceState = VoiceState.ERROR,
                            statusMessage = result.reason
                        )
                    }
                }
            }

            delay(2500L)
            if (_uiState.value.voiceState == VoiceState.COMPLETED || _uiState.value.voiceState == VoiceState.ERROR) {
                _uiState.update {
                    it.copy(voiceState = VoiceState.IDLE)
                }
            }

            if (_uiState.value.isContinuousMode) {
                scheduleContinuousRestart()
            }
        }
    }

    private fun scheduleContinuousRestart() {
        viewModelScope.launch {
            delay(1500L)
            if (_uiState.value.isContinuousMode && _uiState.value.voiceState == VoiceState.IDLE) {
                startListening()
            }
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        settingsRepo.setTtsEnabled(enabled)
    }

    fun setContinuousMode(enabled: Boolean) {
        settingsRepo.setContinuousMode(enabled)
        if (enabled && _uiState.value.voiceState == VoiceState.IDLE) {
            startListening()
        }
    }

    fun setLanguage(code: String) {
        settingsRepo.setLanguage(code)
    }

    fun setSpeechRate(rate: Float) {
        settingsRepo.setSpeechRate(rate)
    }

    fun setConfirmationForRiskyActions(enabled: Boolean) {
        settingsRepo.setConfirmationForRiskyActions(enabled)
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepo.clearHistory()
        }
    }

    fun setShowSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    override fun onCleared() {
        super.onCleared()
        executionJob?.cancel()
        voiceRecognitionManager?.destroy()
    }
}
