package com.clearroom.app.audio

import com.clearroom.app.testing.Signals
import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordingSummaryTest {

    private fun capture(samples: FloatArray, rate: Int = 48_000, source: AudioSourceKind = AudioSourceKind.UNPROCESSED) =
        Capture(AudioClip(samples, rate), CaptureInfo(source, rate, SampleFormat.FLOAT_32, emptyList()))

    @Test
    fun `summary reports duration, rate, source and levels of the clip`() {
        val summary = RecordingSummary.of(capture(Signals.constant(0.5f, 96_000)))
        assertEquals(2.0, summary.durationS, absoluteTolerance = 1e-12)
        assertEquals(48_000, summary.sampleRate)
        assertEquals(AudioSourceKind.UNPROCESSED, summary.source)
        assertEquals(20 * log10(0.5), summary.peakDbFs, absoluteTolerance = 1e-6)
        assertEquals(20 * log10(0.5), summary.averageDbFs, absoluteTolerance = 1e-6)
        assertEquals(ClipQuality.OK, summary.quality)
    }

    @Test
    fun `summary uses the real capture rate and source, not assumed ones`() {
        val summary = RecordingSummary.of(
            capture(FloatArray(44_100) { 0.01f }, rate = 44_100, source = AudioSourceKind.VOICE_RECOGNITION),
        )
        assertEquals(44_100, summary.sampleRate)
        assertEquals(1.0, summary.durationS, absoluteTolerance = 1e-12)
        assertEquals(AudioSourceKind.VOICE_RECOGNITION, summary.source)
    }

    @Test
    fun `summary carries the clip quality verdict`() {
        assertEquals(ClipQuality.CLIPPED, RecordingSummary.of(capture(Signals.clipped(run = 5))).quality)
        assertEquals(ClipQuality.SILENT, RecordingSummary.of(capture(FloatArray(4_800))).quality)
    }
}
