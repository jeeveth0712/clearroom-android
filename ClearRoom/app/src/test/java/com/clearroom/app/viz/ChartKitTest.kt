package com.clearroom.app.viz

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChartKitTest {

    private var savedLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun useEnglishNumbers() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    // --- Axis -----------------------------------------------------------------------------------------

    @Test
    fun `both ends of a linear axis land on the edges`() {
        val axis = Axis(0.0, 2.0)
        assertEquals(100f, axis.toPx(0.0, 100f, 300f))
        assertEquals(300f, axis.toPx(2.0, 100f, 300f))
        assertEquals(200f, axis.toPx(1.0, 100f, 300f))
    }

    @Test
    fun `an axis can run backwards, as screen y does`() {
        assertEquals(50f, Axis(0.0, 1.0).toPx(1.0, startPx = 250f, endPx = 50f))
    }

    @Test
    fun `a log axis spaces decades evenly`() {
        val axis = Axis(1.0, 100.0, log = true)
        assertEquals(0.5, axis.fraction(10.0), 1e-12)
        assertEquals(0.0, axis.fraction(1.0), 1e-12)
        assertEquals(1.0, axis.fraction(100.0), 1e-12)
    }

    @Test
    fun `an axis needs a real range, and a log axis positive values`() {
        assertFailsWith<IllegalArgumentException> { Axis(1.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { Axis(2.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { Axis(0.0, 10.0, log = true) }
    }

    @Test
    fun `nice steps are 1, 2 or 5 times a power of ten`() {
        assertEquals(0.5, Axis.niceStep(1.32, 5), 1e-12)
        assertEquals(20.0, Axis.niceStep(70.0, 4), 1e-12)
        assertEquals(2.0, Axis.niceStep(9.0, 5), 1e-12)
        assertEquals(1.0, Axis.niceStep(5.0, 5), 1e-12)
    }

    @Test
    fun `ticks are round numbers inside the axis`() {
        assertEquals(listOf(-60.0, -40.0, -20.0, 0.0), Axis(-70.0, 0.0).ticks(4))
        assertEquals(listOf(0.0, 0.5, 1.0, 1.5), Axis(0.0, 1.6).ticks(4))
    }

    @Test
    fun `log axes name their own ticks`() {
        assertFailsWith<IllegalStateException> { Axis(1.0, 10.0, log = true).ticks() }
    }

    @Test
    fun `an axis up to a value ends on the next round step`() {
        assertEquals(2.0, Axis.upTo(1.6, maxCount = 4).max, 1e-12)
        assertEquals(1.5, Axis.upTo(1.5, maxCount = 3).max, 1e-12)
        assertEquals(0.0, Axis.upTo(1.6).min)
    }

    // --- Text -------------------------------------------------------------------------------------------

    @Test
    fun `numbers never show as minus zero`() {
        assertEquals("0.0", decimals(-0.0, 1))
        assertEquals("0.0", decimals(-0.04, 1))
        assertEquals("-0.1", decimals(-0.06, 1))
        assertEquals("1.25", decimals(1.25, 2))
    }

    @Test
    fun `numbers follow the phone's decimal style`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("0,6", decimals(0.6, 1))
    }

    @Test
    fun `there is a short and a long name for every band, low to high`() {
        assertEquals(7, BAND_LABELS.size)
        assertEquals("1 kHz", BAND_NAMES[3])
        assertEquals(listOf(0.0, 6.0), listOf(BAND_TICKS.first().value, BAND_TICKS.last().value))
        assertEquals(-0.5, BAND_AXIS.min)
    }
}
