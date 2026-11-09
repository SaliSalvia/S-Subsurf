package com.salisalvia.subsurface.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ExplorerDarkColors = darkColorScheme(
    primary = Color(0xFF8DC8F5),
    onPrimary = Color(0xFF00344F),
    secondary = Color(0xFFF1AA47),
    onSecondary = Color(0xFF422B00),
    tertiary = Color(0xFF80D4C7),
    background = Color(0xFF08121E),
    onBackground = Color(0xFFE4EDF7),
    surface = Color(0xFF101E2D),
    onSurface = Color(0xFFE4EDF7),
    surfaceVariant = Color(0xFF243447),
    onSurfaceVariant = Color(0xFFB9C9D9),
    error = Color(0xFFFFB4AB),
)

private val ExplorerLightColors = lightColorScheme(
    primary = Color(0xFF0B5FA5),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF9A5B00),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF0E6B5E),
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF101E2D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101E2D),
    surfaceVariant = Color(0xFFDCE6F1),
    onSurfaceVariant = Color(0xFF3D4F63),
    error = Color(0xFFBA1A1A),
)

@Composable
fun SubsurfaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ExplorerDarkColors else ExplorerLightColors,
        typography = ExplorerTypography,
        content = content,
    )
}
