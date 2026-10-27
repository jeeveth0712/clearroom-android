package com.clearroom.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.clearroom.app.R

/**
 * Manrope (SIL Open Font License, assets/licenses/manrope-OFL.txt), bundled so the app looks the
 * same on every phone and works offline. One variable font file serves every weight.
 */
val Manrope = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map { weight ->
        // The weight axis is set explicitly: without it every weight drew the file's default, thin instance.
        Font(R.font.manrope, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

private fun manrope(size: Double, lineHeight: Double, weight: FontWeight, letterSpacing: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

/** Big, tight, extra-bold headings and numbers; calm body text. Manrope runs wide, so sizes sit a touch under Material's. */
val Typography = Typography(
    displayLarge = manrope(52.0, 60.0, FontWeight.ExtraBold, -1.0),
    displayMedium = manrope(42.0, 50.0, FontWeight.ExtraBold, -0.8),
    displaySmall = manrope(34.0, 42.0, FontWeight.ExtraBold, -0.6),
    headlineLarge = manrope(30.0, 38.0, FontWeight.ExtraBold, -0.5),
    headlineMedium = manrope(27.0, 34.0, FontWeight.ExtraBold, -0.5),
    headlineSmall = manrope(24.0, 31.0, FontWeight.ExtraBold, -0.3),
    titleLarge = manrope(20.0, 27.0, FontWeight.Bold, -0.2),
    titleMedium = manrope(16.0, 23.0, FontWeight.Bold),
    titleSmall = manrope(14.0, 20.0, FontWeight.Bold),
    bodyLarge = manrope(16.0, 24.0, FontWeight.Medium),
    bodyMedium = manrope(14.0, 21.0, FontWeight.Medium),
    bodySmall = manrope(12.5, 18.0, FontWeight.Medium),
    labelLarge = manrope(14.0, 20.0, FontWeight.Bold),
    labelMedium = manrope(12.0, 16.0, FontWeight.SemiBold),
    labelSmall = manrope(11.0, 15.0, FontWeight.SemiBold, 0.1),
)
