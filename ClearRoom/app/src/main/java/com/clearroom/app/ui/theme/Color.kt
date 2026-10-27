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
/** The light hero card: lime washed out, so the page stays calm and ink text reads on it. */
private val PaleLime = Color(0xFFE8F5BF)
private val NightBackground = Color(0xFF111316)
private val NightCard = Color(0xFF1B1E23)
private val NightRaised = Color(0xFF24282E)
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
 * like a balance card), the big numbers on ordinary cards, the strong full-width button, and the
 * floating navigation bar.
 */
@Immutable
data class BrandColors(
    val hero: Color,
    val onHero: Color,
    val heroAccent: Color,
    val onHeroAccent: Color,
    /** Big headline numbers on an ordinary card: lime at night, ink by day (lime on white can't be read). */
    val headline: Color,
    val strongButton: Color,
    val onStrongButton: Color,
    /** Null when the button is filled enough to need no edge. */
    val strongButtonBorder: Color?,
    val navBar: Color,
    val navBorder: Color?,
    val onNavBar: Color,
    /** The open tab's icon and name. */
    val navSelected: Color,
)

/** Light: a pale lime hero card with ink text, ink buttons, and a black navigation pill. */
internal val LightBrand = BrandColors(
    hero = PaleLime,
    onHero = Ink,
    heroAccent = Ink,
    onHeroAccent = Lime,
    headline = Ink,
    strongButton = Ink,
    onStrongButton = Color.White,
    strongButtonBorder = null,
    navBar = Ink,
    navBorder = null,
    onNavBar = Color(0xFFB9BEC6),
    navSelected = Lime,
)

/** Dark: a lime hero card, lime headline numbers, outlined buttons, and a dark navigation pill. */
internal val DarkBrand = BrandColors(
    hero = Lime,
    onHero = Ink,
    heroAccent = Ink,
    onHeroAccent = Lime,
    headline = Lime,
    strongButton = NightCard,
    onStrongButton = NightText,
    strongButtonBorder = Color(0xFF3A3F47),
    navBar = NightCard,
    navBorder = Color(0xFF2C3037),
    onNavBar = NightMuted,
    navSelected = Lime,
)
