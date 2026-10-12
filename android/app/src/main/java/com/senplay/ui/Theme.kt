package com.senplay.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.senplay.core.LibraryStore

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

/**
 * The seven accents the settings screen offers, as the seed each scheme is built from.
 * Kept as plain colours rather than Material's tonal palette: the reference app picks
 * an accent, not a full dynamic theme, and generating tonal roles from a hue would
 * change surfaces the user did not ask to change.
 */
private val Accents = mapOf(
    "blue" to Color(0xFF4CC2FF),
    "cyan" to Color(0xFF4DD0E1),
    "green" to Color(0xFF81C784),
    "purple" to Color(0xFFBA68C8),
    "orange" to Color(0xFFFFB74D),
    "red" to Color(0xFFE57373),
    "indigo" to Color(0xFF7986CB)
)

@Composable
fun SenPlayTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val chosen = LibraryStore.get(context).prefString("general.themeColor", "blue")
    val accent = Accents[chosen] ?: Accents.getValue("blue")
    // "外观主题": an explicit choice wins; "system" — the default, and what every
    // install was doing before this setting existed — hands it back to the OS.
    val appearance = LibraryStore.get(context).prefString("general.appearance", "system")
    val dark = when (appearance) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val scheme = (if (dark) Dark else Light).copy(
        primary = if (dark) accent else accent.copy(alpha = 1f),
        secondary = accent
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
