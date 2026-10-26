package com.clearroom.app.dsp

import com.clearroom.app.testing.Signals
import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LevelsTest {

    @Test
    fun `constant full-scale signal is 0 dBFS`() {
        assertEquals(0.0, rmsDbFs(Signals.constant(1f, 1_000)), absoluteTolerance = 1e-9)
    }

    @Test
    fun `halving the amplitude lowers the level by about 6 dB`() {
        assertEquals(20 * log10(0.5), rmsDbFs(Signals.constant(0.5f, 1_000)), absoluteTolerance = 1e-6)
    }

    @Test
    fun `full-scale sine reads about -3 dBFS`() {
        val sine = Signals.sine(freqHz = 1_000.0, amplitude = 1.0, seconds = 1.0)
        assertEquals(-3.0103, rmsDbFs(sine), absoluteTolerance = 0.01)
    }

    @Test
    fun `silence and empty ranges give the silence floor, never -Infinity`() {
        assertEquals(SILENCE_DB, rmsDbFs(FloatArray(1_000)))
        assertEquals(SILENCE_DB, rmsDbFs(FloatArray(0)))
        assertEquals(SILENCE_DB, rmsDbFs(Signals.constant(1f, 10), from = 5, count = 0))
    }

    @Test
    fun `extremely quiet signals are clamped to the silence floor`() {
        assertEquals(SILENCE_DB, rmsDbFs(Signals.constant(1e-9f, 100)))
    }

    @Test
    fun `a sub-range ignores samples outside it`() {
        val loudThenSilent = Signals.constant(1f, 500) + FloatArray(500)
        assertEquals(0.0, rmsDbFs(loudThenSilent, from = 0, count = 500), absoluteTolerance = 1e-9)
        assertEquals(SILENCE_DB, rmsDbFs(loudThenSilent, from = 500, count = 500))
    }

    @Test
    fun `ranges outside the array are rejected`() {
        val samples = FloatArray(10)
        assertFailsWith<IllegalArgumentException> { rmsDbFs(samples, from = -1, count = 2) }
        assertFailsWith<IllegalArgumentException> { rmsDbFs(samples, from = 8, count = 5) }
        assertFailsWith<IllegalArgumentException> { rmsDbFs(samples, from = 0, count = -1) }
    }

    @Test
    fun `peak finds the largest magnitude whether positive or negative`() {
        assertEquals(20 * log10(0.5), peakDbFs(floatArrayOf(0.1f, -0.5f, 0.25f)), absoluteTolerance = 1e-6)
        assertEquals(0.0, peakDbFs(floatArrayOf(0f, 1f)), absoluteTolerance = 1e-9)
    }

    @Test
    fun `energy converts to dB, with silence for zero and NaN left visible`() {
        assertEquals(0.0, meanSquareDb(1.0), absoluteTolerance = 1e-12)
        assertEquals(-20.0, meanSquareDb(0.01), absoluteTolerance = 1e-9)
        assertEquals(SILENCE_DB, meanSquareDb(0.0))
        assertEquals(SILENCE_DB, meanSquareDb(-1.0))
        assertEquals(true, meanSquareDb(Double.NaN).isNaN(), "NaN must not be hidden as silence")
    }

    @Test
    fun `peak of silence is the silence floor`() {
        assertEquals(SILENCE_DB, peakDbFs(FloatArray(100)))
        assertEquals(SILENCE_DB, peakDbFs(FloatArray(0)))
    }
}
