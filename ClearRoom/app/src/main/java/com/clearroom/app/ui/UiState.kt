package com.clearroom.app.ui

import com.clearroom.app.audio.CaptureProblem
import com.clearroom.app.audio.ClipQuality
import com.clearroom.app.data.FanState
import com.clearroom.app.data.FieldProblem
import com.clearroom.app.data.RoomField
import com.clearroom.app.data.RoomForm
import com.clearroom.app.data.SavedRoom
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.Session
import com.clearroom.app.measure.ClapProblem
import com.clearroom.app.measure.MeasureKind

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
    /** The seat where the phone is; null until the user taps one. */
    val selectedSeat: SeatId? = null,
    /** FR-4: fan / AC state recorded with the next noise snapshot. */
    val fans: FanState = FanState.OFF,
    /** Countdown before a clap recording: the doc's 3 s, or 10 s to walk from the seat to the front. */
    val clapDelayS: Int = CLAP_DELAYS_S.first(),
    val phase: MeasurePhase = MeasurePhase.Idle,
) {
    companion object {
        val CLAP_DELAYS_S = listOf(3, 10)
    }
}

/** Steps of one measurement. Only legal states exist, so the screen is a single `when`. */
sealed interface MeasurePhase {
    data object Idle : MeasurePhase
    data class Countdown(val kind: MeasureKind, val secondsLeft: Int) : MeasurePhase
    data class Recording(val kind: MeasureKind) : MeasurePhase
    data class Saved(val kind: MeasureKind, val seat: SeatId?) : MeasurePhase

    /** The recording was unusable (clipped or silent); nothing was saved. */
    data class Rejected(val kind: MeasureKind, val quality: ClipQuality) : MeasurePhase
    data class Failed(val problem: CaptureProblem) : MeasurePhase

    /** A clap gave a usable echo time; [takes] is how many accepted claps the seat now has. */
    data class ClapAccepted(val seat: SeatId, val takes: Int, val speechRt60S: Double) : MeasurePhase

    /** A clap couldn't be used; [problem] says what to do differently (FR-2). */
    data class ClapRejected(val problem: ClapProblem) : MeasurePhase

    /** Measured fine, but writing the room file failed. */
    data object SaveFailed : MeasurePhase

    val isBusy: Boolean get() = this is Countdown || this is Recording
}

/** User actions the screens send up to AppViewModel. */
sealed interface UiEvent {
    data class SelectSeat(val seat: SeatId) : UiEvent
    data class SetFans(val fans: FanState) : UiEvent
    data class StartMeasurement(val kind: MeasureKind) : UiEvent
    data object CancelRecording : UiEvent
    data class SetClapDelay(val seconds: Int) : UiEvent
    data class ClearClaps(val seat: SeatId) : UiEvent

    data class RoomFormChanged(val form: RoomForm) : UiEvent
    data object SaveRoom : UiEvent
    data object NewRoom : UiEvent
    data object LoadSampleRoom : UiEvent
    data class OpenRoom(val roomId: String) : UiEvent
    data class DeleteRoom(val roomId: String) : UiEvent
}
