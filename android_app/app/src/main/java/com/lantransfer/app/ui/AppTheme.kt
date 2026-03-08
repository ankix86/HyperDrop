package com.lantransfer.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF53EEFF),
    secondary = Color(0xFFBE4BFF),
    tertiary = Color(0xFF596BFF),
    background = Color(0xFF080A24),
    surface = Color(0xFF12143A),
    onPrimary = Color(0xFF06122A),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFFEFF0FF),
    onSurface = Color(0xFFEFF0FF),
    surfaceVariant = Color(0xFF1E2253),
    onSurfaceVariant = Color(0xFFC7CCFF),
    outlineVariant = Color(0xFF374187)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF53EEFF),
    secondary = Color(0xFFD96BFF),
    tertiary = Color(0xFF7387FF),
    background = Color(0xFF06071C),
    surface = Color(0xFF101336),
    onPrimary = Color(0xFF06122A),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFFEFF0FF),
    onSurface = Color(0xFFEFF0FF),
    surfaceVariant = Color(0xFF1A1F49),
    onSurfaceVariant = Color(0xFFB8BEF9),
    outlineVariant = Color(0xFF333C78)
)

@Composable
fun HyperDropTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
