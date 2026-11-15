package com.cdvirtual.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = CdPrimary, onPrimary = CdOnPrimary, secondary = CdSecondary,
    background = CdBackgroundDark, surface = CdSurfaceDark
)
private val LightColors = lightColorScheme(
    primary = CdPrimary, onPrimary = CdOnPrimary, secondary = CdSecondary,
    background = CdBackgroundLight, surface = CdSurfaceLight
)

@Composable
fun CDVirtualTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
