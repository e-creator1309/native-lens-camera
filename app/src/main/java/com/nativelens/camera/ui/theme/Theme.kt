package com.nativelens.camera.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NativeLensColorScheme = darkColorScheme(
    primary = LensGreen,
    background = LensDark,
    surface = LensDarkSurface
)

@Composable
fun NativeLensTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NativeLensColorScheme,
        typography = NativeLensTypography,
        content = content
    )
}
