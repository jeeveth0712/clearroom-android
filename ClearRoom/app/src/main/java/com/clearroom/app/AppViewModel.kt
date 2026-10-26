package com.clearroom.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clearroom.app.acoustics.ClarityModel
import com.clearroom.app.acoustics.ClarityResult
import com.clearroom.app.acoustics.Rt60Summary
import com.clearroom.app.audio.AudioClip
import com.clearroom.app.audio.AudioInput
import com.clearroom.app.data.FanState
import com.clearroom.app.data.RoomForm
import com.clearroom.app.data.RoomRepository
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SavedRoom
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.Session
import com.clearroom.app.dsp.Reverb
import com.clearroom.app.measure.ClapTake
import com.clearroom.app.measure.ClapVerdict
import com.clearroom.app.measure.MeasureKind
import com.clearroom.app.measure.MeasureOutcome
import com.clearroom.app.measure.MeasurementRunner
import com.clearroom.app.measure.bandLevelsOf
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

/**
 * The app's single ViewModel (doc decision). It holds state and sequences work; recording is
 * [MeasurementRunner]'s job, analysis belongs to the engine (OctaveBank, Reverb, ClapTake) and
 * storage to [RoomRepository].
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

    /**
     * The clarity engine's verdict for the working room (FR-6, FR-7), recomputed off the main
     * thread whenever its measurements change; null while no room is open.
     */
    val clarity: StateFlow<ClarityResult?> = state
        .map { it.session }
        .distinctUntilChanged()
        .map { session -> session?.let { ClarityModel.evaluate(it) } }
        .flowOn(dsp)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val runner = MeasurementRunner(audio, dsp)
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
            is UiEvent.SetClapDelay -> setClapDelay(event.seconds)
            is UiEvent.ClearClaps -> clearClaps(event.seat)
            is UiEvent.ClearSeat -> clearSeat(event.seat)
            is UiEvent.RoomFormChanged -> updateRoom { it.copy(form = event.form, problems = emptyMap(), notice = null) }
            UiEvent.SaveRoom -> viewModelScope.launch { saveRoom() }
            UiEvent.NewRoom -> newRoom()
            UiEvent.LoadSampleRoom -> loadSampleRoom()
            is UiEvent.OpenRoom -> viewModelScope.launch { openRoom(event.roomId) }
            is UiEvent.DeleteRoom -> viewModelScope.launch { deleteRoom(event.roomId) }
        }
    }

    // --- Measuring (FR-2 to FR-5) ------------------------------------------------------------

    private fun selectSeat(seat: SeatId) {
        val session = _state.value.session ?: return
        if (session.contains(seat) && !_state.value.measure.phase.isBusy) {
            updateMeasure { it.copy(selectedSeat = seat, phase = MeasurePhase.Idle) }
        }
    }

    private fun setClapDelay(seconds: Int) {
        if (seconds in MeasureUiState.CLAP_DELAYS_S) updateMeasure { it.copy(clapDelayS = seconds) }
    }

    private fun startMeasurement(kind: MeasureKind) {
        if (measurement?.isActive == true) return
        val current = _state.value
        val roomId = current.session?.room?.id?.takeUnless { it == SampleData.ROOM_ID } ?: return
        val seat = current.measure.selectedSeat
        if (kind != MeasureKind.VOICE && seat == null) return

        measurement = viewModelScope.launch {
            setPhase(
                when (kind) {
                    MeasureKind.NOISE -> measureNoise(roomId, requireNotNull(seat), current.measure.fans)
                    MeasureKind.VOICE -> measureVoice(roomId)
                    MeasureKind.CLAP -> measureClap(roomId, requireNotNull(seat), current.measure.clapDelayS)
                },
            )
        }
    }

    private suspend fun measureNoise(roomId: String, seat: SeatId, fans: FanState): MeasurePhase =
        record(MeasureKind.NOISE, analyse = ::bandLevelsOf).toPhase(MeasureKind.NOISE) { levels ->
            if (saveMeasurement(roomId) { it.withNoise(seat, levels, fans) } == null) MeasurePhase.SaveFailed
            else MeasurePhase.Saved(MeasureKind.NOISE, seat)
        }

    private suspend fun measureVoice(roomId: String): MeasurePhase =
        record(MeasureKind.VOICE, analyse = ::bandLevelsOf).toPhase(MeasureKind.VOICE) { levels ->
            if (saveMeasurement(roomId) { it.withVoice(levels) } == null) MeasurePhase.SaveFailed
            else MeasurePhase.Saved(MeasureKind.VOICE, seat = null)
        }

    private suspend fun measureClap(roomId: String, seat: SeatId, delayS: Int): MeasurePhase =
        record(MeasureKind.CLAP, delayS) { Reverb.analyse(it.samples, it.sampleRate) }.toPhase(MeasureKind.CLAP) { result ->
            when (val verdict = ClapTake.judge(result)) {
                is ClapVerdict.Rejected -> MeasurePhase.ClapRejected(verdict.problem)
                is ClapVerdict.Accepted -> {
                    val saved = saveMeasurement(roomId) { it.withClap(seat, verdict.take) }
                    val speechRt60S = requireNotNull(Rt60Summary.of(listOf(verdict.take))).speechRt60S
                    if (saved == null) MeasurePhase.SaveFailed
                    else MeasurePhase.ClapAccepted(seat, saved.measureAt(seat)?.rtTakes?.size ?: 1, speechRt60S)
                }
            }
        }

    private fun clearClaps(seat: SeatId) = clearReadings { it.withoutClaps(seat) }

    private fun clearSeat(seat: SeatId) = clearReadings { it.withoutSeat(seat) }

    /** Removes readings from the user's own room (never the sample), unless a recording is running. */
    private fun clearReadings(change: (Session) -> Session) {
        val roomId = _state.value.session?.room?.id?.takeUnless { it == SampleData.ROOM_ID } ?: return
        if (_state.value.measure.phase.isBusy) return
        viewModelScope.launch {
            val saved = saveMeasurement(roomId, change)
            setPhase(if (saved == null) MeasurePhase.SaveFailed else MeasurePhase.Idle)
        }
    }

    private suspend fun <T> record(kind: MeasureKind, countdownS: Int = MeasurementRunner.COUNTDOWN_S, analyse: (AudioClip) -> T) =
        runner.run(
            kind,
            countdownS,
            onCountdown = { setPhase(MeasurePhase.Countdown(kind, it)) },
            onRecording = { setPhase(MeasurePhase.Recording(kind)) },
            analyse = analyse,
        )

    /** Unusable recordings and microphone problems read the same for every kind of measurement. */
    private inline fun <T> MeasureOutcome<T>.toPhase(kind: MeasureKind, onMeasured: (T) -> MeasurePhase): MeasurePhase =
        when (this) {
            is MeasureOutcome.Measured -> onMeasured(result)
            is MeasureOutcome.Rejected -> MeasurePhase.Rejected(kind, quality)
            is MeasureOutcome.Failed -> MeasurePhase.Failed(problem)
        }

    /**
     * Applies [change] to the latest version of room [roomId], stores it and returns it, or null if
     * the room is gone or the save failed. The user may switch rooms during a 30-s recording, so a
     * result always goes to the room it was measured in.
     */
    private suspend fun saveMeasurement(roomId: String, change: (Session) -> Session): Session? {
        val latest = _state.value.session?.takeIf { it.room.id == roomId } ?: rooms.load(roomId) ?: return null
        val updated = change(latest)
        try {
            rooms.save(updated)
        } catch (e: IOException) {
            return null
        }
        val saved = rooms.list()
        _state.update {
            it.copy(
                session = if (it.session?.room?.id == roomId) updated else it.session,
                room = it.room.copy(saved = saved),
            )
        }
        return updated
    }

    private fun cancelMeasurement() {
        measurement?.cancel()
        measurement = null
        setPhase(MeasurePhase.Idle)
    }

    // --- Rooms (FR-1) ------------------------------------------------------------------------

    private suspend fun saveRoom() {
        val current = _state.value
        // The sample is read-only: saving it creates the user's own copy with a fresh id. Only its
        // layout is copied: its readings are illustrative, and must never mix with real measurements.
        val fromSample = current.room.editingId == SampleData.ROOM_ID
        val id = current.room.editingId?.takeUnless { fromSample } ?: newRoomId()
        when (val result = current.room.form.toRoom(id)) {
            is RoomForm.Result.Invalid -> updateRoom { it.copy(problems = result.problems, notice = null) }
            is RoomForm.Result.Valid -> {
                val session = if (fromSample) Session(result.room) else current.session?.withRoom(result.room) ?: Session(result.room)
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
