package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ShareItColorScheme = lightColorScheme(
    primary = ShareItBlue,
    onPrimary = Color.White,
    primaryContainer = ShareItBlueLight,
    onPrimaryContainer = ShareItBlueDark,
    secondary = ShareItGreen,
    onSecondary = Color.White,
    secondaryContainer = ShareItGreenLight,
    onSecondaryContainer = Color(0xFF065F46),
    tertiary = CategoryMusicOrange,
    background = BgLight,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondary,
    outline = DividerColor
)

@Composable
fun SwiftShareTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ShareItColorScheme,
        typography = Typography,
        content = content
    )
}
