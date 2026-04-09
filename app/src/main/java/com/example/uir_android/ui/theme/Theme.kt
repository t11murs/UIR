package com.example.uir_android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentAmber,
    secondary = OceanBlue,
    tertiary = SuccessGreen,
    background = SurfaceDark,
    surface = SurfaceDark,
    onPrimary = SurfaceDark,
    onSecondary = SurfaceLight,
    onBackground = SurfaceLight,
    onSurface = SurfaceLight,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = OceanBlue,
    secondary = AccentAmber,
    tertiary = SuccessGreen,
    background = SurfaceLight,
    surface = SurfaceLight,
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

