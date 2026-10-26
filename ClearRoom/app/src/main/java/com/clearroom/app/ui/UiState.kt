package com.clearroom.app.ui

import com.clearroom.app.audio.CaptureProblem
import com.clearroom.app.audio.RecordingSummary
import com.clearroom.app.data.FieldProblem
import com.clearroom.app.data.RoomField
import com.clearroom.app.data.RoomForm
import com.clearroom.app.data.SavedRoom
import com.clearroom.app.data.Session

/** Everything the screens show, one immutable value. Only AppViewModel writes it. */
data class UiState(
    /** The room being worked on, with its measurements; null until a room is saved or opened. */
    val session: Session? = null,
    val room: RoomUiState = RoomUiState(),
    val measure: MeasureUiState = MeasureUiState(),
)

data class RoomUiState(
    val saved: List<SavedRoom> = emptyList(),
    val form: RoomForm = RoomForm(),
    /** Shown after a Save attempt; cleared as soon as the user edits the form again. */
    val problems: Map<RoomField, FieldProblem> = emptyMap(),
    /** Id of the room the form edits; null for a room that hasn't been saved yet. */
    val editingId: String? = null,
    val notice: RoomNotice? = null,
)

enum class RoomNotice { SAVED, SAMPLE_LOADED, SAVE_FAILED, OPEN_FAILED, DELETED }

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

    data class RoomFormChanged(val form: RoomForm) : UiEvent
    data object SaveRoom : UiEvent
    data object NewRoom : UiEvent
    data object LoadSampleRoom : UiEvent
    data class OpenRoom(val roomId: String) : UiEvent
    data class DeleteRoom(val roomId: String) : UiEvent
}
