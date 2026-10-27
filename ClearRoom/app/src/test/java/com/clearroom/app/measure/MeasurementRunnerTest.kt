package com.clearroom.app.measure

import com.clearroom.app.audio.AudioClip
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
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementRunnerTest {

    private val audio = FakeAudioInput()
    private fun TestScope.runner() = MeasurementRunner(audio, StandardTestDispatcher(testScheduler))

    private suspend fun <T> TestScope.run(
        kind: MeasureKind,
        countdownS: Int = MeasurementRunner.COUNTDOWN_S,
        events: MutableList<String> = mutableListOf(),
        analyse: (AudioClip) -> T,
    ) = runner().run(kind, countdownS, onCountdown = { events += "countdown $it" }, onRecording = { events += "recording" }, analyse = analyse)

    @Test
    fun `counts down a second apart before recording`() = runTest {
        val events = mutableListOf<String>()
        val job = launch { run(MeasureKind.NOISE, events = events, analyse = ::bandLevelsOf) }
        runCurrent()
        assertEquals(listOf("countdown 3"), events)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf("countdown 3", "countdown 2"), events)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(listOf("countdown 3", "countdown 2", "countdown 1", "recording"), events)
        job.cancel()
    }

    @Test
    fun `a longer countdown can be asked for, and zero starts straight away`() = runTest {
        val events = mutableListOf<String>()
        run(MeasureKind.CLAP, countdownS = 10, events = events) { it.durationS }
        assertEquals((10 downTo 1).map { "countdown $it" } + "recording", events)
        val none = mutableListOf<String>()
        run(MeasureKind.CLAP, countdownS = 0, events = none) { it.durationS }
        assertEquals(listOf("recording"), none)
        assertFailsWith<IllegalArgumentException> { run(MeasureKind.CLAP, countdownS = -1) { it.durationS } }
    }

    @Test
    fun `each kind records for its own length - noise 30 s, voice 20 s, clap 5 s, voice at a distance 20 s`() = runTest {
        MeasureKind.entries.forEach { run(it) { clip -> clip.durationS } }
        assertEquals(listOf(30.0, 20.0, 5.0, 20.0), audio.requestedSeconds)
    }

    private fun <T> MeasureOutcome<T>.measured(): T =
        (this as? MeasureOutcome.Measured<T>)?.result ?: error("expected a measurement, got $this")

    @Test
    fun `a clean recording is handed to the analysis and its result returned`() = runTest {
        assertEquals(5 * FakeAudioInput.SAMPLE_RATE, run(MeasureKind.CLAP) { clip -> clip.samples.size }.measured())
    }

    @Test
    fun `band levels of a 2 kHz tone are loudest in the 2 kHz band`() = runTest {
        audio.samples = { rate, seconds -> Signals.sine(2_000.0, 0.05, seconds, rate) }
        val levels = run(MeasureKind.VOICE, analyse = ::bandLevelsOf).measured().valuesDb
        assertEquals(4, levels.indices.maxBy { levels[it] }, "$levels")
    }

    @Test
    fun `clipped or silent recordings are rejected before any analysis runs`() = runTest {
        var analysed = 0
        audio.samples = { rate, seconds -> Signals.clipped(run = 5, seconds = seconds, sampleRate = rate) }
        assertEquals(MeasureOutcome.Rejected(ClipQuality.CLIPPED), run(MeasureKind.NOISE) { analysed++ })
        audio.samples = { rate, seconds -> FloatArray((rate * seconds).toInt()) }
        assertEquals(MeasureOutcome.Rejected(ClipQuality.SILENT), run(MeasureKind.NOISE) { analysed++ })
        assertEquals(0, analysed)
    }

    @Test
    fun `microphone problems are reported, not thrown`() = runTest {
        CaptureProblem.entries.forEach { problem ->
            audio.failWith = problem
            assertEquals(MeasureOutcome.Failed(problem), run(MeasureKind.VOICE) { it.durationS })
        }
    }

    @Test
    fun `cancelling during the countdown never opens the microphone`() = runTest {
        val job = launch { run(MeasureKind.NOISE) { it.durationS } }
        advanceTimeBy(1_500)
        job.cancel()
        advanceUntilIdle()
        assertEquals(emptyList(), audio.requestedSeconds)
    }
}
