package com.salisalvia.subsurface.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

@Composable
fun SubsurfaceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ExplorerDarkColors,
        typography = ExplorerTypography,
        content = content,
    )
}
