package com.clearroom.app

import com.clearroom.app.audio.CaptureProblem
import com.clearroom.app.audio.ClipQuality
import com.clearroom.app.testing.FakeAudioInput
import com.clearroom.app.testing.MainDispatcherRule
import com.clearroom.app.testing.Signals
import com.clearroom.app.ui.MeasurePhase
import com.clearroom.app.ui.UiEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val audio = FakeAudioInput()
    private val viewModel = AppViewModel(audio, dsp = main.dispatcher)
    private val phase get() = viewModel.state.value.measure.phase

    private fun TestScope.advanceSeconds(seconds: Int) {
        advanceTimeBy(seconds * 1_000L)
        runCurrent()
    }

    @Test
    fun `starts idle`() {
        assertEquals(MeasurePhase.Idle, phase)
    }

    @Test
    fun `start counts down 3, 2, 1 a second apart, then records for 5 seconds`() = runTest {
        viewModel.onEvent(UiEvent.StartTestRecording)
        runCurrent()
        assertEquals(MeasurePhase.Countdown(3), phase)
        advanceSeconds(1)
        assertEquals(MeasurePhase.Countdown(2), phase)
        advanceSeconds(1)
        assertEquals(MeasurePhase.Countdown(1), phase)
        advanceSeconds(1)
        assertEquals(MeasurePhase.Recording(AppViewModel.TEST_RECORDING_S), phase)
        assertEquals(listOf(AppViewModel.TEST_RECORDING_S), audio.requestedSeconds)
    }

    @Test
    fun `a finished recording shows its summary`() = runTest {
        viewModel.onEvent(UiEvent.StartTestRecording)
        advanceUntilIdle()
        val done = assertIs<MeasurePhase.Done>(phase)
        assertEquals(AppViewModel.TEST_RECORDING_S, done.summary.durationS, absoluteTolerance = 1e-9)
        assertEquals(FakeAudioInput.SAMPLE_RATE, done.summary.sampleRate)
        assertEquals(ClipQuality.OK, done.summary.quality)
    }

    @Test
    fun `a clipped recording is a finished recording with a clipped verdict, not a failure`() = runTest {
        audio.samples = { rate, seconds -> Signals.clipped(run = 10, seconds = seconds, sampleRate = rate) }
        viewModel.onEvent(UiEvent.StartTestRecording)
        advanceUntilIdle()
        assertEquals(ClipQuality.CLIPPED, assertIs<MeasurePhase.Done>(phase).summary.quality)
    }

    @Test
    fun `each capture problem ends in a Failed phase naming that problem`() = runTest {
        CaptureProblem.entries.forEach { problem ->
            audio.failWith = problem
            viewModel.onEvent(UiEvent.StartTestRecording)
            advanceUntilIdle()
            assertEquals(MeasurePhase.Failed(problem), phase, "problem=$problem")
        }
    }

    @Test
    fun `cancel during the countdown returns to idle without opening the microphone`() = runTest {
        viewModel.onEvent(UiEvent.StartTestRecording)
        advanceSeconds(1)
        viewModel.onEvent(UiEvent.CancelRecording)
        advanceUntilIdle()
        assertEquals(MeasurePhase.Idle, phase)
        assertEquals(emptyList(), audio.requestedSeconds)
    }

    @Test
    fun `cancel during recording stops the recording and returns to idle`() = runTest {
        viewModel.onEvent(UiEvent.StartTestRecording)
        advanceSeconds(AppViewModel.COUNTDOWN_S + 1)
        assertIs<MeasurePhase.Recording>(phase)
        viewModel.onEvent(UiEvent.CancelRecording)
        advanceUntilIdle()
        assertEquals(MeasurePhase.Idle, phase)
        assertEquals(1, audio.cancelledRecordings)
    }

    @Test
    fun `pressing start again while busy does not start a second recording`() = runTest {
        viewModel.onEvent(UiEvent.StartTestRecording)
        advanceSeconds(1)
        viewModel.onEvent(UiEvent.StartTestRecording)
        advanceUntilIdle()
        assertEquals(1, audio.requestedSeconds.size)
    }

    @Test
    fun `recording again after a result starts a fresh countdown`() = runTest {
        viewModel.onEvent(UiEvent.StartTestRecording)
        advanceUntilIdle()
        viewModel.onEvent(UiEvent.StartTestRecording)
        runCurrent()
        assertEquals(MeasurePhase.Countdown(AppViewModel.COUNTDOWN_S), phase)
        advanceUntilIdle()
        assertEquals(2, audio.requestedSeconds.size)
    }

    @Test
    fun `the live level comes straight from the audio input`() {
        audio.level.value = -33.0
        assertEquals(-33.0, viewModel.liveLevelDb.value)
    }
}
