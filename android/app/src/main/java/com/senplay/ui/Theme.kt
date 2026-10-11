package com.senplay.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFF4CC2FF),
    onPrimary = Color(0xFF00243A),
    secondary = Color(0xFF8ED0FF),
    background = Color(0xFF0E1114),
    surface = Color(0xFF14181C),
    surfaceVariant = Color(0xFF1D2329),
    onSurface = Color(0xFFE6EAEE),
    onSurfaceVariant = Color(0xFFA9B4BE)
)

private val Light = lightColorScheme(
    primary = Color(0xFF00629E),
    secondary = Color(0xFF0E6FA8),
    background = Color(0xFFF6F8FA),
    surface = Color(0xFFFFFFFF)
)

@Composable
fun SenPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
