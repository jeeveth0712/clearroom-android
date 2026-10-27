package com.clearroom.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ClearRoom's look (owner's choice, M12): ink and lime on soft grey, light or dark with the phone.
// No red, orange or blue anywhere in the chrome: those hues mean clarity bands (StiColors.kt).

/** Near-black for text, buttons and the navigation bar. */
internal val Ink = Color(0xFF111318)

/** The one accent: selected tabs, highlights, buttons on dark. Always with [Ink] on top, never as text on white. */
internal val Lime = Color(0xFFD4F25C)

private val Mist = Color(0xFFF2F3F5)
private val Slate = Color(0xFF5B616B)
private val NightBackground = Color(0xFF0C0E11)
private val NightCard = Color(0xFF171A1F)
private val NightRaised = Color(0xFF1E2228)
private val NightText = Color(0xFFF2F3F5)
private val NightMuted = Color(0xFFA4AAB4)

internal val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Lime,
    onPrimaryContainer = Ink,
    inversePrimary = Lime,
    secondary = Color(0xFF3D424B),
    onSecondary = Color.White,
    secondaryContainer = Lime,
    onSecondaryContainer = Ink,
    tertiary = Slate,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6E8EC),
    onTertiaryContainer = Ink,
    background = Mist,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFECEEF1),
    onSurfaceVariant = Slate,
    surfaceTint = Color.White,
    inverseSurface = Ink,
    inverseOnSurface = Color.White,
    outline = Color(0xFFC6CAD1),
    outlineVariant = Color(0xFFE2E4E8),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE3E5E9),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FA),
    surfaceContainer = Mist,
    surfaceContainerHigh = Color(0xFFECEEF1),
    surfaceContainerHighest = Color(0xFFE6E8EC),
)

internal val DarkColors = darkColorScheme(
    primary = Lime,
    onPrimary = Ink,
    primaryContainer = Lime,
    onPrimaryContainer = Ink,
    inversePrimary = Ink,
    secondary = Color(0xFFC9CDD4),
    onSecondary = Ink,
    secondaryContainer = Lime,
    onSecondaryContainer = Ink,
    tertiary = NightMuted,
    onTertiary = Ink,
    tertiaryContainer = Color(0xFF262A31),
    onTertiaryContainer = NightText,
    background = NightBackground,
    onBackground = NightText,
    surface = NightCard,
    onSurface = NightText,
    surfaceVariant = Color(0xFF23272E),
    onSurfaceVariant = NightMuted,
    surfaceTint = NightCard,
    inverseSurface = NightText,
    inverseOnSurface = Ink,
    outline = Color(0xFF4A505A),
    outlineVariant = Color(0xFF2C3037),
    surfaceBright = Color(0xFF2A2E35),
    surfaceDim = NightBackground,
    surfaceContainerLowest = NightBackground,
    surfaceContainerLow = Color(0xFF13161A),
    surfaceContainer = NightCard,
    surfaceContainerHigh = NightRaised,
    surfaceContainerHighest = Color(0xFF262A31),
)

/**
 * The colours Material's scheme has no role for: the one bold "hero" card per screen (the verdict,
 * like a balance card) and the floating navigation bar.
 */
@Immutable
data class BrandColors(
    val hero: Color,
    val onHero: Color,
    val heroAccent: Color,
    val onHeroAccent: Color,
    val navBar: Color,
    val onNavBar: Color,
    val navSelected: Color,
    val onNavSelected: Color,
)

/** Light: a black hero card with a lime button, and a black navigation pill. */
internal val LightBrand = BrandColors(
    hero = Ink,
    onHero = Color.White,
    heroAccent = Lime,
    onHeroAccent = Ink,
    navBar = Ink,
    onNavBar = Color(0xFFB9BEC6),
    navSelected = Lime,
    onNavSelected = Ink,
)

/** Dark: a lime hero card with an ink button, and a raised grey navigation pill. */
internal val DarkBrand = BrandColors(
    hero = Lime,
    onHero = Ink,
    heroAccent = Ink,
    onHeroAccent = Lime,
    navBar = NightRaised,
    onNavBar = NightMuted,
    navSelected = Lime,
    onNavSelected = Ink,
)
