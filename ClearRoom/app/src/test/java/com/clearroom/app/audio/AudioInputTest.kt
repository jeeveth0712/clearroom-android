package com.clearroom.app.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AudioInputTest {

    @Test
    fun `clip duration is sample count over sample rate`() {
        assertEquals(1.0, AudioClip(FloatArray(48_000), 48_000).durationS, absoluteTolerance = 1e-12)
        assertEquals(0.5, AudioClip(FloatArray(22_050), 44_100).durationS, absoluteTolerance = 1e-12)
        assertEquals(0.0, AudioClip(FloatArray(0), 48_000).durationS)
    }

    @Test
    fun `a clip without a positive sample rate is rejected`() {
        assertFailsWith<IllegalArgumentException> { AudioClip(FloatArray(10), 0) }
        assertFailsWith<IllegalArgumentException> { AudioClip(FloatArray(10), -48_000) }
    }

    @Test
    fun `unprocessed source is used only when Android reports support for it`() {
        assertEquals(AudioSourceKind.UNPROCESSED, AudioSourceKind.fromUnprocessedProperty("true"))
        assertEquals(AudioSourceKind.UNPROCESSED, AudioSourceKind.fromUnprocessedProperty("TRUE"))
        assertEquals(AudioSourceKind.VOICE_RECOGNITION, AudioSourceKind.fromUnprocessedProperty(null))
        assertEquals(AudioSourceKind.VOICE_RECOGNITION, AudioSourceKind.fromUnprocessedProperty("false"))
        assertEquals(AudioSourceKind.VOICE_RECOGNITION, AudioSourceKind.fromUnprocessedProperty(""))
    }

    @Test
    fun `capture exceptions carry their problem for the screen to explain`() {
        CaptureProblem.entries.forEach { problem ->
            assertEquals(problem, CaptureException(problem, "detail").problem)
        }
    }
}
