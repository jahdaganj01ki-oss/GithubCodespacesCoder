package com.gitcodera.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF79C0FF),
    secondary = Color(0xFF7EE787),
    background = Color(0xFF0D1117),
    surface = Color(0xFF161B22),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0969DA),
    secondary = Color(0xFF1A7F37),
)

@Composable
fun GitCoderATheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
