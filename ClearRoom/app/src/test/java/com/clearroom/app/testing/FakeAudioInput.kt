package com.clearroom.app.testing

import com.clearroom.app.audio.AudioClip
import com.clearroom.app.audio.AudioInput
import com.clearroom.app.audio.AudioSourceKind
import com.clearroom.app.audio.Capture
import com.clearroom.app.audio.CaptureException
import com.clearroom.app.audio.CaptureInfo
import com.clearroom.app.audio.CaptureProblem
import com.clearroom.app.audio.SampleFormat
import com.clearroom.app.dsp.SILENCE_DB
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToLong

/**
 * In-memory [AudioInput]: "records" for the requested time on the test clock, then returns
 * [samples] or fails with [failWith]. Records what it was asked to do.
 */
class FakeAudioInput(
    var samples: (sampleRate: Int, seconds: Double) -> FloatArray =
        { rate, seconds -> Signals.sine(1_000.0, 0.1, seconds, rate) },
    var failWith: CaptureProblem? = null,
) : AudioInput {

    val level = MutableStateFlow(SILENCE_DB)
    override val liveLevelDb: StateFlow<Double> = level

    val requestedSeconds = mutableListOf<Double>()
    var cancelledRecordings = 0
        private set

    override suspend fun record(seconds: Double): Capture {
        requestedSeconds += seconds
        try {
            delay((seconds * 1_000).roundToLong())
        } catch (e: CancellationException) {
            cancelledRecordings++
            throw e
        }
        failWith?.let { throw CaptureException(it, "fake failure: $it") }
        val clip = AudioClip(samples(SAMPLE_RATE, seconds), SAMPLE_RATE)
        return Capture(clip, CaptureInfo(AudioSourceKind.UNPROCESSED, SAMPLE_RATE, SampleFormat.FLOAT_32, emptyList()))
    }

    companion object {
        const val SAMPLE_RATE = 48_000
    }
}
