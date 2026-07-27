package com.asterplay.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val AsterplayColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = BgBase,
    primaryContainer = NeonPurple,
    onPrimaryContainer = TextPrimary,
    secondary = NeonPurple,
    onSecondary = TextPrimary,
    background = BgBase,
    onBackground = TextPrimary,
    surface = BgSurface,
    onSurface = TextPrimary,
    surfaceVariant = BgElevated,
    onSurfaceVariant = TextSecondary,
    border = BgElevated,
)

@Composable
fun AsterplayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AsterplayColors,
        typography = AsterplayTypography,
        content = content,
    )
}
