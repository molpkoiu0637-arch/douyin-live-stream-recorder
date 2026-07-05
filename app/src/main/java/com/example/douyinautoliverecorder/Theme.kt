package com.example.douyinautoliverecorder

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DouyinRed = Color(0xFFFE2C55)

private val DarkColors = darkColorScheme(
    primary = DouyinRed,
    onPrimary = Color.White,
    background = Color(0xFF0E1013),
    onBackground = Color(0xFFF2F4F8),
    surface = Color(0xFF181B21),
    onSurface = Color(0xFFF2F4F8),
    surfaceVariant = Color(0xFF22262E),
    onSurfaceVariant = Color(0xFF8A93A5),
    secondaryContainer = Color(0xFF22262E),
    onSecondaryContainer = Color(0xFFC4CBD8),
    outline = Color(0xFF262A32),
    outlineVariant = Color(0xFF23262D),
    error = Color(0xFFFF6B7D),
    surfaceContainerHigh = Color(0xFF1C2027)
)

private val LightColors = lightColorScheme(
    primary = DouyinRed,
    onPrimary = Color.White,
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF191C22),
    surface = Color.White,
    onSurface = Color(0xFF191C22),
    surfaceVariant = Color(0xFFEEF0F3),
    onSurfaceVariant = Color(0xFF6D7684),
    secondaryContainer = Color(0xFFEEF0F3),
    onSecondaryContainer = Color(0xFF4B5563),
    outline = Color(0xFFE5E8EC),
    outlineVariant = Color(0xFFECEFF2),
    error = Color(0xFFE11D48),
    surfaceContainerHigh = Color.White
)

data class StatusStyle(
    val ring: Color,
    val avatarBg: Color,
    val avatarFg: Color,
    val chipBg: Color,
    val chipFg: Color
)

data class StatusPalette(
    val recording: StatusStyle,
    val saving: StatusStyle,
    val live: StatusStyle,
    val neutral: StatusStyle,
    val statRecording: Color,
    val statLive: Color,
    val statOffline: Color,
    val dotOffline: Color,
    val recordingCardBorder: Color,
    val expandedCardBorder: Color,
    val progressFill: Color,
    val heroStoppedBg: Color,
    val heroStoppedFg: Color
)

private val DarkStatusPalette = StatusPalette(
    recording = StatusStyle(
        ring = DouyinRed,
        avatarBg = Color(0xFF3A2430),
        avatarFg = Color(0xFFFF9AA8),
        chipBg = Color(0xFF3A1B22),
        chipFg = Color(0xFFFF8494)
    ),
    saving = StatusStyle(
        ring = Color(0xFFFBBF24),
        avatarBg = Color(0xFF332B1C),
        avatarFg = Color(0xFFF5C96B),
        chipBg = Color(0xFF32291A),
        chipFg = Color(0xFFF5C96B)
    ),
    live = StatusStyle(
        ring = Color(0xFF34D399),
        avatarBg = Color(0xFF1D3328),
        avatarFg = Color(0xFF7FE6B0),
        chipBg = Color(0xFF1C2F26),
        chipFg = Color(0xFF6FE0A8)
    ),
    neutral = StatusStyle(
        ring = Color(0xFF3A4150),
        avatarBg = Color(0xFF242A35),
        avatarFg = Color(0xFF9DB1D6),
        chipBg = Color(0xFF22262E),
        chipFg = Color(0xFF8A93A5)
    ),
    statRecording = Color(0xFFFF8494),
    statLive = Color(0xFF6FE0A8),
    statOffline = Color(0xFF8A93A5),
    dotOffline = Color(0xFF5A6272),
    recordingCardBorder = Color(0xFF54222D),
    expandedCardBorder = Color(0xFF333A47),
    progressFill = Color(0xFFFBBF24),
    heroStoppedBg = Color(0xFF2C313B),
    heroStoppedFg = Color(0xFFAEB7C6)
)

private val LightStatusPalette = StatusPalette(
    recording = StatusStyle(
        ring = DouyinRed,
        avatarBg = Color(0xFFFFE3E8),
        avatarFg = Color(0xFFD61F45),
        chipBg = Color(0xFFFFE3E8),
        chipFg = Color(0xFFD61F45)
    ),
    saving = StatusStyle(
        ring = Color(0xFFF59E0B),
        avatarBg = Color(0xFFFDF0D5),
        avatarFg = Color(0xFFA16207),
        chipBg = Color(0xFFFDF0D5),
        chipFg = Color(0xFFA16207)
    ),
    live = StatusStyle(
        ring = Color(0xFF10B981),
        avatarBg = Color(0xFFD9F4E6),
        avatarFg = Color(0xFF0F8A55),
        chipBg = Color(0xFFD9F4E6),
        chipFg = Color(0xFF0F8A55)
    ),
    neutral = StatusStyle(
        ring = Color(0xFFCCD3DC),
        avatarBg = Color(0xFFEEF0F3),
        avatarFg = Color(0xFF6D7684),
        chipBg = Color(0xFFEEF0F3),
        chipFg = Color(0xFF6D7684)
    ),
    statRecording = Color(0xFFD61F45),
    statLive = Color(0xFF0F8A55),
    statOffline = Color(0xFF6D7684),
    dotOffline = Color(0xFFAAB3BF),
    recordingCardBorder = Color(0xFFF6C7D0),
    expandedCardBorder = Color(0xFFD7DCE2),
    progressFill = Color(0xFFF59E0B),
    heroStoppedBg = Color(0xFFD6DAE0),
    heroStoppedFg = Color(0xFF5F6672)
)

@Composable
fun statusPalette(): StatusPalette {
    return if (isSystemInDarkTheme()) DarkStatusPalette else LightStatusPalette
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
