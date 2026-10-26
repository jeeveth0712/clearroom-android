package com.clearroom.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clearroom.app.R
import com.clearroom.app.data.BandLevels
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.Session
import com.clearroom.app.daylog.DayLogState
import com.clearroom.app.daylog.LogSecond
import com.clearroom.app.daylog.SoundLabel
import com.clearroom.app.permission.MicAccess
import com.clearroom.app.ui.DayLogView
import com.clearroom.app.ui.UiEvent
import com.clearroom.app.ui.theme.ClearRoomTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The stateless Log tab: what it shows before, during and after a day log, and the events it sends. */
@RunWith(AndroidJUnit4::class)
class LogScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<UiEvent>()

    private fun text(@StringRes id: Int, vararg args: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    /** The sample room's measurements, as a real room the user saved (the sample itself can't be logged). */
    private val room = SampleData.session.let { it.copy(room = it.room.copy(id = "room-1")) }

    private fun show(
        session: Session? = room,
        log: DayLogState = DayLogState(roomId = room.room.id),
        micAccess: MicAccess = MicAccess.GRANTED,
        measureBusy: Boolean = false,
    ) {
        val view = session?.let { DayLogView.of(log, it, SeatId(4, 2)) }
        compose.setContent {
            ClearRoomTheme { LogContent(micAccess, session, measureBusy, view, { -50.0 }, {}, {}, onEvent = { events += it }) }
        }
    }

    private fun assertShown(text: String) {
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    /** Ten minutes at the back seat: steady fan noise with a loud horn at 2 minutes in. */
    private fun tenMinutes(): List<LogSecond> = List(600) { s ->
        val horn = s in 120..124
        LogSecond(1_800_000_000_000L + s * 1_000L, BandLevels(List(7) { if (horn) -35.0 else -62.0 }), if (horn) SoundLabel.HORN else SoundLabel.FAN)
    }

    @Test
    fun withNoRoom_pointsToTheRoomTab() {
        show(session = null)
        compose.onNodeWithText(text(R.string.log_no_room)).assertIsDisplayed()
    }

    @Test
    fun theSampleRoom_isForExploringOnly() {
        show(session = SampleData.session, log = DayLogState())
        compose.onNodeWithText(text(R.string.log_sample_room)).assertIsDisplayed()
    }

    @Test
    fun beforeTheMicIsAllowed_explainsWhyItsNeeded() {
        show(micAccess = MicAccess.NOT_ASKED)
        compose.onNodeWithText(text(R.string.mic_why)).assertIsDisplayed()
    }

    @Test
    fun startingTheLog_sendsStartLog_andNamesTheSeat() {
        show()
        assertShown(text(R.string.log_spot, text(R.string.seat_label, 5, 3)))
        compose.onNode(hasText(text(R.string.log_start)) and hasClickAction()).performScrollTo().performClick()
        assertEquals(UiEvent.StartLog, events.last())
        assertShown(text(R.string.log_empty))
    }

    @Test
    fun whileMeasuring_theLogCantStart() {
        show(measureBusy = true)
        compose.onNode(hasText(text(R.string.log_start)) and hasClickAction()).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun whileLogging_theBannerSaysLevelsNotAudio_andStopSendsStopLog() {
        show(log = DayLogState(roomId = room.room.id, running = true, startMillis = 1_800_000_000_000L, seconds = tenMinutes().take(65)))
        compose.onNodeWithText(text(R.string.log_banner)).assertIsDisplayed()
        assertShown(text(R.string.log_running, "0:01:05"))
        compose.onNode(hasText(text(R.string.log_stop)) and hasClickAction()).performScrollTo().performClick()
        assertEquals(UiEvent.StopLog, events.last())
    }

    @Test
    fun aFinishedLog_showsItsSummaryChartsAndEvents() {
        show(log = DayLogState(roomId = room.room.id, startMillis = 1_800_000_000_000L, seconds = tenMinutes()))
        listOf(R.string.log_summary_title, R.string.log_timeline_title, R.string.log_landscape_title, R.string.log_events_title).forEach { assertShown(text(it)) }
        // The horn is named in the summary's "Loudest" line, the event list and the timeline's legend.
        val hornMentions = compose.onAllNodes(hasText(text(R.string.sound_horn), substring = true)).fetchSemanticsNodes().size
        assertTrue("horn mentioned $hornMentions times", hornMentions >= 3)
    }

    @Test
    fun withoutTheLabelModel_saysOnlyLevelsWereLogged() {
        show(log = DayLogState(roomId = room.room.id, labelsAvailable = false))
        assertShown(text(R.string.log_no_labels))
    }

    @Test
    fun clearingLogs_asksFirst_andOnlyClearsOnConfirm() {
        show(log = DayLogState(roomId = room.room.id, startMillis = 1_800_000_000_000L, seconds = tenMinutes().take(30)))
        compose.onNode(hasText(text(R.string.log_clear)) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.log_clear_body)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.log_clear_keep)).performClick()
        assertTrue(events.none { it == UiEvent.ClearLogs })
        compose.onNode(hasText(text(R.string.log_clear)) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.log_clear_confirm)).performClick()
        assertEquals(UiEvent.ClearLogs, events.last())
    }

    @Test
    fun whileLogging_logsCantBeCleared() {
        show(log = DayLogState(roomId = room.room.id, running = true, startMillis = 1_800_000_000_000L, seconds = tenMinutes().take(30)))
        compose.onNodeWithText(text(R.string.log_clear)).assertDoesNotExist()
    }
}
