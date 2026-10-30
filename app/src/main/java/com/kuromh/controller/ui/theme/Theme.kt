package com.kuromh.controller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    background = BackgroundBlack,
    surface = CardDarkGray,
    primary = AccentLight,
    onPrimary = BackgroundBlack,
    onBackground = TextWhite,
    onSurface = TextWhite
)

@Composable
fun KUROMHTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
