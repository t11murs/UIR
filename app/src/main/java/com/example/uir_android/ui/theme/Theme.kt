package com.example.uir_android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = OceanBlueLight,
    secondary = DarkBlueAccent,
    tertiary = SuccessGreenDark,
    background = SurfaceDark,
    surface = SurfaceContainerDark,
    surfaceVariant = SurfaceVariantDark,
    primaryContainer = OceanBlueDark,
    onPrimaryContainer = OnDarkBlueContainer,
    secondaryContainer = DarkBlueContainer,
    onPrimary = OceanBlueDark,
    onSecondary = SurfaceDark,
    onSecondaryContainer = OnDarkBlueContainer,
    tertiaryContainer = Color(0xFF174D2A),
    onTertiaryContainer = Color(0xFFC5F2CB),
    onBackground = SurfaceLight,
    onSurface = SurfaceLight,
    onSurfaceVariant = OceanBlueLight,
    error = ErrorRedDark,
    outline = OutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = OceanBlue,
    secondary = AccentAmber,
    tertiary = SuccessGreen,
    background = SurfaceLight,
    surface = SurfaceContainerLight,
    surfaceVariant = Color(0xFFE7EDF2),
    primaryContainer = Color(0xFFD5E7FA),
    onPrimaryContainer = OceanBlueDark,
    secondaryContainer = Color(0xFFFFE2A3),
    onSecondaryContainer = AccentAmberDark,
    tertiaryContainer = Color(0xFFC8EACB),
    onTertiaryContainer = Color(0xFF123D1B),
    onPrimary = SurfaceLight,
    onSecondary = OceanBlueDark,
    onBackground = OceanBlueDark,
    onSurface = OceanBlueDark,
    error = ErrorRed,
    outline = OutlineGray
)

@Composable
fun UIR_androidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}

