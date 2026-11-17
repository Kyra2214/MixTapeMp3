package com.cdvirtual.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cdvirtual.app.R

/** Poppins (OFL): letras arredondadas e abertas, bem mais suaves que o Roboto padrão. */
val MixTapeFontFamily = FontFamily(
    Font(R.font.poppins_light, FontWeight.Light),
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_medium, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_bold, FontWeight.ExtraBold)
)

private fun style(weight: FontWeight, size: Int, line: Int, tracking: Float = 0f) = TextStyle(
    fontFamily = MixTapeFontFamily, fontWeight = weight,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.em
)

val MixTapeTypography = Typography(
    displayLarge = style(FontWeight.Bold, 28, 34),
    displayMedium = style(FontWeight.Bold, 26, 32),
    displaySmall = style(FontWeight.Bold, 24, 30),
    headlineLarge = style(FontWeight.Bold, 22, 28),
    headlineMedium = style(FontWeight.Bold, 20, 26),
    headlineSmall = style(FontWeight.Medium, 18, 24),
    titleLarge = style(FontWeight.Medium, 17, 23),
    titleMedium = style(FontWeight.Medium, 15, 21, 0.01f),
    titleSmall = style(FontWeight.Medium, 13, 18, 0.01f),
    bodyLarge = style(FontWeight.Normal, 14, 20, 0.01f),
    bodyMedium = style(FontWeight.Normal, 13, 18, 0.01f),
    bodySmall = style(FontWeight.Normal, 11, 15, 0.02f),
    labelLarge = style(FontWeight.Medium, 13, 18, 0.01f),
    labelMedium = style(FontWeight.Medium, 11, 15, 0.03f),
    labelSmall = style(FontWeight.Medium, 10, 14, 0.04f)
)
