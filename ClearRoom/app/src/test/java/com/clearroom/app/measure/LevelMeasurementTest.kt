package com.clearroom.app.measure

import com.clearroom.app.audio.CaptureProblem
import com.clearroom.app.audio.ClipQuality
import com.clearroom.app.testing.FakeAudioInput
import com.clearroom.app.testing.Signals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class LevelMeasurementTest {

    private val audio = FakeAudioInput()
    private fun TestScope.measurement() = LevelMeasurement(audio, StandardTestDispatcher(testScheduler))

    private suspend fun TestScope.run(kind: MeasureKind, events: MutableList<String> = mutableListOf()) =
        measurement().run(kind, onCountdown = { events += "countdown $it" }, onRecording = { events += "recording" })

    @Test
    fun `counts down 3, 2, 1 a second apart before recording`() = runTest {
        val events = mutableListOf<String>()
        val job = launch { run(MeasureKind.NOISE, events) }
        runCurrent()
        assertEquals(listOf("countdown 3"), events)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf("countdown 3", "countdown 2"), events)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(listOf("countdown 3", "countdown 2", "countdown 1", "recording"), events)
        job.cancel()
    }

    @Test
    fun `noise listens for 30 s and the voice reference for 20 s`() = runTest {
        run(MeasureKind.NOISE)
        run(MeasureKind.VOICE)
        assertEquals(listOf(30.0, 20.0), audio.requestedSeconds)
    }

    @Test
    fun `a clean recording gives seven band levels, loudest where the sound is`() = runTest {
        audio.samples = { rate, seconds -> Signals.sine(2_000.0, 0.05, seconds, rate) }
        val measured = assertIs<MeasureOutcome.Measured>(run(MeasureKind.VOICE))
        val levels = measured.levels.valuesDb
        assertEquals(7, levels.size)
        assertEquals(4, levels.indices.maxBy { levels[it] }, "2 kHz should be loudest: $levels")
        assertEquals(ClipQuality.OK, measured.summary.quality)
    }

    @Test
    fun `clipped or silent recordings are rejected and give no levels`() = runTest {
        audio.samples = { rate, seconds -> Signals.clipped(run = 5, seconds = seconds, sampleRate = rate) }
        assertEquals(MeasureOutcome.Rejected(ClipQuality.CLIPPED), run(MeasureKind.NOISE))
        audio.samples = { rate, seconds -> FloatArray((rate * seconds).toInt()) }
        assertEquals(MeasureOutcome.Rejected(ClipQuality.SILENT), run(MeasureKind.NOISE))
    }

    @Test
    fun `microphone problems are reported, not thrown`() = runTest {
        CaptureProblem.entries.forEach { problem ->
            audio.failWith = problem
            assertEquals(MeasureOutcome.Failed(problem), run(MeasureKind.VOICE))
        }
    }

    @Test
    fun `cancelling during the countdown never opens the microphone`() = runTest {
        val job = launch { run(MeasureKind.NOISE) }
        advanceTimeBy(1_500)
        job.cancel()
        advanceUntilIdle()
        assertEquals(emptyList(), audio.requestedSeconds)
    }
}
