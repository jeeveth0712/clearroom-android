package com.clearroom.app.ui

import com.clearroom.app.audio.CaptureProblem
import com.clearroom.app.audio.RecordingSummary

/** Everything the screens show, one immutable value. Only AppViewModel writes it. */
data class UiState(
    val measure: MeasureUiState = MeasureUiState(),
)

data class MeasureUiState(
    val phase: MeasurePhase = MeasurePhase.Idle,
)

/** Steps of one recording. Only legal states exist, so the screen is a single `when`. */
sealed interface MeasurePhase {
    data object Idle : MeasurePhase
    data class Countdown(val secondsLeft: Int) : MeasurePhase
    data class Recording(val durationS: Double) : MeasurePhase
    data class Done(val summary: RecordingSummary) : MeasurePhase
    data class Failed(val problem: CaptureProblem) : MeasurePhase
}

/** User actions the screens send up to AppViewModel. */
sealed interface UiEvent {
    data object StartTestRecording : UiEvent
    data object CancelRecording : UiEvent
}
