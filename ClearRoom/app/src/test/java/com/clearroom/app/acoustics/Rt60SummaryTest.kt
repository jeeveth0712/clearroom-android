package com.clearroom.app.acoustics

import com.clearroom.app.data.RtTake
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Rt60SummaryTest {

    private fun take(vararg t60: Double?) = RtTake(t60.toList())

    @Test
    fun `mean and sample spread per band across takes`() {
        val summary = Rt60Summary.of(
            listOf(
                take(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0),
                take(1.2, 1.2, 1.2, 1.2, 1.2, 1.2, 1.2),
                take(1.4, 1.4, 1.4, 1.4, 1.4, 1.4, 1.4),
            ),
        )!!
        summary.bands.forEach { band ->
            assertEquals(1.2, band.meanS, absoluteTolerance = 1e-12)
            assertEquals(0.2, band.spreadS, absoluteTolerance = 1e-12) // sample sd of 1.0, 1.2, 1.4
            assertEquals(3, band.takes)
            assertFalse(band.estimated)
            assertEquals(0.2 / 1.2, band.variation, absoluteTolerance = 1e-12)
        }
    }

    @Test
    fun `one take has a mean but no spread yet`() {
        val band = Rt60Summary.of(listOf(take(0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9)))!!.bands[3]
        assertEquals(0.9, band.meanS)
        assertEquals(0.0, band.spreadS)
        assertEquals(1, band.takes)
    }

    @Test
    fun `bands rejected in some takes average only the takes that worked`() {
        val band = Rt60Summary.of(listOf(take(null, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0), take(0.8, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)))!!.bands[0]
        assertEquals(0.8, band.meanS)
        assertEquals(1, band.takes)
    }

    @Test
    fun `bands no take could measure borrow the nearest measured band and say so`() {
        val summary = Rt60Summary.of(listOf(take(null, 1.3, 1.2, 1.1, 1.0, 0.9, null)))!!
        assertEquals(1.3, summary.bands[0].meanS) // 125 Hz from 250 Hz
        assertEquals(0.9, summary.bands[6].meanS) // 8 kHz from 4 kHz
        assertTrue(summary.bands[0].estimated && summary.bands[6].estimated)
        assertEquals(0, summary.bands[0].takes)
        assertFalse(summary.bands[3].estimated)
    }

    @Test
    fun `a gap between two measured bands borrows from the higher one when both are equally near`() {
        val summary = Rt60Summary.of(listOf(take(1.4, 1.3, 1.2, null, 1.0, 0.9, 0.8)))!!
        assertEquals(1.0, summary.bands[3].meanS)
        assertTrue(summary.bands[3].estimated)
    }

    @Test
    fun `the speech echo time is the mean over 500 Hz to 2 kHz`() {
        val summary = Rt60Summary.of(listOf(take(2.0, 1.5, 1.2, 1.0, 0.8, 0.6, 0.5)))!!
        assertEquals((1.2 + 1.0 + 0.8) / 3, summary.speechRt60S, absoluteTolerance = 1e-12)
    }

    @Test
    fun `with no usable takes there is no summary`() {
        assertNull(Rt60Summary.of(emptyList()))
        assertNull(Rt60Summary.of(listOf(take(null, null, null, null, null, null, null))))
    }

    @Test
    fun `two takes give the textbook sample spread`() {
        val band = Rt60Summary.of(listOf(take(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0), take(2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0)))!!.bands[2]
        assertEquals(sqrt(0.5), band.spreadS, absoluteTolerance = 1e-12)
    }

    @Test
    fun `the classroom target is ANSI S12,60's 0,6 s`() {
        assertEquals(0.6, Rt60Summary.CLASSROOM_TARGET_S)
    }
}
