package com.clearroom.app.dsp

import com.clearroom.app.testing.Signals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StreamingOctaveBankTest {

    private val rate = 48_000

    @Test
    fun `a 1 kHz tone lands in the 1 kHz band, well above its neighbours`() {
        val levels = StreamingOctaveBank(rate).bandLevelsDb(Signals.sine(1_000.0, 0.1, 1.0, rate))
        assertTrue(levels[3] > levels[2] + 10 && levels[3] > levels[4] + 10, levels.toList().toString())
    }

    @Test
    fun `a steady tone gives the same level every second, with no restart ripple between blocks`() {
        val bank = StreamingOctaveBank(rate)
        val tone = Signals.sine(125.0, 0.1, 5.0, rate)
        val perSecond = (0 until 5).map { s -> bank.bandLevelsDb(tone.copyOfRange(s * rate, (s + 1) * rate))[0] }
        val later = perSecond.drop(1)
        assertTrue(later.max() - later.min() < 0.1, "125 Hz per second: $perSecond")
    }

    @Test
    fun `streaming in blocks matches filtering the whole clip at once`() {
        val clip = Signals.sine(500.0, 0.1, 2.0, rate)
        val whole = OctaveBank.bandLevelsDb(clip, rate)
        val bank = StreamingOctaveBank(rate)
        bank.bandLevelsDb(clip.copyOfRange(0, rate))
        val second = bank.bandLevelsDb(clip.copyOfRange(rate, 2 * rate))
        assertEquals(whole[2], second[2], 0.2)
    }

    @Test
    fun `only the first block skips the filters' settling time`() {
        val bank = StreamingOctaveBank(rate)
        val settle = (DspConstants.FILTER_SETTLE_S * rate).toInt()
        assertEquals(SILENCE_DB, bank.bandLevelsDb(FloatArray(settle / 2) { 0.1f })[3]) // still settling: nothing counted
        val next = bank.bandLevelsDb(Signals.sine(1_000.0, 0.1, 0.5, rate))
        assertTrue(next[3] > SILENCE_DB)
    }

    @Test
    fun `the sample rate must be positive`() {
        assertFailsWith<IllegalArgumentException> { StreamingOctaveBank(0) }
    }
}
