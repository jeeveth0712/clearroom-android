package com.clearroom.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clearroom.app.audio.AudioInput
import com.clearroom.app.audio.CaptureException
import com.clearroom.app.audio.RecordingSummary
import com.clearroom.app.ui.MeasurePhase
import com.clearroom.app.ui.UiEvent
import com.clearroom.app.ui.UiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app's single ViewModel (doc decision). It holds state and sequences work; the recording
 * itself is [AudioInput]'s job and the numbers are [RecordingSummary]'s.
 */
class AppViewModel(
    private val audio: AudioInput,
    private val dsp: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Separate from [state] so 20 level updates a second redraw only the level bar. */
    val liveLevelDb: StateFlow<Double> = audio.liveLevelDb

    private var recording: Job? = null

    fun onEvent(event: UiEvent) {
        when (event) {
            UiEvent.StartTestRecording -> startTestRecording()
            UiEvent.CancelRecording -> cancelRecording()
        }
    }

    private fun startTestRecording() {
        if (recording?.isActive == true) return
        recording = viewModelScope.launch {
            for (secondsLeft in COUNTDOWN_S downTo 1) {
                setPhase(MeasurePhase.Countdown(secondsLeft))
                delay(1_000)
            }
            setPhase(MeasurePhase.Recording(TEST_RECORDING_S))
            val outcome = try {
                val capture = audio.record(TEST_RECORDING_S)
                MeasurePhase.Done(withContext(dsp) { RecordingSummary.of(capture) })
            } catch (e: CaptureException) {
                MeasurePhase.Failed(e.problem)
            }
            setPhase(outcome)
        }
    }

    private fun cancelRecording() {
        recording?.cancel()
        recording = null
        setPhase(MeasurePhase.Idle)
    }

    private fun setPhase(phase: MeasurePhase) {
        _state.update { it.copy(measure = it.measure.copy(phase = phase)) }
    }

    companion object {
        /** Doc "Exact parameters": a 3-s countdown before each recording. */
        const val COUNTDOWN_S = 3

        /** Doc "Exact parameters": clap recordings are 5 s; the microphone test uses the same length. */
        const val TEST_RECORDING_S = 5.0

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(container.audioInput) }
        }
    }
}
