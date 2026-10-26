package com.clearroom.app

import com.clearroom.app.audio.CaptureProblem
import com.clearroom.app.audio.ClipQuality
import com.clearroom.app.data.FanState
import com.clearroom.app.data.FieldProblem
import com.clearroom.app.data.RoomField
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SavedRoom
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.SeatMeasure
import com.clearroom.app.data.Session
import com.clearroom.app.measure.MeasurementRunner
import com.clearroom.app.measure.ClapProblem
import com.clearroom.app.measure.MeasureKind
import com.clearroom.app.testing.FakeAudioInput
import com.clearroom.app.testing.InMemoryRoomRepository
import com.clearroom.app.testing.MainDispatcherRule
import com.clearroom.app.testing.Signals
import com.clearroom.app.testing.aMeasuredSeat
import com.clearroom.app.testing.aRoom
import com.clearroom.app.testing.aValidForm
import com.clearroom.app.ui.MeasurePhase
import com.clearroom.app.ui.RoomNotice
import com.clearroom.app.ui.UiEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val audio = FakeAudioInput()
    private val rooms = InMemoryRoomRepository()
    private var idCounter = 0

    private fun newViewModel() = AppViewModel(audio, rooms, dsp = main.dispatcher, newRoomId = { "new-${++idCounter}" })

    private val viewModel by lazy { newViewModel() }
    private val phase get() = viewModel.state.value.measure.phase
    private val roomState get() = viewModel.state.value.room

    /** Creates the ViewModel the way the app starts, after the test has set up saved rooms. */
    private fun TestScope.startApp(): AppViewModel = newViewModel().also { runCurrent() }

    private fun TestScope.advanceSeconds(seconds: Int) {
        advanceTimeBy(seconds * 1_000L)
        runCurrent()
    }

    // --- Measuring noise and voice (M4) ------------------------------------------------------

    /** Starts the app with saved room "r1" (5 x 6 seats) open, as a user would be before measuring. */
    private fun TestScope.startWithRoom(vararg seatsMeasured: SeatMeasure): AppViewModel {
        saveNow(Session(aRoom(id = "r1"), seats = seatsMeasured.toList()))
        return startApp()
    }

    private fun TestScope.saveNow(session: Session) {
        launch { rooms.save(session) }
        runCurrent()
    }

    private fun AppViewModel.phase() = state.value.measure.phase

    @Test
    fun `starts idle with no seat picked`() {
        assertEquals(MeasurePhase.Idle, phase)
        assertNull(viewModel.state.value.measure.selectedSeat)
    }

    @Test
    fun `nothing is measured without a saved room or, for noise, a seat`() = runTest {
        viewModel.onEvent(UiEvent.StartMeasurement(MeasureKind.VOICE)) // no room at all
        val vm = startWithRoom()
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.NOISE)) // room, but no seat picked
        advanceUntilIdle()
        assertEquals(emptyList(), audio.requestedSeconds)
    }

    @Test
    fun `the sample room can't be measured into`() = runTest {
        val vm = startApp()
        vm.onEvent(UiEvent.LoadSampleRoom)
        vm.onEvent(UiEvent.SelectSeat(SeatId(0, 0)))
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.NOISE))
        advanceUntilIdle()
        assertEquals(emptyList(), audio.requestedSeconds)
    }

    @Test
    fun `noise at a seat counts down, listens 30 s, then saves the levels and fan state`() = runTest {
        val vm = startWithRoom()
        vm.onEvent(UiEvent.SelectSeat(SeatId(2, 3)))
        vm.onEvent(UiEvent.SetFans(FanState.FAST))
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.NOISE))
        runCurrent()
        assertEquals(MeasurePhase.Countdown(MeasureKind.NOISE, 3), vm.phase())
        advanceSeconds(MeasurementRunner.COUNTDOWN_S)
        assertEquals(MeasurePhase.Recording(MeasureKind.NOISE), vm.phase())
        advanceUntilIdle()

        assertEquals(MeasurePhase.Saved(MeasureKind.NOISE, SeatId(2, 3)), vm.phase())
        assertEquals(listOf(30.0), audio.requestedSeconds)
        val stored = assertNotNull(assertNotNull(rooms.load("r1")).measureAt(SeatId(2, 3)))
        assertEquals(FanState.FAST, stored.fans)
        val noise = assertNotNull(stored.noise).valuesDb
        assertEquals(3, noise.indices.maxBy { noise[it] }, "the 1 kHz test tone should be loudest: $noise")
        assertEquals(stored, vm.state.value.session?.measureAt(SeatId(2, 3)))
    }

    @Test
    fun `the voice reference needs no seat and is saved for the room`() = runTest {
        val vm = startWithRoom()
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.VOICE))
        advanceUntilIdle()
        assertEquals(MeasurePhase.Saved(MeasureKind.VOICE, null), vm.phase())
        assertEquals(listOf(20.0), audio.requestedSeconds)
        assertNotNull(assertNotNull(rooms.load("r1")).voice)
    }

    @Test
    fun `measuring a seat again replaces its noise and keeps its echo takes`() = runTest {
        val vm = startWithRoom(aMeasuredSeat(0, 0))
        vm.onEvent(UiEvent.SelectSeat(SeatId(0, 0)))
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.NOISE))
        advanceUntilIdle()
        val seat = assertNotNull(rooms.load("r1")).seats.single()
        assertNotEquals(aMeasuredSeat(0, 0).noise, seat.noise)
        assertEquals(aMeasuredSeat(0, 0).rtTakes, seat.rtTakes)
    }

    @Test
    fun `a clipped recording is rejected and nothing is saved`() = runTest {
        val vm = startWithRoom()
        vm.onEvent(UiEvent.SelectSeat(SeatId(0, 0)))
        audio.samples = { rate, seconds -> Signals.clipped(run = 10, seconds = seconds, sampleRate = rate) }
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.NOISE))
        advanceUntilIdle()
        assertEquals(MeasurePhase.Rejected(MeasureKind.NOISE, ClipQuality.CLIPPED), vm.phase())
        assertEquals(emptyList(), assertNotNull(rooms.load("r1")).seats)
    }

    @Test
    fun `microphone problems and failed saves are reported`() = runTest {
        val vm = startWithRoom()
        audio.failWith = CaptureProblem.MIC_UNAVAILABLE
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.VOICE))
        advanceUntilIdle()
        assertEquals(MeasurePhase.Failed(CaptureProblem.MIC_UNAVAILABLE), vm.phase())

        audio.failWith = null
        rooms.failSaves = true
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.VOICE))
        advanceUntilIdle()
        assertEquals(MeasurePhase.SaveFailed, vm.phase())
    }

    @Test
    fun `cancel during the countdown returns to idle without opening the microphone`() = runTest {
        val vm = startWithRoom()
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.VOICE))
        advanceSeconds(1)
        vm.onEvent(UiEvent.CancelRecording)
        advanceUntilIdle()
        assertEquals(MeasurePhase.Idle, vm.phase())
        assertEquals(emptyList(), audio.requestedSeconds)
    }

    @Test
    fun `cancel during recording stops it, returns to idle and saves nothing`() = runTest {
        val vm = startWithRoom()
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.VOICE))
        advanceSeconds(MeasurementRunner.COUNTDOWN_S + 1)
        assertIs<MeasurePhase.Recording>(vm.phase())
        vm.onEvent(UiEvent.CancelRecording)
        advanceUntilIdle()
        assertEquals(MeasurePhase.Idle, vm.phase())
        assertEquals(1, audio.cancelledRecordings)
        assertNull(assertNotNull(rooms.load("r1")).voice)
    }

    @Test
    fun `starting again while busy doesn't start a second recording, and the seat can't change`() = runTest {
        val vm = startWithRoom()
        vm.onEvent(UiEvent.SelectSeat(SeatId(0, 0)))
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.NOISE))
        advanceSeconds(1)
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.VOICE))
        vm.onEvent(UiEvent.SelectSeat(SeatId(4, 5)))
        assertEquals(SeatId(0, 0), vm.state.value.measure.selectedSeat)
        advanceUntilIdle()
        assertEquals(1, audio.requestedSeconds.size)
    }

    @Test
    fun `seats outside the grid can't be picked`() = runTest {
        val vm = startWithRoom()
        vm.onEvent(UiEvent.SelectSeat(SeatId(5, 0)))
        assertNull(vm.state.value.measure.selectedSeat)
    }

    @Test
    fun `the seat choice is kept for the same room but cleared for another room or a smaller grid`() = runTest {
        saveNow(Session(aRoom(id = "other")))
        val vm = startWithRoom()
        vm.onEvent(UiEvent.SelectSeat(SeatId(4, 0)))

        vm.onEvent(UiEvent.RoomFormChanged(vm.state.value.room.form.copy(name = "Renamed")))
        vm.onEvent(UiEvent.SaveRoom)
        runCurrent()
        assertEquals(SeatId(4, 0), vm.state.value.measure.selectedSeat)

        vm.onEvent(UiEvent.RoomFormChanged(vm.state.value.room.form.copy(rows = "3")))
        vm.onEvent(UiEvent.SaveRoom)
        runCurrent()
        assertNull(vm.state.value.measure.selectedSeat)

        vm.onEvent(UiEvent.SelectSeat(SeatId(0, 0)))
        vm.onEvent(UiEvent.OpenRoom("other"))
        runCurrent()
        assertNull(vm.state.value.measure.selectedSeat)
    }

    @Test
    fun `a measurement is saved to the room it was taken in, even if the user switches rooms meanwhile`() = runTest {
        saveNow(Session(aRoom(id = "other")))
        val vm = startWithRoom() // "r1" is saved last, so it opens
        vm.onEvent(UiEvent.SelectSeat(SeatId(1, 1)))
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.NOISE))
        advanceSeconds(MeasurementRunner.COUNTDOWN_S + 1)
        vm.onEvent(UiEvent.OpenRoom("other"))
        advanceUntilIdle()
        assertNotNull(assertNotNull(rooms.load("r1")).measureAt(SeatId(1, 1)))
        assertEquals(emptyList(), assertNotNull(rooms.load("other")).seats)
        assertEquals("other", vm.state.value.session?.room?.id)
    }

    // --- Clap test (M5) ------------------------------------------------------------------------

    private fun clapsWithEcho(t60S: Double) {
        audio.samples = { rate, seconds -> Signals.syntheticClap(t60S, sampleRate = rate, lengthS = seconds) }
    }

    @Test
    fun `a clap test needs a seat`() = runTest {
        val vm = startWithRoom()
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.CLAP))
        advanceUntilIdle()
        assertEquals(emptyList(), audio.requestedSeconds)
    }

    @Test
    fun `a good clap counts down, records 5 s, and saves a take with its echo time`() = runTest {
        clapsWithEcho(0.8)
        val vm = startWithRoom()
        vm.onEvent(UiEvent.SelectSeat(SeatId(4, 2)))
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.CLAP))
        runCurrent()
        assertEquals(MeasurePhase.Countdown(MeasureKind.CLAP, 3), vm.phase())
        advanceSeconds(MeasurementRunner.COUNTDOWN_S)
        assertEquals(MeasurePhase.Recording(MeasureKind.CLAP), vm.phase())
        advanceUntilIdle()

        val accepted = assertIs<MeasurePhase.ClapAccepted>(vm.phase())
        assertEquals(SeatId(4, 2), accepted.seat)
        assertEquals(1, accepted.takes)
        assertEquals(0.8, accepted.speechRt60S, absoluteTolerance = 0.08)
        assertEquals(listOf(5.0), audio.requestedSeconds)
        assertEquals(1, assertNotNull(assertNotNull(rooms.load("r1")).measureAt(SeatId(4, 2))).rtTakes.size)
    }

    @Test
    fun `three claps give three takes at the seat`() = runTest {
        clapsWithEcho(1.2)
        val vm = startWithRoom()
        vm.onEvent(UiEvent.SelectSeat(SeatId(0, 0)))
        repeat(3) {
            vm.onEvent(UiEvent.StartMeasurement(MeasureKind.CLAP))
            advanceUntilIdle()
        }
        assertEquals(3, assertIs<MeasurePhase.ClapAccepted>(vm.phase()).takes)
        assertEquals(3, vm.state.value.session?.measureAt(SeatId(0, 0))?.rtTakes?.size)
    }

    @Test
    fun `the clap countdown can be 10 s to walk to the front, but only the offered lengths`() = runTest {
        clapsWithEcho(0.8)
        val vm = startWithRoom()
        vm.onEvent(UiEvent.SetClapDelay(7))
        assertEquals(3, vm.state.value.measure.clapDelayS)
        vm.onEvent(UiEvent.SetClapDelay(10))
        vm.onEvent(UiEvent.SelectSeat(SeatId(0, 0)))
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.CLAP))
        runCurrent()
        assertEquals(MeasurePhase.Countdown(MeasureKind.CLAP, 10), vm.phase())
        advanceSeconds(9)
        assertEquals(MeasurePhase.Countdown(MeasureKind.CLAP, 1), vm.phase())
    }

    @Test
    fun `a missing clap is explained and nothing is saved`() = runTest {
        audio.samples = { rate, seconds -> Signals.sine(1_000.0, 0.05, seconds, rate) } // steady tone, no clap
        val vm = startWithRoom()
        vm.onEvent(UiEvent.SelectSeat(SeatId(0, 0)))
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.CLAP))
        advanceUntilIdle()
        assertEquals(MeasurePhase.ClapRejected(ClapProblem.NO_CLAP), vm.phase())
        assertEquals(emptyList(), assertNotNull(rooms.load("r1")).seats)
    }

    @Test
    fun `a clipped clap is rejected as too loud`() = runTest {
        audio.samples = { rate, seconds -> Signals.clipped(run = 10, seconds = seconds, sampleRate = rate) }
        val vm = startWithRoom()
        vm.onEvent(UiEvent.SelectSeat(SeatId(0, 0)))
        vm.onEvent(UiEvent.StartMeasurement(MeasureKind.CLAP))
        advanceUntilIdle()
        assertEquals(MeasurePhase.Rejected(MeasureKind.CLAP, ClipQuality.CLIPPED), vm.phase())
    }

    @Test
    fun `clearing claps removes that seat's takes but keeps its noise`() = runTest {
        val vm = startWithRoom(aMeasuredSeat(1, 1))
        vm.onEvent(UiEvent.ClearClaps(SeatId(1, 1)))
        runCurrent()
        val seat = assertNotNull(assertNotNull(rooms.load("r1")).measureAt(SeatId(1, 1)))
        assertEquals(emptyList(), seat.rtTakes)
        assertEquals(aMeasuredSeat(1, 1).noise, seat.noise)
        assertEquals(MeasurePhase.Idle, vm.phase())
    }

    @Test
    fun `the live level comes straight from the audio input`() {
        audio.level.value = -33.0
        assertEquals(-33.0, viewModel.liveLevelDb.value)
    }

    // --- Rooms (M3) -------------------------------------------------------------------------

    @Test
    fun `on startup the most recently saved room is opened, so work survives a restart`() = runTest {
        rooms.save(Session(aRoom(id = "older", name = "Older room")))
        rooms.save(Session(aRoom(id = "newer", name = "Newer room")))
        val restarted = startApp()
        assertEquals("newer", restarted.state.value.session?.room?.id)
        assertEquals("Newer room", restarted.state.value.room.form.name)
        assertEquals(listOf("newer", "older"), restarted.state.value.room.saved.map { it.id })
    }

    @Test
    fun `with nothing saved the app starts with a blank form and no room`() = runTest {
        val vm = startApp()
        assertNull(vm.state.value.session)
        assertEquals("", vm.state.value.room.form.name)
        assertEquals(emptyList(), vm.state.value.room.saved)
    }

    @Test
    fun `saving a valid new room stores it, opens it and lists it`() = runTest {
        viewModel.onEvent(UiEvent.RoomFormChanged(aValidForm(name = "Lab 2")))
        viewModel.onEvent(UiEvent.SaveRoom)
        runCurrent()
        val stored = assertNotNull(rooms.load("new-1"))
        assertEquals("Lab 2", stored.room.name)
        assertEquals(stored, viewModel.state.value.session)
        assertEquals("new-1", roomState.editingId)
        assertEquals(RoomNotice.SAVED, roomState.notice)
        assertEquals(listOf("new-1"), roomState.saved.map { it.id })
    }

    @Test
    fun `saving an invalid form shows the problems and stores nothing`() = runTest {
        viewModel.onEvent(UiEvent.RoomFormChanged(aValidForm().copy(lengthM = "", rows = "40")))
        viewModel.onEvent(UiEvent.SaveRoom)
        runCurrent()
        assertEquals(mapOf(RoomField.LENGTH to FieldProblem.MISSING, RoomField.ROWS to FieldProblem.OUT_OF_RANGE), roomState.problems)
        assertEquals(emptyList(), rooms.list())
        assertNull(roomState.notice)
    }

    @Test
    fun `editing the form clears earlier problems and notices`() = runTest {
        viewModel.onEvent(UiEvent.RoomFormChanged(aValidForm().copy(lengthM = "")))
        viewModel.onEvent(UiEvent.SaveRoom)
        runCurrent()
        viewModel.onEvent(UiEvent.RoomFormChanged(aValidForm()))
        assertEquals(emptyMap(), roomState.problems)
        assertNull(roomState.notice)
    }

    @Test
    fun `saving an opened room keeps its id and its measurements`() = runTest {
        rooms.save(Session(aRoom(id = "r1"), seats = listOf(aMeasuredSeat(0, 0))))
        val vm = startApp()
        vm.onEvent(UiEvent.RoomFormChanged(vm.state.value.room.form.copy(name = "Renamed")))
        vm.onEvent(UiEvent.SaveRoom)
        runCurrent()
        val stored = assertNotNull(rooms.load("r1"))
        assertEquals("Renamed", stored.room.name)
        assertEquals(listOf(aMeasuredSeat(0, 0)), stored.seats)
        assertEquals(1, rooms.list().size)
    }

    @Test
    fun `shrinking the grid drops measurements of seats that no longer exist`() = runTest {
        rooms.save(Session(aRoom(id = "r1", rows = 5), seats = listOf(aMeasuredSeat(0, 0), aMeasuredSeat(4, 0))))
        val vm = startApp()
        vm.onEvent(UiEvent.RoomFormChanged(vm.state.value.room.form.copy(rows = "3")))
        vm.onEvent(UiEvent.SaveRoom)
        runCurrent()
        assertEquals(listOf(aMeasuredSeat(0, 0)), assertNotNull(rooms.load("r1")).seats)
    }

    @Test
    fun `the sample room loads into the form without being saved`() = runTest {
        viewModel.onEvent(UiEvent.LoadSampleRoom)
        runCurrent()
        assertEquals(SampleData.session, viewModel.state.value.session)
        assertEquals(SampleData.room.name, roomState.form.name)
        assertEquals(RoomNotice.SAMPLE_LOADED, roomState.notice)
        assertEquals(emptyList(), rooms.list())
    }

    @Test
    fun `saving the sample makes the user's own copy with a new id and the sample measurements`() = runTest {
        viewModel.onEvent(UiEvent.LoadSampleRoom)
        viewModel.onEvent(UiEvent.SaveRoom)
        runCurrent()
        val savedId = roomState.editingId
        assertNotEquals(SampleData.ROOM_ID, savedId)
        val stored = assertNotNull(rooms.load(assertNotNull(savedId)))
        assertEquals(SampleData.session.seats, stored.seats)
        assertNull(rooms.load(SampleData.ROOM_ID))
    }

    @Test
    fun `loading the sample before startup finishes is not overridden by the last saved room`() = runTest {
        rooms.save(Session(aRoom(id = "r1")))
        val fresh = newViewModel()
        fresh.onEvent(UiEvent.LoadSampleRoom) // before the startup coroutine has run
        runCurrent()
        assertEquals(SampleData.ROOM_ID, fresh.state.value.session?.room?.id)
    }

    @Test
    fun `a failed save keeps the form and says so`() = runTest {
        rooms.failSaves = true
        viewModel.onEvent(UiEvent.RoomFormChanged(aValidForm(name = "Lab 3")))
        viewModel.onEvent(UiEvent.SaveRoom)
        runCurrent()
        assertEquals(RoomNotice.SAVE_FAILED, roomState.notice)
        assertEquals("Lab 3", roomState.form.name)
        assertNull(viewModel.state.value.session)
    }

    @Test
    fun `opening a room that can't be read says so and keeps the current room`() = runTest {
        viewModel.onEvent(UiEvent.LoadSampleRoom)
        viewModel.onEvent(UiEvent.OpenRoom("missing"))
        runCurrent()
        assertEquals(RoomNotice.OPEN_FAILED, roomState.notice)
        assertEquals(SampleData.session, viewModel.state.value.session)
    }

    @Test
    fun `deleting the open room clears it from the screen and the list`() = runTest {
        rooms.save(Session(aRoom(id = "r1")))
        val vm = startApp()
        vm.onEvent(UiEvent.DeleteRoom("r1"))
        runCurrent()
        assertNull(vm.state.value.session)
        assertEquals("", vm.state.value.room.form.name)
        assertEquals(emptyList(), vm.state.value.room.saved)
        assertEquals(RoomNotice.DELETED, vm.state.value.room.notice)
    }

    @Test
    fun `deleting another room keeps the open one`() = runTest {
        rooms.save(Session(aRoom(id = "other", name = "Other")))
        rooms.save(Session(aRoom(id = "open", name = "Open")))
        val vm = startApp()
        vm.onEvent(UiEvent.DeleteRoom("other"))
        runCurrent()
        assertEquals("open", vm.state.value.session?.room?.id)
        assertEquals(listOf("open"), vm.state.value.room.saved.map { it.id })
    }

    @Test
    fun `a damaged file can be deleted from the list`() = runTest {
        rooms.damagedIds += "broken"
        val vm = startApp()
        assertTrue(vm.state.value.room.saved.single() is SavedRoom.Damaged)
        vm.onEvent(UiEvent.DeleteRoom("broken"))
        runCurrent()
        assertEquals(emptyList(), vm.state.value.room.saved)
    }

    @Test
    fun `new room clears the form and the working room but keeps the saved list`() = runTest {
        rooms.save(Session(aRoom(id = "r1")))
        val vm = startApp()
        vm.onEvent(UiEvent.NewRoom)
        assertNull(vm.state.value.session)
        assertNull(vm.state.value.room.editingId)
        assertEquals("", vm.state.value.room.form.name)
        assertEquals(listOf("r1"), vm.state.value.room.saved.map { it.id })
    }
}
