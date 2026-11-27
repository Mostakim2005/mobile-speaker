package com.example.audiobtbridge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Google Cobalt Material 3 Tonal Palette (aligned with SharedLibraries/m3-tokens)
private val CobaltLightColorScheme = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF0369A1),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFF047857),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF065F46),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEDF2FA),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFFCBD5E1),
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val CobaltDarkColorScheme = darkColorScheme(
    primary = Color(0xFF93C5FD),
    onPrimary = Color(0xFF0B1220),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF082F49),
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = Color(0xFFD1FAE5),
    background = Color(0xFF0B0F17),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF121826),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF192234),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

@Immutable
data class SpeakerStatusColors(
    val synced: Color,
    val syncedContainer: Color,
    val onSyncedContainer: Color,
    val adapting: Color,
    val adaptingContainer: Color,
    val onAdaptingContainer: Color,
    val cardElevated: Color
)

private val LightStatusColors = SpeakerStatusColors(
    synced = Color(0xFF047857),
    syncedContainer = Color(0xFFD1FAE5),
    onSyncedContainer = Color(0xFF065F46),
    adapting = Color(0xFFB45309),
    adaptingContainer = Color(0xFFFEF3C7),
    onAdaptingContainer = Color(0xFF78350F),
    cardElevated = Color(0xFFEDF2FA)
)

private val DarkStatusColors = SpeakerStatusColors(
    synced = Color(0xFF34D399),
    syncedContainer = Color(0xFF064E3B),
    onSyncedContainer = Color(0xFFD1FAE5),
    adapting = Color(0xFFFBBF24),
    adaptingContainer = Color(0xFF78350F),
    onAdaptingContainer = Color(0xFFFEF3C7),
    cardElevated = Color(0xFF192234)
)

val LocalSpeakerStatusColors = staticCompositionLocalOf { DarkStatusColors }

@Composable
fun MobileSpeakerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> CobaltDarkColorScheme
        else -> CobaltLightColorScheme
    }
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(LocalSpeakerStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
