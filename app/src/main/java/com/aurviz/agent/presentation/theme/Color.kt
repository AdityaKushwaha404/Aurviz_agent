package com.aurviz.agent.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ---- Brand palette (raw values, used to build the light and dark schemes) ----
internal val BrandIndigo = Color(0xFF4F46E5)
internal val BrandIndigoDark = Color(0xFF3730A3)
internal val BrandIndigoSoft = Color(0xFF8B93FF)
internal val BrandTeal = Color(0xFF0F766E)

// ---- Status colours: readable on both light and dark surfaces ----
val SuccessGreen = Color(0xFF10B981)
val WarningOrange = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFEF4444)

// ---- Category accents ----
val CategoryWork = Color(0xFF0284C7)
val CategoryPreference = Color(0xFFD97706)
val CategoryPersonal = Color(0xFF7C3AED)
val CategoryGeneral = Color(0xFF4F46E5)

// ---- Semantic tokens: resolve against the active (light/dark) colour scheme ----
val PrimaryBlue: Color @Composable get() = MaterialTheme.colorScheme.primary
val SecondaryTeal: Color @Composable get() = MaterialTheme.colorScheme.secondary
val BackgroundLight: Color @Composable get() = MaterialTheme.colorScheme.background
val SurfaceLight: Color @Composable get() = MaterialTheme.colorScheme.surface
val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val ToolBadgeBackground: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val SurfaceBorder: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
