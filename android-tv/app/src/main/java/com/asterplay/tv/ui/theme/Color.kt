package com.asterplay.tv.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Paleta baseada na logo UFO neon (roxo/ciano)
val BgBase = Color(0xFF07070F)
val BgSurface = Color(0xFF11121C)
val BgElevated = Color(0xFF1A1B2A)
val BgSelected = Color(0xFF23253B)

val NeonPurple = Color(0xFF7C3AED)
val NeonPurpleGlow = Color(0xFFB794F4)
val NeonCyan = Color(0xFF22D3EE)
val NeonCyanGlow = Color(0xFF67E8F9)

val TextPrimary = Color(0xFFF5F6FA)
val TextSecondary = Color(0xFFB0B3C7)
val TextMuted = Color(0xFF6C6F85)

val Accent = NeonCyan
val AccentGlow = NeonCyanGlow

val BrandGradient = Brush.linearGradient(
    listOf(NeonPurple, NeonCyan)
)

val BackgroundGradient = Brush.verticalGradient(
    listOf(Color(0xFF0B0B18), Color(0xFF05050C))
)
