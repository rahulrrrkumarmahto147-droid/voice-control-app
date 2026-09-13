package com.example.executor

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.action.ActionType
import com.example.action.ParsedCommand
import com.example.action.ScrollDirection
import com.example.action.SwipeDirection
import com.example.applauncher.AppResolver
import com.example.data.model.CommandStatus
import com.example.data.repository.HistoryRepository
import com.example.service.VoiceControlAccessibilityService
import com.example.voice.TtsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface ExecutionResult {
    data class Success(val message: String) : ExecutionResult
    data class Failure(val reason: String) : ExecutionResult
}

class ActionExecutor(
    private val context: Context,
    private val appResolver: AppResolver,
    private val historyRepository: HistoryRepository,
    private val ttsManager: TtsManager,
    private val isTtsEnabled: () -> Boolean
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _currentAction = MutableStateFlow<ActionType?>(null)
    val currentAction: StateFlow<ActionType?> = _currentAction.asStateFlow()

    private val _executionStatusMessage = MutableStateFlow("")
    val executionStatusMessage: StateFlow<String> = _executionStatusMessage.asStateFlow()

    suspend fun executeCommand(command: ParsedCommand): ExecutionResult = withContext(Dispatchers.Main) {
        val actions = command.actions
        if (actions.isEmpty()) {
            val msg = "I couldn't recognize an actionable command from: \"${command.rawText}\""
            speakIfEnabled(msg)
            historyRepository.logCommand(
                originalCommand = command.rawText,
                interpretedAction = "None",
                status = CommandStatus.FAILED,
                resultMessage = msg
            )
            return@withContext ExecutionResult.Failure(msg)
        }

        val total = actions.size
        var lastSuccessMessage = "Command executed successfully."

        for ((index, action) in actions.withIndex()) {
            _currentAction.value = action
            _executionStatusMessage.value = "Executing (${index + 1}/$total): ${action.description}"
            Log.d(TAG, "Step ${index + 1}/$total: ${action.description}")

            val stepResult = executeSingleAction(action)
            if (stepResult is ExecutionResult.Failure) {
                _currentAction.value = null
                _executionStatusMessage.value = "Failed: ${stepResult.reason}"
                speakIfEnabled(stepResult.reason)

                historyRepository.logCommand(
                    originalCommand = command.rawText,
                    interpretedAction = actions.joinToString { it.description },
                    status = CommandStatus.FAILED,
                    resultMessage = stepResult.reason
                )
                return@withContext stepResult
            } else if (stepResult is ExecutionResult.Success) {
                lastSuccessMessage = stepResult.message
            }
        }

        _currentAction.value = null
        _executionStatusMessage.value = lastSuccessMessage
        speakIfEnabled(lastSuccessMessage)

        historyRepository.logCommand(
            originalCommand = command.rawText,
            interpretedAction = actions.joinToString { it.description },
            status = CommandStatus.SUCCESS,
            resultMessage = lastSuccessMessage
        )

        return@withContext ExecutionResult.Success(lastSuccessMessage)
    }

    private suspend fun executeSingleAction(action: ActionType): ExecutionResult {
        return when (action) {
            is ActionType.OpenApp -> {
                val launched = appResolver.launchApp(action.appName)
                if (launched) {
                    ExecutionResult.Success("${action.appName} opened.")
                } else {
                    ExecutionResult.Failure("Could not find or open app \"${action.appName}\". Is it installed?")
                }
            }

            is ActionType.Back -> {
                val service = VoiceControlAccessibilityService.currentInstance
                if (service == null) {
                    return ExecutionResult.Failure("Accessibility Service is disabled. Please enable it in Settings.")
                }
                for (i in 0 until action.times) {
                    service.performBack()
                    if (action.times > 1 && i < action.times - 1) {
                        delay(400L)
                    }
                }
                ExecutionResult.Success(if (action.times > 1) "Went back ${action.times} times." else "Went back.")
            }

            is ActionType.Home -> {
                val service = VoiceControlAccessibilityService.currentInstance
                if (service == null) {
                    return ExecutionResult.Failure("Accessibility Service is disabled. Please enable it in Settings.")
                }
                val success = service.performHome()
                if (success) ExecutionResult.Success("Returned home.") else ExecutionResult.Failure("Could not navigate home.")
            }

            is ActionType.Recents -> {
                val service = VoiceControlAccessibilityService.currentInstance
                if (service == null) {
                    return ExecutionResult.Failure("Accessibility Service is disabled. Please enable it in Settings.")
                }
                val success = service.performRecents()
                if (success) ExecutionResult.Success("Recent apps opened.") else ExecutionResult.Failure("Could not open recent apps.")
            }

            is ActionType.Tap -> {
                val service = VoiceControlAccessibilityService.currentInstance
                if (service == null) {
                    return ExecutionResult.Failure("Accessibility Service is required to tap UI elements.")
                }

                // Retry logic: try finding the button over 3 attempts with 600ms delays
                var clicked = false
                for (attempt in 1..3) {
                    clicked = service.findAndClick(action.target)
                    if (clicked) break
                    delay(600L)
                }

                if (clicked) {
                    ExecutionResult.Success("Tapped \"${action.target}\".")
                } else {
                    ExecutionResult.Failure("I couldn't find or tap \"${action.target}\" on the current screen.")
                }
            }

            is ActionType.TapCoordinates -> {
                val service = VoiceControlAccessibilityService.currentInstance
                if (service == null) {
                    return ExecutionResult.Failure("Accessibility Service is required for tap gestures.")
                }
                val tapped = service.dispatchTap(action.x, action.y)
                if (tapped) ExecutionResult.Success("Tapped screen coordinate.") else ExecutionResult.Failure("Could not tap coordinate.")
            }

            is ActionType.LongPress -> {
                val service = VoiceControlAccessibilityService.currentInstance
                if (service == null) {
                    return ExecutionResult.Failure("Accessibility Service is required for long press.")
                }
                val pressed = service.findAndLongClick(action.target)
                if (pressed) {
                    ExecutionResult.Success("Long pressed \"${action.target}\".")
                } else {
                    ExecutionResult.Failure("Could not long press \"${action.target}\".")
                }
            }

            is ActionType.Scroll -> {
                val service = VoiceControlAccessibilityService.currentInstance
                if (service == null) {
                    return ExecutionResult.Failure("Accessibility Service is required for scrolling.")
                }
                val scrolled = service.performScroll(action.direction)
                if (scrolled) {
                    ExecutionResult.Success("Scrolled ${action.direction.name.lowercase()}.")
                } else {
                    ExecutionResult.Failure("Could not scroll on the current screen.")
                }
            }

            is ActionType.Swipe -> {
                val service = VoiceControlAccessibilityService.currentInstance
                if (service == null) {
                    return ExecutionResult.Failure("Accessibility Service is required for swiping.")
                }
                val swiped = service.performSwipe(action.direction)
                if (swiped) {
                    ExecutionResult.Success("Swiped ${action.direction.name.lowercase()}.")
                } else {
                    ExecutionResult.Failure("Could not perform swipe gesture.")
                }
            }

            is ActionType.TypeText -> {
                val service = VoiceControlAccessibilityService.currentInstance
                if (service == null) {
                    return ExecutionResult.Failure("Accessibility Service is required to type text.")
                }

                // If target field is specified or needs focus, retry up to 2 times
                var typed = false
                for (attempt in 1..2) {
                    typed = service.setEditText(action.targetField, action.text)
                    if (typed) break
                    delay(500L)
                }

                if (typed) {
                    ExecutionResult.Success("Typed \"${action.text}\".")
                } else {
                    ExecutionResult.Failure("Could not find an active text field to type into.")
                }
            }

            is ActionType.PressEnter -> {
                // In Android Accessibility, clicking the IME search action or search node
                val service = VoiceControlAccessibilityService.currentInstance
                if (service != null) {
                    // Try clicking "Search" / "Go" / "Submit" button if present
                    val clickedSearch = service.findAndClick("Search") || service.findAndClick("Go")
                    if (clickedSearch) {
                        ExecutionResult.Success("Search submitted.")
                    } else {
                        // Delay briefly to allow auto-search
                        delay(600L)
                        ExecutionResult.Success("Submitted.")
                    }
                } else {
                    ExecutionResult.Success("Submitted.")
                }
            }

            is ActionType.TakeScreenshot -> {
                val service = VoiceControlAccessibilityService.currentInstance
                if (service == null) {
                    return ExecutionResult.Failure("Accessibility Service is required to take screenshots.")
                }
                val captured = service.performScreenshot()
                if (captured) {
                    ExecutionResult.Success("Screenshot captured.")
                } else {
                    ExecutionResult.Failure("Screenshot capture not supported on this Android version.")
                }
            }

            is ActionType.ReadScreen -> {
                val service = VoiceControlAccessibilityService.currentInstance
                if (service == null) {
                    return ExecutionResult.Failure("Accessibility Service is required to read screen content.")
                }
                val content = service.readScreen()
                ExecutionResult.Success(content)
            }

            is ActionType.Wait -> {
                delay(action.durationMs)
                ExecutionResult.Success("Waited.")
            }

            is ActionType.Speak -> {
                speakIfEnabled(action.message)
                ExecutionResult.Success(action.message)
            }

            is ActionType.VolumeUp -> {
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                ExecutionResult.Success("Volume increased.")
            }

            is ActionType.VolumeDown -> {
                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                ExecutionResult.Success("Volume decreased.")
            }

            is ActionType.FindText,
            is ActionType.FindDescription,
            is ActionType.FindId -> {
                ExecutionResult.Success("Element located.")
            }
        }
    }

    private fun speakIfEnabled(message: String) {
        if (isTtsEnabled()) {
            ttsManager.speak(message)
        }
    }

    companion object {
        private const val TAG = "ActionExecutor"
    }
}
