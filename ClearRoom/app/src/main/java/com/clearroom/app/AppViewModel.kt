package com.clearroom.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clearroom.app.audio.AudioInput
import com.clearroom.app.data.RoomForm
import com.clearroom.app.data.RoomRepository
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SavedRoom
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.Session
import com.clearroom.app.measure.LevelMeasurement
import com.clearroom.app.measure.MeasureKind
import com.clearroom.app.measure.MeasureOutcome
import com.clearroom.app.ui.MeasurePhase
import com.clearroom.app.ui.MeasureUiState
import com.clearroom.app.ui.RoomNotice
import com.clearroom.app.ui.RoomUiState
import com.clearroom.app.ui.UiEvent
import com.clearroom.app.ui.UiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

/**
 * The app's single ViewModel (doc decision). It holds state and sequences work; recording and
 * analysis are [LevelMeasurement]'s job and storage is [RoomRepository]'s.
 */
class AppViewModel(
    audio: AudioInput,
    private val rooms: RoomRepository,
    dsp: CoroutineDispatcher = Dispatchers.Default,
    private val newRoomId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Separate from [state] so 20 level updates a second redraw only the level bar. */
    val liveLevelDb: StateFlow<Double> = audio.liveLevelDb

    private val levels = LevelMeasurement(audio, dsp)
    private var measurement: Job? = null

    init {
        // Reopen the room the user saved last, so their work survives an app restart.
        viewModelScope.launch {
            val saved = rooms.list()
            updateRoom { it.copy(saved = saved) }
            val userAlreadyChoseARoom = _state.value.session != null || _state.value.room.editingId != null
            if (!userAlreadyChoseARoom) saved.firstOrNull { it is SavedRoom.Readable }?.let { openRoom(it.id) }
        }
    }

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.SelectSeat -> selectSeat(event.seat)
            is UiEvent.SetFans -> updateMeasure { it.copy(fans = event.fans) }
            is UiEvent.StartMeasurement -> startMeasurement(event.kind)
            UiEvent.CancelRecording -> cancelMeasurement()
            is UiEvent.RoomFormChanged -> updateRoom { it.copy(form = event.form, problems = emptyMap(), notice = null) }
            UiEvent.SaveRoom -> viewModelScope.launch { saveRoom() }
            UiEvent.NewRoom -> newRoom()
            UiEvent.LoadSampleRoom -> loadSampleRoom()
            is UiEvent.OpenRoom -> viewModelScope.launch { openRoom(event.roomId) }
            is UiEvent.DeleteRoom -> viewModelScope.launch { deleteRoom(event.roomId) }
        }
    }

    // --- Measuring (FR-4, FR-5) --------------------------------------------------------------

    private fun selectSeat(seat: SeatId) {
        val session = _state.value.session ?: return
        if (session.contains(seat) && !_state.value.measure.phase.isBusy) {
            updateMeasure { it.copy(selectedSeat = seat, phase = MeasurePhase.Idle) }
        }
    }

    private fun startMeasurement(kind: MeasureKind) {
        if (measurement?.isActive == true) return
        val current = _state.value
        val session = current.session?.takeUnless { it.room.id == SampleData.ROOM_ID } ?: return
        val seat = current.measure.selectedSeat
        if (kind == MeasureKind.NOISE && seat == null) return
        val fans = current.measure.fans

        measurement = viewModelScope.launch {
            val outcome = levels.run(
                kind,
                onCountdown = { setPhase(MeasurePhase.Countdown(kind, it)) },
                onRecording = { setPhase(MeasurePhase.Recording(kind)) },
            )
            setPhase(
                when (outcome) {
                    is MeasureOutcome.Rejected -> MeasurePhase.Rejected(kind, outcome.quality)
                    is MeasureOutcome.Failed -> MeasurePhase.Failed(outcome.problem)
                    is MeasureOutcome.Measured -> {
                        val saved = saveMeasurement(session.room.id) { latest ->
                            when (kind) {
                                MeasureKind.NOISE -> latest.withNoise(requireNotNull(seat), outcome.levels, fans)
                                MeasureKind.VOICE -> latest.withVoice(outcome.levels)
                            }
                        }
                        if (saved) MeasurePhase.Saved(kind, seat.takeIf { kind == MeasureKind.NOISE }) else MeasurePhase.SaveFailed
                    }
                },
            )
        }
    }

    /**
     * Applies [change] to the latest version of room [roomId] and stores it. The user may have
     * edited or switched rooms during a 30-s recording, so the result always goes to the room it
     * was measured in.
     */
    private suspend fun saveMeasurement(roomId: String, change: (Session) -> Session): Boolean {
        val latest = _state.value.session?.takeIf { it.room.id == roomId } ?: rooms.load(roomId) ?: return false
        val updated = change(latest)
        try {
            rooms.save(updated)
        } catch (e: IOException) {
            return false
        }
        val saved = rooms.list()
        _state.update {
            it.copy(
                session = if (it.session?.room?.id == roomId) updated else it.session,
                room = it.room.copy(saved = saved),
            )
        }
        return true
    }

    private fun cancelMeasurement() {
        measurement?.cancel()
        measurement = null
        setPhase(MeasurePhase.Idle)
    }

    // --- Rooms (FR-1) ------------------------------------------------------------------------

    private suspend fun saveRoom() {
        val current = _state.value
        // The sample is read-only: saving it creates the user's own copy with a fresh id.
        val id = current.room.editingId?.takeUnless { it == SampleData.ROOM_ID } ?: newRoomId()
        when (val result = current.room.form.toRoom(id)) {
            is RoomForm.Result.Invalid -> updateRoom { it.copy(problems = result.problems, notice = null) }
            is RoomForm.Result.Valid -> {
                val session = current.session?.withRoom(result.room) ?: Session(result.room)
                try {
                    rooms.save(session)
                } catch (e: IOException) {
                    updateRoom { it.copy(notice = RoomNotice.SAVE_FAILED) }
                    return
                }
                val saved = rooms.list()
                showSession(session, notice = RoomNotice.SAVED)
                updateRoom { it.copy(saved = saved) }
            }
        }
    }

    private suspend fun openRoom(roomId: String) {
        val session = rooms.load(roomId)
        if (session == null) {
            updateRoom { it.copy(notice = RoomNotice.OPEN_FAILED) }
        } else {
            showSession(session, notice = null)
        }
    }

    private fun newRoom() {
        _state.update { it.copy(session = null, room = RoomUiState(saved = it.room.saved), measure = it.measure.copy(selectedSeat = null)) }
    }

    private fun loadSampleRoom() = showSession(SampleData.session, notice = RoomNotice.SAMPLE_LOADED)

    private suspend fun deleteRoom(roomId: String) {
        rooms.delete(roomId)
        val saved = rooms.list()
        val wasOpen = _state.value.session?.room?.id == roomId
        if (wasOpen) newRoom()
        updateRoom { it.copy(saved = saved, notice = RoomNotice.DELETED) }
    }

    /** Makes [session] the working room, loads it into the form, and keeps the seat choice valid. */
    private fun showSession(session: Session, notice: RoomNotice?) {
        _state.update {
            val sameRoom = it.session?.room?.id == session.room.id
            it.copy(
                session = session,
                room = it.room.copy(
                    form = RoomForm.from(session.room),
                    editingId = session.room.id,
                    problems = emptyMap(),
                    notice = notice,
                ),
                // A seat choice carries over only within the same room, and only if the seat still exists.
                measure = it.measure.copy(selectedSeat = it.measure.selectedSeat?.takeIf { seat -> sameRoom && session.contains(seat) }),
            )
        }
    }

    private fun setPhase(phase: MeasurePhase) = updateMeasure { it.copy(phase = phase) }

    private fun updateMeasure(change: (MeasureUiState) -> MeasureUiState) {
        _state.update { it.copy(measure = change(it.measure)) }
    }

    private fun updateRoom(change: (RoomUiState) -> RoomUiState) {
        _state.update { it.copy(room = change(it.room)) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(container.audioInput, container.roomRepository) }
        }
    }
}
