package com.feanor08.onering.sample.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SampleDarkColorScheme = darkColorScheme(
    primary = Color(0xFF5AA9FF),
    background = Color(0xFF111111),
    surface = Color(0xFF1B1B1B),
)

private val SampleLightColorScheme = lightColorScheme(
    primary = Color(0xFF356FA2),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF2F2F2),
)

@Composable
public fun OneRingSampleTheme(useDarkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (useDarkTheme) SampleDarkColorScheme else SampleLightColorScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}
