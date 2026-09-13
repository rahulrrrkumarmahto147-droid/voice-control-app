package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = VoicePrimaryDark,
    onPrimary = VoiceOnPrimaryDark,
    primaryContainer = VoicePrimaryContainerDark,
    onPrimaryContainer = VoiceOnPrimaryContainerDark,
    secondary = VoiceSecondaryDark,
    onSecondary = VoiceOnSecondaryDark,
    secondaryContainer = VoiceSecondaryContainerDark,
    onSecondaryContainer = VoiceOnSecondaryContainerDark,
    tertiary = VoiceTertiaryDark,
    background = VoiceBackgroundDark,
    surface = VoiceSurfaceDark,
    surfaceVariant = VoiceSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = VoicePrimaryLight,
    onPrimary = VoiceOnPrimaryLight,
    primaryContainer = VoicePrimaryContainerLight,
    onPrimaryContainer = VoiceOnPrimaryContainerLight,
    secondary = VoiceSecondaryLight,
    onSecondary = VoiceOnSecondaryLight,
    secondaryContainer = VoiceSecondaryContainerLight,
    onSecondaryContainer = VoiceOnSecondaryContainerLight,
    tertiary = VoiceTertiaryLight,
    background = VoiceBackgroundLight,
    surface = VoiceSurfaceLight,
    surfaceVariant = VoiceSurfaceVariantLight
)

@Composable
fun VoiceControlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) = VoiceControlTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
