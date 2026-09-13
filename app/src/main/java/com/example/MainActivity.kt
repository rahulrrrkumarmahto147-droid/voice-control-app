package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.action.ActionType
import com.example.data.model.CommandHistoryEntity
import com.example.data.model.CommandStatus
import com.example.service.VoiceControlAccessibilityService
import com.example.ui.MainViewModel
import com.example.ui.VoiceControlUiState
import com.example.ui.theme.StateCompleted
import com.example.ui.theme.StateError
import com.example.ui.theme.StateExecuting
import com.example.ui.theme.StateIdle
import com.example.ui.theme.StateListening
import com.example.ui.theme.StateProcessing
import com.example.ui.theme.VoiceControlTheme
import com.example.voice.VoiceState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoiceControlTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current

                // Check permissions & service status on resume
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel.refreshStatus()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                // Microphone permission request launcher
                val micPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    viewModel.refreshStatus()
                    if (isGranted) {
                        viewModel.startListening()
                    }
                }

                MainScreen(
                    uiState = uiState,
                    onMicClick = {
                        val hasMicPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasMicPermission) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.toggleListening()
                        }
                    },
                    onRequestMicPermission = {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onOpenAccessibilitySettings = {
                        context.startActivity(VoiceControlAccessibilityService.createAccessibilitySettingsIntent())
                    },
                    onQuickCommandSelected = { commandText ->
                        viewModel.processCommand(commandText)
                    },
                    onToggleTts = { enabled ->
                        viewModel.setTtsEnabled(enabled)
                    },
                    onToggleContinuous = { enabled ->
                        viewModel.setContinuousMode(enabled)
                    },
                    onOpenSettings = {
                        viewModel.setShowSettingsDialog(true)
                    },
                    onDismissSettings = {
                        viewModel.setShowSettingsDialog(false)
                    },
                    onConfirmAction = {
                        viewModel.confirmPendingAction()
                    },
                    onCancelAction = {
                        viewModel.cancelPendingAction()
                    },
                    onClearHistory = {
                        viewModel.clearHistory()
                    },
                    onChangeLanguage = { code ->
                        viewModel.setLanguage(code)
                    },
                    onChangeSpeechRate = { rate ->
                        viewModel.setSpeechRate(rate)
                    },
                    onToggleRiskyConfirmation = { enabled ->
                        viewModel.setConfirmationForRiskyActions(enabled)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: VoiceControlUiState,
    onMicClick: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onQuickCommandSelected: (String) -> Unit,
    onToggleTts: (Boolean) -> Unit,
    onToggleContinuous: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onDismissSettings: () -> Unit,
    onConfirmAction: () -> Unit,
    onCancelAction: () -> Unit,
    onClearHistory: () -> Unit,
    onChangeLanguage: (String) -> Unit,
    onChangeSpeechRate: (Float) -> Unit,
    onToggleRiskyConfirmation: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "VoiceControl AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.isAccessibilityActive) StateCompleted else StateError
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.isAccessibilityActive) "Service Active" else "Service Disabled",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onToggleTts(!uiState.isTtsEnabled) },
                        modifier = Modifier.testTag("tts_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isTtsEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.Close,
                            contentDescription = "Toggle Voice Response",
                            tint = if (uiState.isTtsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Accessibility Service Alert Banner
            if (!uiState.isAccessibilityActive) {
                item {
                    AccessibilityWarningBanner(onOpenAccessibilitySettings = onOpenAccessibilitySettings)
                }
            }

            // 2. Microphone Permission Banner
            if (!uiState.isMicPermissionGranted) {
                item {
                    MicPermissionBanner(onRequestMicPermission = onRequestMicPermission)
                }
            }

            // 3. Main Center Voice Hub with Animated Mic Button
            item {
                VoiceHubCard(
                    uiState = uiState,
                    onMicClick = onMicClick
                )
            }

            // 4. Safety Confirmation Card (if pending confirmation)
            if (uiState.pendingConfirmation != null) {
                item {
                    SafetyConfirmationCard(
                        prompt = uiState.pendingConfirmation.confirmationPrompt
                            ?: "Are you sure you want to perform this action?",
                        onConfirm = onConfirmAction,
                        onCancel = onCancelAction
                    )
                }
            }

            // 5. Interpreted Command & Action Sequence Card
            if (uiState.interpretedCommand != null) {
                item {
                    InterpretedCommandCard(
                        rawCommand = uiState.interpretedCommand.rawText,
                        actions = uiState.interpretedCommand.actions,
                        currentActionDescription = uiState.currentActionDescription
                    )
                }
            }

            // 6. Quick Test Command Chips (Great for testing without speaking)
            item {
                QuickCommandsSection(onCommandSelected = onQuickCommandSelected)
            }

            // 7. Recent Command History Card
            item {
                CommandHistorySection(
                    historyItems = uiState.historyItems,
                    onClearHistory = onClearHistory,
                    onRerunCommand = onQuickCommandSelected
                )
            }
        }

        // Settings Bottom Sheet
        if (uiState.showSettingsDialog) {
            SettingsBottomSheet(
                uiState = uiState,
                onDismiss = onDismissSettings,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onToggleTts = onToggleTts,
                onToggleContinuous = onToggleContinuous,
                onChangeLanguage = onChangeLanguage,
                onChangeSpeechRate = onChangeSpeechRate,
                onToggleRiskyConfirmation = onToggleRiskyConfirmation,
                onClearHistory = onClearHistory
            )
        }
    }
}

@Composable
fun AccessibilityWarningBanner(onOpenAccessibilitySettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("accessibility_banner"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Accessibility Service Required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "VoiceControl AI needs accessibility permission to tap UI elements, scroll, and type on your behalf.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onOpenAccessibilitySettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("enable_accessibility_button")
                ) {
                    Text("Enable Service in Settings", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun MicPermissionBanner(onRequestMicPermission: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mic_permission_banner"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MicOff,
                contentDescription = "Microphone Off",
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Microphone Access Required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Allow microphone access to recognize spoken voice commands.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRequestMicPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("grant_mic_button")
                ) {
                    Text("Grant Permission", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun VoiceHubCard(
    uiState: VoiceControlUiState,
    onMicClick: () -> Unit
) {
    val stateColor by animateColorAsState(
        targetValue = when (uiState.voiceState) {
            VoiceState.IDLE -> StateIdle
            VoiceState.LISTENING -> StateListening
            VoiceState.PROCESSING -> StateProcessing
            VoiceState.EXECUTING -> StateExecuting
            VoiceState.COMPLETED -> StateCompleted
            VoiceState.ERROR -> StateError
        },
        label = "stateColor"
    )

    val stateText = when (uiState.voiceState) {
        VoiceState.IDLE -> "IDLE • Ready"
        VoiceState.LISTENING -> "LISTENING…"
        VoiceState.PROCESSING -> "PROCESSING…"
        VoiceState.EXECUTING -> "EXECUTING ACTIONS…"
        VoiceState.COMPLETED -> "COMPLETED"
        VoiceState.ERROR -> "ERROR"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (uiState.voiceState == VoiceState.LISTENING) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_hub_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Chip
            Surface(
                color = stateColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, stateColor.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(stateColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stateText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = stateColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Animated Microphone Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                // Outer Pulse Ring when listening or executing
                if (uiState.voiceState == VoiceState.LISTENING || uiState.voiceState == VoiceState.EXECUTING) {
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(stateColor.copy(alpha = 0.2f))
                    )
                }

                // Inner Button
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    stateColor,
                                    stateColor.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .clickable(onClick = onMicClick)
                        .testTag("mic_button"),
                    contentAlignment = Alignment.Center
                ) {
                    when (uiState.voiceState) {
                        VoiceState.LISTENING -> {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Listening",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        VoiceState.PROCESSING -> {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        VoiceState.EXECUTING -> {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        VoiceState.COMPLETED -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        VoiceState.ERROR -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        VoiceState.IDLE -> {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Start Listening",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (uiState.voiceState == VoiceState.LISTENING) "Listening… Tap to stop" else "Tap microphone to speak",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Transcript / Status Text
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (uiState.liveTranscript.isNotBlank()) {
                            "\"${uiState.liveTranscript}\""
                        } else {
                            uiState.statusMessage
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (uiState.liveTranscript.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun SafetyConfirmationCard(
    prompt: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("safety_confirmation_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Security Alert",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Safety Confirmation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("cancel_confirmation_button")
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_action_button")
                ) {
                    Text("Continue")
                }
            }
        }
    }
}

@Composable
fun InterpretedCommandCard(
    rawCommand: String,
    actions: List<ActionType>,
    currentActionDescription: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("interpreted_command_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Interpreted Action Plan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Command: \"$rawCommand\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            actions.forEachIndexed { index, action ->
                val isCurrent = currentActionDescription == action.description
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = action.description,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCurrent) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickCommandsSection(onCommandSelected: (String) -> Unit) {
    val quickCommands = listOf(
        "Open YouTube",
        "Open Chrome",
        "Open Settings",
        "Go back",
        "Go home",
        "Scroll down",
        "Scroll up",
        "Take a screenshot",
        "Read what's on the screen",
        "Open YouTube and search for AI news"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Quick Voice Commands",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            quickCommands.forEach { command ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clickable { onCommandSelected(command) }
                        .testTag("quick_command_${command.replace(' ', '_').lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = command,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CommandHistorySection(
    historyItems: List<CommandHistoryEntity>,
    onClearHistory: () -> Unit,
    onRerunCommand: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("command_history_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (historyItems.isNotEmpty()) {
                    TextButton(
                        onClick = onClearHistory,
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear History",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }

            if (historyItems.isEmpty()) {
                Text(
                    text = "No voice commands executed yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                historyItems.take(8).forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRerunCommand(item.originalCommand) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (item.status) {
                                        CommandStatus.SUCCESS -> StateCompleted
                                        CommandStatus.FAILED -> StateError
                                        CommandStatus.CANCELLED -> StateProcessing
                                        CommandStatus.REQUIRES_CONFIRMATION -> StateProcessing
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.originalCommand,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = item.resultMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = dateFormat.format(Date(item.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    uiState: VoiceControlUiState,
    onDismiss: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onToggleTts: (Boolean) -> Unit,
    onToggleContinuous: (Boolean) -> Unit,
    onChangeLanguage: (String) -> Unit,
    onChangeSpeechRate: (Float) -> Unit,
    onToggleRiskyConfirmation: (Boolean) -> Unit,
    onClearHistory: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings & Accessibility",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close Settings")
                }
            }

            // 1. Accessibility Service Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Android Accessibility Service",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The accessibility service is what allows VoiceControl AI to detect screen buttons, execute gestures, scroll, and launch applications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onOpenAccessibilitySettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isAccessibilityActive) "Accessibility Settings (Active)" else "Enable Accessibility Service")
                    }
                }
            }

            // 2. Voice Response Toggle (TTS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Spoken Responses (TTS)",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "VoiceControl speaks action confirmations and screen reading out loud.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.isTtsEnabled,
                    onCheckedChange = onToggleTts
                )
            }

            // 3. Continuous Voice Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Continuous Voice Mode",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Automatically returns to listening after completing each command.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.isContinuousMode,
                    onCheckedChange = onToggleContinuous
                )
            }

            // 4. Confirmation for Risky Actions Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Confirm Risky Actions",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Requires confirmation before deleting content, sending messages, or purchasing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.confirmationForRiskyActions,
                    onCheckedChange = onToggleRiskyConfirmation
                )
            }

            // 5. Speech Rate Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Speech Rate: ${String.format(Locale.US, "%.1fx", uiState.speechRate)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = uiState.speechRate,
                    onValueChange = onChangeSpeechRate,
                    valueRange = 0.5f..1.8f,
                    steps = 5
                )
            }

            // 6. Voice Recognition Language
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Voice Recognition Language",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onChangeLanguage("en-US") },
                        colors = if (uiState.languageCode == "en-US") ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("English (US)")
                    }
                    OutlinedButton(
                        onClick = { onChangeLanguage("hi-IN") },
                        colors = if (uiState.languageCode == "hi-IN") ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("Hindi (India)")
                    }
                }
            }

            // 7. Privacy & Wake Word Info
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Privacy & AI Architecture",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Voice processing is handled locally without storing permanent audio.\n• Wake word architecture supports \"Hey VoiceControl\" extension.\n• Visual AI ScreenVision hook allows multimodal image models for complex UI scanning.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
