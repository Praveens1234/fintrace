package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * AMOLED / dark scheme. Every Material 3 surface role is mapped onto the stepped surface scale
 * so cards, dialogs and menus separate by lightness (the only elevation cue that reads on black).
 */
private val AmoledDarkColorScheme = darkColorScheme(
    primary = BrandDark,
    onPrimary = Color(0xFF00251A),
    primaryContainer = BrandDarkContainer,
    onPrimaryContainer = Color(0xFFB6F5DD),
    secondary = Color(0xFF9FB0C2),
    onSecondary = Color(0xFF0E1116),
    secondaryContainer = Color(0xFF1C2630),
    onSecondaryContainer = Color(0xFFCFE0F0),
    tertiary = DemoPurple,
    onTertiary = Color(0xFFFFFFFF),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = Color(0xFF1E1E24),
    error = AlertCritical,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3B1416),
    onErrorContainer = Color(0xFFFFD9D7),
    scrim = Color(0xCC000000)
)

/** Light scheme — deep-teal brand, neutral cool greys, AA-contrast text. */
private val StandardLightColorScheme = lightColorScheme(
    primary = BrandLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = BrandLightContainer,
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF455A64),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E3EC),
    onSecondaryContainer = Color(0xFF0E1116),
    tertiary = Color(0xFF6D4BC4),
    onTertiary = Color(0xFFFFFFFF),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceContainer,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = LightBackground,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    outlineVariant = Color(0xFFE6E8ED),
    error = PriceDownLight,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0x99000000)
)

@Composable
fun FinTraceTheme(
    mode: String = "AMOLED", // "Light", "AMOLED", "System"
    content: @Composable () -> Unit
) {
    val darkTheme = when (mode) {
        "Light" -> false
        "AMOLED" -> true
        else -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val colorScheme = when {
        mode == "AMOLED" -> AmoledDarkColorScheme
        mode == "Light" -> StandardLightColorScheme
        // System mode honours Material You dynamic color on Android 12+
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AmoledDarkColorScheme
        else -> StandardLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = FinTraceShapes,
        content = content
    )
}
