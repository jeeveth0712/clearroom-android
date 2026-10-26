package com.clearroom.app.dsp

import com.clearroom.app.testing.Signals
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BiquadTest {

    private val rate = 48_000
    private val q = DspConstants.BAND_FILTER_Q

    /** Steady-state gain in dB for a sine at [freqHz], measured after the filter has settled. */
    private fun gainDb(filter: Biquad, freqHz: Double): Double {
        val input = Signals.sine(freqHz, amplitude = 0.5, seconds = 1.0, sampleRate = rate)
        val output = DoubleArray(input.size) { filter.process(input[it].toDouble()) }
        val half = input.size / 2
        fun rms(x: (Int) -> Double) = sqrt((half until input.size).sumOf { x(it) * x(it) } / (input.size - half))
        return 20 * log10(rms { output[it] } / rms { input[it].toDouble() })
    }

    /** RBJ band-pass magnitude: 1 / sqrt(1 + Q² (f/f0 − f0/f)²). */
    private fun expectedGainDb(f: Double, f0: Double) =
        -10 * log10(1 + q * q * (f / f0 - f0 / f) * (f / f0 - f0 / f))

    @Test
    fun `a tone at the centre frequency passes at 0 dB`() {
        assertEquals(0.0, gainDb(Biquad.bandPass(1_000.0, q, rate), 1_000.0), absoluteTolerance = 0.05)
    }

    @Test
    fun `one octave either side is cut as the RBJ formula predicts, about 7 dB`() {
        listOf(500.0, 2_000.0).forEach { f ->
            val measured = gainDb(Biquad.bandPass(1_000.0, q, rate), f)
            assertEquals(expectedGainDb(f, 1_000.0), measured, absoluteTolerance = 0.1, "f=$f")
        }
        assertEquals(-7.4, expectedGainDb(2_000.0, 1_000.0), absoluteTolerance = 0.05)
    }

    @Test
    fun `steady DC is blocked`() {
        val filter = Biquad.bandPass(125.0, q, rate)
        var last = 1.0
        repeat(rate) { last = filter.process(1.0) }
        assertTrue(abs(last) < 1e-6, "DC leaked: $last")
    }

    @Test
    fun `reset clears the memory so the same input gives the same output`() {
        val filter = Biquad.bandPass(500.0, q, rate)
        val impulse = DoubleArray(200).also { it[0] = 1.0 }
        val first = DoubleArray(impulse.size) { filter.process(impulse[it]) }
        filter.reset()
        val second = DoubleArray(impulse.size) { filter.process(impulse[it]) }
        assertContentEquals(first, second)
    }

    @Test
    fun `without a reset the previous signal's tail leaks into the next`() {
        val filter = Biquad.bandPass(500.0, q, rate)
        repeat(100) { filter.process(1.0) }
        assertTrue(abs(filter.process(0.0)) > 0.0, "a stateful filter should still be ringing")
    }

    @Test
    fun `impossible filters are rejected`() {
        assertFailsWith<IllegalArgumentException> { Biquad.bandPass(24_000.0, q, rate) } // at Nyquist
        assertFailsWith<IllegalArgumentException> { Biquad.bandPass(0.0, q, rate) }
        assertFailsWith<IllegalArgumentException> { Biquad.bandPass(1_000.0, 0.0, rate) }
        assertFailsWith<IllegalArgumentException> { Biquad.bandPass(1_000.0, q, 0) }
    }
}
