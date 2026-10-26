package com.clearroom.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clearroom.app.audio.AudioInput
import com.clearroom.app.audio.CaptureException
import com.clearroom.app.audio.RecordingSummary
import com.clearroom.app.data.RoomForm
import com.clearroom.app.data.RoomRepository
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SavedRoom
import com.clearroom.app.data.Session
import com.clearroom.app.ui.MeasurePhase
import com.clearroom.app.ui.RoomNotice
import com.clearroom.app.ui.RoomUiState
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
import java.io.IOException
import java.util.UUID

/**
 * The app's single ViewModel (doc decision). It holds state and sequences work; recording is
 * [AudioInput]'s job, storage is [RoomRepository]'s and the numbers are the engine's.
 */
class AppViewModel(
    private val audio: AudioInput,
    private val rooms: RoomRepository,
    private val dsp: CoroutineDispatcher = Dispatchers.Default,
    private val newRoomId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Separate from [state] so 20 level updates a second redraw only the level bar. */
    val liveLevelDb: StateFlow<Double> = audio.liveLevelDb

    private var recording: Job? = null

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
            UiEvent.StartTestRecording -> startTestRecording()
            UiEvent.CancelRecording -> cancelRecording()
            is UiEvent.RoomFormChanged -> updateRoom { it.copy(form = event.form, problems = emptyMap(), notice = null) }
            UiEvent.SaveRoom -> viewModelScope.launch { saveRoom() }
            UiEvent.NewRoom -> newRoom()
            UiEvent.LoadSampleRoom -> loadSampleRoom()
            is UiEvent.OpenRoom -> viewModelScope.launch { openRoom(event.roomId) }
            is UiEvent.DeleteRoom -> viewModelScope.launch { deleteRoom(event.roomId) }
        }
    }

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
        _state.update { it.copy(session = null, room = RoomUiState(saved = it.room.saved)) }
    }

    private fun loadSampleRoom() = showSession(SampleData.session, notice = RoomNotice.SAMPLE_LOADED)

    private suspend fun deleteRoom(roomId: String) {
        rooms.delete(roomId)
        val saved = rooms.list()
        val wasOpen = _state.value.session?.room?.id == roomId
        if (wasOpen) newRoom()
        updateRoom { it.copy(saved = saved, notice = RoomNotice.DELETED) }
    }

    /** Makes [session] the working room and loads it into the form for editing. */
    private fun showSession(session: Session, notice: RoomNotice?) {
        _state.update {
            it.copy(
                session = session,
                room = it.room.copy(
                    form = RoomForm.from(session.room),
                    editingId = session.room.id,
                    problems = emptyMap(),
                    notice = notice,
                ),
            )
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

    private fun updateRoom(change: (RoomUiState) -> RoomUiState) {
        _state.update { it.copy(room = change(it.room)) }
    }

    companion object {
        /** Doc "Exact parameters": a 3-s countdown before each recording. */
        const val COUNTDOWN_S = 3

        /** Doc "Exact parameters": clap recordings are 5 s; the microphone test uses the same length. */
        const val TEST_RECORDING_S = 5.0

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(container.audioInput, container.roomRepository) }
        }
    }
}
