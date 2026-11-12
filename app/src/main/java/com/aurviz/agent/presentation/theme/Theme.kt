package com.aurviz.agent.presentation.theme


import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryTint,
    onPrimaryContainer = PrimaryBlueVariant,
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceLight,
    onSurface = TextPrimary,
    surfaceVariant = ToolBadgeBackground,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFFD8D7E2),
    surfaceTint = PrimaryBlue,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun AurvizTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = LightColorScheme, content = content)
}
