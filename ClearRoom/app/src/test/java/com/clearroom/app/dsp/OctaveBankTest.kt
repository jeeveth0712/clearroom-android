package com.clearroom.app.dsp

import com.clearroom.app.testing.Signals
import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OctaveBankTest {

    private fun loudestBand(levels: DoubleArray) = levels.indices.maxBy { levels[it] }

    @Test
    fun `a 1 kHz tone lands in the 1 kHz band at its true level, well above the neighbours`() {
        val tone = Signals.sine(1_000.0, amplitude = 0.1, seconds = 1.0)
        val levels = OctaveBank.bandLevelsDb(tone, 48_000)
        val toneRmsDb = 20 * log10(0.1 / kotlin.math.sqrt(2.0)) // -23.0 dBFS
        assertEquals(toneRmsDb, levels[3], absoluteTolerance = 0.5)
        assertTrue(levels[3] - levels[2] >= 10.0, "500 Hz band too high: ${levels.toList()}")
        assertTrue(levels[3] - levels[4] >= 10.0, "2 kHz band too high: ${levels.toList()}")
    }

    @Test
    fun `each band's centre tone lands in that band, at 48 and 44,1 kHz`() {
        listOf(48_000, 44_100).forEach { rate ->
            OctaveBands.CENTRES_HZ.forEachIndexed { band, hz ->
                val levels = OctaveBank.bandLevelsDb(Signals.sine(hz.toDouble(), 0.1, seconds = 0.5, sampleRate = rate), rate)
                assertEquals(band, loudestBand(levels), "tone $hz Hz at $rate Hz: ${levels.toList()}")
            }
        }
    }

    @Test
    fun `doubling the amplitude raises every band by about 6 dB`() {
        val quiet = OctaveBands.CENTRES_HZ.map { Signals.sine(it.toDouble(), 0.02, seconds = 0.5) }
            .reduce { acc, tone -> FloatArray(acc.size) { acc[it] + tone[it] } }
        val loud = FloatArray(quiet.size) { quiet[it] * 2 }
        val before = OctaveBank.bandLevelsDb(quiet, 48_000)
        val after = OctaveBank.bandLevelsDb(loud, 48_000)
        before.indices.forEach { band -> assertEquals(20 * log10(2.0), after[band] - before[band], absoluteTolerance = 0.01) }
    }

    @Test
    fun `a reading doesn't depend on how long the recording was`() {
        val short = OctaveBank.bandLevelsDb(Signals.sine(500.0, 0.1, seconds = 1.0), 48_000)
        val long = OctaveBank.bandLevelsDb(Signals.sine(500.0, 0.1, seconds = 3.0), 48_000)
        assertEquals(short[2], long[2], absoluteTolerance = 0.1)
    }

    @Test
    fun `silence and clips shorter than the settle time read as silence`() {
        OctaveBank.bandLevelsDb(FloatArray(48_000), 48_000).forEach { assertEquals(SILENCE_DB, it) }
        OctaveBank.bandLevelsDb(Signals.sine(1_000.0, 0.5, seconds = 0.01), 48_000).forEach { assertEquals(SILENCE_DB, it) }
    }

    @Test
    fun `an impossible sample rate is rejected`() {
        assertFailsWith<IllegalArgumentException> { OctaveBank.bandLevelsDb(FloatArray(10), 0) }
    }

    @Test
    fun `A-weighting a flat 0 dB spectrum gives 6,98 dB (hand-worked from IEC weights)`() {
        // 10·log10(10^-1.61 + 10^-0.86 + 10^-0.32 + 1 + 10^0.12 + 10^0.10 + 10^-0.11) = 10·log10(4.9946)
        assertEquals(6.985, OctaveBank.aWeightedDb(List(7) { 0.0 }), absoluteTolerance = 0.005)
    }

    @Test
    fun `A-weighting leaves 1 kHz alone and cuts 125 Hz by 16,1 dB`() {
        val only1k = List(7) { if (it == 3) -40.0 else SILENCE_DB }
        val only125 = List(7) { if (it == 0) -40.0 else SILENCE_DB }
        assertEquals(-40.0, OctaveBank.aWeightedDb(only1k), absoluteTolerance = 0.001)
        assertEquals(-56.1, OctaveBank.aWeightedDb(only125), absoluteTolerance = 0.001)
    }

    @Test
    fun `A-weighting needs exactly seven bands`() {
        assertFailsWith<IllegalArgumentException> { OctaveBank.aWeightedDb(List(6) { 0.0 }) }
    }
}
