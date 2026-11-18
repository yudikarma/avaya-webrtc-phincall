package com.phincall.webrtc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PhinCallLightColors = lightColorScheme(
    primary = CrimsonAccent,
    onPrimary = Color.White,
    secondary = SlateNavy,
    onSecondary = Color.White,
    background = CanvasLight,
    onBackground = InkPrimary,
    surface = SurfaceLight,
    onSurface = InkPrimary,
    surfaceVariant = SurfaceSubtle,
    onSurfaceVariant = InkSecondary,
    outline = BorderInteractive,
    outlineVariant = BorderHairline,
    error = StatusPoorLight,
    onError = Color.White
)

private val PhinCallDarkColors = darkColorScheme(
    primary = CrimsonAccent,
    onPrimary = Color.White,
    secondary = SlateNavyPill,
    onSecondary = InkOnDark,
    background = StageDark,
    onBackground = InkOnDark,
    surface = DockDark,
    onSurface = InkOnDark,
    surfaceVariant = DockButtonIdle,
    onSurfaceVariant = InkOnDarkMuted,
    outline = SlateNavyPillBorder,
    outlineVariant = DockButtonActive,
    error = StatusPoorDark,
    onError = StageDark
)

@Composable
fun PhinCallTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PhinCallDarkColors else PhinCallLightColors,
        typography = PhinCallTypography,
        content = content
    )
}
