package com.lantransfer.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B9A72),
    secondary = Color(0xFF6AA883),
    tertiary = Color(0xFFC79352),
    background = Color(0xFFF2F3F5),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF2E3338),
    onSurface = Color(0xFF2E3338),
    surfaceVariant = Color(0xFFEBEDEF),
    onSurfaceVariant = Color(0xFF5B616A),
    outlineVariant = Color(0xFFD4D7DC)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5CAD73),
    secondary = Color(0xFF8DC7A0),
    tertiary = Color(0xFFD1A35C),
    background = Color(0xFF1E1F22),
    surface = Color(0xFF313338),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFFF2F3F5),
    onSurface = Color(0xFFF2F3F5),
    surfaceVariant = Color(0xFF232428),
    onSurfaceVariant = Color(0xFFB5BAC1),
    outlineVariant = Color(0xFF404249)
)

@Composable
fun HyperDropTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
