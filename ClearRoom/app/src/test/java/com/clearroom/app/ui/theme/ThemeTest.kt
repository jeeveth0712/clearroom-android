package com.clearroom.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.clearroom.app.acoustics.StiBand
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The M12 look: readable in light and dark, and never confusable with a clarity band. */
class ThemeTest {

    /** WCAG contrast ratio between two opaque colours (1 to 21). */
    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = max(a.luminance(), b.luminance()) to min(a.luminance(), b.luminance())
        return (light + 0.05) / (dark + 0.05)
    }

    /** Hue in degrees, and how coloured it looks 0–1: HSV saturation, but 0 for greys and near-blacks. */
    private fun hueAndSaturation(colour: Color): Pair<Double, Double> {
        val (r, g, b) = Triple(colour.red.toDouble(), colour.green.toDouble(), colour.blue.toDouble())
        val high = maxOf(r, g, b)
        val spread = high - minOf(r, g, b)
        // A tint in a near-black (the ink is #111318) isn't seen as a hue.
        if (spread == 0.0 || high < DARK_BELOW) return 0.0 to 0.0
        val hue = when (high) {
            r -> 60 * (((g - b) / spread).mod(6.0))
            g -> 60 * ((b - r) / spread + 2)
            else -> 60 * ((r - g) / spread + 4)
        }
        return hue to spread / high
    }

    private fun textPairs(scheme: ColorScheme) = mapOf(
        "text on cards" to (scheme.onSurface to scheme.surface),
        "text on the background" to (scheme.onBackground to scheme.background),
        "muted text on cards" to (scheme.onSurfaceVariant to scheme.surface),
        "muted text on the background" to (scheme.onSurfaceVariant to scheme.background),
        "buttons" to (scheme.onPrimary to scheme.primary),
        "the accent banner" to (scheme.onPrimaryContainer to scheme.primaryContainer),
        "selected segments" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
    )

    private fun brandPairs(brand: BrandColors) = mapOf(
        "hero card" to (brand.onHero to brand.hero),
        "hero button" to (brand.onHeroAccent to brand.heroAccent),
        "selected tab" to (brand.onNavSelected to brand.navSelected),
        "other tabs" to (brand.onNavBar to brand.navBar),
    )

    @Test
    fun `all text is readable on what it sits on, in the light and the dark theme (WCAG AA)`() {
        listOf(textPairs(LightColors) + brandPairs(LightBrand), textPairs(DarkColors) + brandPairs(DarkBrand)).forEach { pairs ->
            pairs.forEach { (what, pair) ->
                val ratio = contrast(pair.first, pair.second)
                assertTrue(ratio >= 4.5, "$what: contrast %.1f is under 4.5".format(ratio))
            }
        }
    }

    @Test
    fun `no coloured part of the app's chrome looks like a clarity band colour`() {
        val bandHues = StiBand.entries.map { hueAndSaturation(it.color()).first }
        val chrome = listOf(LightColors, DarkColors).flatMap { listOf(it.primary, it.primaryContainer, it.secondaryContainer, it.tertiary) } +
            listOf(LightBrand, DarkBrand).flatMap { listOf(it.hero, it.heroAccent, it.navBar, it.navSelected) }
        chrome.forEach { colour ->
            val (hue, saturation) = hueAndSaturation(colour)
            if (saturation > GREY_BELOW) {
                bandHues.forEach { bandHue ->
                    val apart = abs(hue - bandHue).let { min(it, 360 - it) }
                    assertTrue(apart >= MIN_HUE_APART, "$colour is only %.0f° from a band hue".format(apart))
                }
            }
        }
    }

    @Test
    fun `the accent is lime and the ink is near-black, as the owner chose`() {
        assertEquals(Color(0xFFD4F25C), Lime)
        assertTrue(Ink.luminance() < 0.01f)
        val (hue, _) = hueAndSaturation(Lime)
        assertTrue(hue in 60.0..90.0, "lime hue $hue")
    }

    @Test
    fun `the light theme is light and the dark theme is dark`() {
        assertTrue(LightColors.background.luminance() > 0.8f)
        assertTrue(DarkColors.background.luminance() < 0.02f)
    }

    private companion object {
        /** Below this saturation a colour reads as grey. */
        const val GREY_BELOW = 0.25

        /** Below this brightness (HSV value) a colour reads as black. */
        const val DARK_BELOW = 0.2

        const val MIN_HUE_APART = 40.0
    }
}
