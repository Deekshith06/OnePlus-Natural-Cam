package com.deekshith.naturalcam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = OnePlusRed,
    secondary = AmberAccent,
    background = Black,
    surface = DarkGray,
    onPrimary = White,
    onSecondary = Black,
    onBackground = White,
    onSurface = White
)

@Composable
fun OnePlusNaturalCamTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
