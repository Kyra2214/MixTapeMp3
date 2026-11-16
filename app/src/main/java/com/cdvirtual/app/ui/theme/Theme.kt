package com.cdvirtual.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = CdPrimary, onPrimary = CdOnPrimary, secondary = CdSecondary,
    tertiary = CdTertiary, background = CdBackgroundDark, surface = CdSurfaceDark,
    surfaceVariant = Color(0xFF202A3D), onSurfaceVariant = Color(0xFFB7C1D5)
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF6546D8), onPrimary = Color.White, secondary = Color(0xFF087F79),
    tertiary = Color(0xFFB94D43), background = CdBackgroundLight, surface = CdSurfaceLight,
    surfaceVariant = Color(0xFFEAE8F2), onSurfaceVariant = Color(0xFF55566A)
)
private val MixTapeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun CDVirtualTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = MixTapeShapes,
        content = content
    )
}
