package com.clearroom.app.audio

import com.clearroom.app.testing.Signals
import kotlin.test.Test
import kotlin.test.assertEquals

class ClipQualityTest {

    @Test
    fun `a normal recording is OK`() {
        assertEquals(ClipQuality.OK, ClipQuality.of(Signals.sine(1_000.0, 0.1, seconds = 1.0)))
    }

    @Test
    fun `three full-scale samples in a row is clipping, at either polarity`() {
        assertEquals(ClipQuality.CLIPPED, ClipQuality.of(Signals.clipped(run = 3)))
        val negative = Signals.clipped(run = 3).map { if (it == 1f) -1f else it }.toFloatArray()
        assertEquals(ClipQuality.CLIPPED, ClipQuality.of(negative))
    }

    @Test
    fun `two full-scale samples are a loud peak, not clipping`() {
        assertEquals(ClipQuality.OK, ClipQuality.of(Signals.clipped(run = 2)))
    }

    @Test
    fun `a quieter sample breaks the run`() {
        val samples = Signals.sine(1_000.0, 0.1, seconds = 0.1) + floatArrayOf(1f, 1f, 0.5f, 1f, 1f)
        assertEquals(ClipQuality.OK, ClipQuality.of(samples))
    }

    @Test
    fun `the clipping threshold is inclusive`() {
        val atThreshold = Signals.sine(1_000.0, 0.1, seconds = 0.1) + Signals.constant(ClipQuality.CLIP_LEVEL, 3)
        assertEquals(ClipQuality.CLIPPED, ClipQuality.of(atThreshold))
    }

    @Test
    fun `digital silence and empty clips are silent`() {
        assertEquals(ClipQuality.SILENT, ClipQuality.of(FloatArray(48_000)))
        assertEquals(ClipQuality.SILENT, ClipQuality.of(FloatArray(0)))
    }

    @Test
    fun `the silence threshold sits at -90 dBFS`() {
        // A sine's RMS is amplitude / sqrt(2): 4.5e-5 is about -90 dB, so these sit either side.
        assertEquals(ClipQuality.SILENT, ClipQuality.of(Signals.sine(1_000.0, 2e-5, seconds = 0.5)))
        assertEquals(ClipQuality.OK, ClipQuality.of(Signals.sine(1_000.0, 1e-4, seconds = 0.5)))
    }

    @Test
    fun `clipping is reported even when the rest of the clip is nearly silent`() {
        val samples = FloatArray(48_000) + Signals.constant(1f, 3)
        assertEquals(ClipQuality.CLIPPED, ClipQuality.of(samples))
    }
}
