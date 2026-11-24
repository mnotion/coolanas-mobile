package com.coolanas.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun CoolanasTheme(
    darkMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val light = lightColorScheme(
        primary = Color(0xFF202020),
        onPrimary = Color.White,
        background = Color(0xFFDEDEDE),
        onBackground = Color(0xFF171717),
        surface = Color(0xFFF7F7F7),
        onSurface = Color(0xFF171717),
        surfaceVariant = Color(0xFFEAEAEA),
        onSurfaceVariant = Color(0xFF555555),
        outline = Color(0xFFD7D7D7)
    )
    val dark = darkColorScheme(
        primary = Color(0xFFEAEAEA),
        onPrimary = Color(0xFF111111),
        background = Color(0xFF17191D),
        onBackground = Color(0xFFF4F4F4),
        surface = Color(0xFF24262B),
        onSurface = Color(0xFFF4F4F4),
        surfaceVariant = Color(0xFF303238),
        onSurfaceVariant = Color(0xFFB7BAC1),
        outline = Color(0xFF4D5058)
    )
    MaterialTheme(
        colorScheme = if (darkMode) dark else light,
        content = content,
    )
}
