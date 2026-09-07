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
    primary = QuantumDarkPrimary,
    onPrimary = QuantumDarkOnPrimary,
    primaryContainer = QuantumDarkPrimaryContainer,
    onPrimaryContainer = QuantumDarkOnPrimaryContainer,
    secondary = QuantumDarkSecondary,
    onSecondary = QuantumDarkOnSecondary,
    secondaryContainer = QuantumDarkSecondaryContainer,
    onSecondaryContainer = QuantumDarkOnSecondaryContainer,
    tertiary = QuantumDarkTertiary,
    background = QuantumDarkBackground,
    surface = QuantumDarkSurface,
    surfaceVariant = QuantumDarkSurfaceVariant,
    onSurface = QuantumDarkOnSurface
)

private val LightColorScheme = lightColorScheme(
    primary = QuantumPrimary,
    onPrimary = QuantumOnPrimary,
    primaryContainer = QuantumPrimaryContainer,
    onPrimaryContainer = QuantumOnPrimaryContainer,
    secondary = QuantumSecondary,
    onSecondary = QuantumOnSecondary,
    secondaryContainer = QuantumSecondaryContainer,
    onSecondaryContainer = QuantumOnSecondaryContainer,
    tertiary = QuantumTertiary,
    background = QuantumBackground,
    surface = QuantumSurface,
    onSurface = QuantumOnSurface
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep distinctive Quantum branding by default
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

