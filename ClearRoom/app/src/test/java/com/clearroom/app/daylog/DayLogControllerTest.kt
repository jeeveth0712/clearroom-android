package com.clearroom.app.daylog

import com.clearroom.app.audio.CaptureProblem
import com.clearroom.app.testing.FakeAudioInput
import com.clearroom.app.testing.InMemoryLogStore
import com.clearroom.app.testing.ScriptedLabeller
import com.clearroom.app.testing.Signals
import com.clearroom.app.testing.flatBands
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DayLogControllerTest {

    private val audio = FakeAudioInput()
    private val store = InMemoryLogStore()
    private var labeller: ScriptedLabeller? = ScriptedLabeller(listOf(SoundLabel.FAN, null, SoundLabel.HORN))

    private fun TestScope.controller(): DayLogController {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return DayLogController(backgroundScope, audio, store, { labeller }, dispatcher, dispatcher, clock = { 1_000_000L + currentTime })
    }

    private fun TestScope.runFor(seconds: Int) {
        advanceTimeBy(seconds * 1_000L + 1)
        runCurrent()
    }

    @Test
    fun `each second becomes a logged second with its levels and label`() = runTest {
        val log = controller()
        log.start("room-1")
        runFor(3)
        val state = log.state.value
        assertTrue(state.running)
        assertEquals(listOf(SoundLabel.FAN, null, SoundLabel.HORN), state.seconds.map { it.label })
        assertTrue(state.seconds.all { it.bands[3] > it.bands[1] + 10 }, "the fake's 1 kHz tone shows in the 1 kHz band")
        assertEquals(state.seconds, store.latest("room-1")?.seconds)
    }

    @Test
    fun `stopping releases the labeller and closes the log file`() = runTest {
        val log = controller()
        log.start("room-1")
        runFor(2)
        log.stop()
        runCurrent()
        assertFalse(log.state.value.running)
        assertFalse(log.isRunning)
        assertTrue(labeller!!.closed)
        assertEquals(0, store.openWriters)
    }

    @Test
    fun `without the label model the log keeps levels only, and says so`() = runTest {
        labeller = null
        val log = controller()
        log.start("room-1")
        runFor(2)
        assertFalse(log.state.value.labelsAvailable)
        assertEquals(listOf(null, null), log.state.value.seconds.map { it.label })
    }

    @Test
    fun `a microphone failure stops the log with the reason`() = runTest {
        audio.failWith = CaptureProblem.MIC_UNAVAILABLE
        val log = controller()
        log.start("room-1")
        runFor(1)
        assertEquals(CaptureProblem.MIC_UNAVAILABLE, log.state.value.problem)
        assertFalse(log.state.value.running)
        assertEquals(0, store.openWriters)
    }

    @Test
    fun `starting twice keeps the first log`() = runTest {
        val log = controller()
        log.start("room-1")
        runFor(1)
        log.start("room-2")
        runFor(1)
        assertEquals("room-1", log.state.value.roomId)
        assertEquals(2, log.state.value.seconds.size)
    }

    @Test
    fun `a room's latest saved log is shown when nothing is running`() = runTest {
        store.open("room-1", 42L).apply { append(LogSecond(42L, flatBands(-50.0), SoundLabel.MUSIC)) }.close()
        val log = controller()
        log.show("room-1")
        runCurrent()
        assertEquals(42L, log.state.value.startMillis)
        assertEquals(1, log.state.value.seconds.size)
        log.show(null)
        assertEquals(DayLogState(), log.state.value)
    }

    @Test
    fun `while logging, showing another room changes nothing`() = runTest {
        val log = controller()
        log.start("room-1")
        runFor(1)
        log.show("room-2")
        runCurrent()
        assertEquals("room-1", log.state.value.roomId)
        assertTrue(log.state.value.running)
    }

    @Test
    fun `the label model only ever sees one block at a time`() = runTest {
        audio.blockSamples = { rate, _ -> Signals.sine(250.0, 0.1, 1.0, rate) }
        val log = controller()
        log.start("room-1")
        runFor(3)
        assertEquals(3, labeller!!.calls)
        assertEquals(3, audio.blocksEmitted)
    }

    @Test
    fun `clearing deletes the room's saved logs and shows it empty`() = runTest {
        store.open("room-1", 42L).apply { append(LogSecond(42L, flatBands(-50.0), null)) }.close()
        val log = controller()
        log.show("room-1")
        runCurrent()
        log.clear("room-1")
        runCurrent()
        assertEquals(null, store.latest("room-1"))
        assertEquals(DayLogState(roomId = "room-1"), log.state.value)
    }

    @Test
    fun `a running log can't be cleared`() = runTest {
        val log = controller()
        log.start("room-1")
        runFor(2)
        log.clear("room-1")
        runCurrent()
        assertTrue(log.state.value.running)
        assertEquals(2, store.latest("room-1")?.seconds?.size)
    }
}
