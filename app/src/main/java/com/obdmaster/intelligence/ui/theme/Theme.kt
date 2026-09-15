package com.obdmaster.intelligence.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF0D47A1)
private val Teal = Color(0xFF00897B)
private val Amber = Color(0xFFFFB300)

private val LightColors = lightColorScheme(
    primary = Blue,
    secondary = Teal,
    tertiary = Amber,
    background = Color(0xFFF5F7FA),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFF80CBC4),
    tertiary = Amber
)

@Composable
fun ObdMasterTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
