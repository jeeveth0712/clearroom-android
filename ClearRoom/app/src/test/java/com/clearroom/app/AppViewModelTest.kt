package com.clearroom.app

import com.clearroom.app.audio.CaptureProblem
import com.clearroom.app.audio.ClipQuality
import com.clearroom.app.data.FieldProblem
import com.clearroom.app.data.RoomField
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SavedRoom
import com.clearroom.app.data.Session
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

    // --- Microphone test (M2) -------------------------------------------------------------

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
