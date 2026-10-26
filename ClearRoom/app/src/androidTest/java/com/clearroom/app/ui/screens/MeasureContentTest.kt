package com.clearroom.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clearroom.app.R
import com.clearroom.app.audio.AudioSourceKind
import com.clearroom.app.audio.CaptureProblem
import com.clearroom.app.audio.ClipQuality
import com.clearroom.app.audio.RecordingSummary
import com.clearroom.app.permission.MicAccess
import com.clearroom.app.ui.MeasurePhase
import com.clearroom.app.ui.MeasureUiState
import com.clearroom.app.ui.UiEvent
import com.clearroom.app.ui.theme.ClearRoomTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The stateless Measure content in every permission state and recording phase, with no real mic. */
@RunWith(AndroidJUnit4::class)
class MeasureContentTest {

    @get:Rule
    val compose = createComposeRule()

    private var allowClicks = 0
    private var settingsClicks = 0
    private val events = mutableListOf<UiEvent>()

    private fun text(@StringRes id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun show(access: MicAccess, phase: MeasurePhase = MeasurePhase.Idle) = compose.setContent {
        ClearRoomTheme {
            MeasureContent(
                micAccess = access,
                measure = MeasureUiState(phase),
                liveLevelDb = { -40.0 },
                onAllowMic = { allowClicks++ },
                onOpenSettings = { settingsClicks++ },
                onEvent = { events += it },
            )
        }
    }

    @Test
    fun notAsked_explainsWhy_andAllowButtonRequestsPermission() {
        show(MicAccess.NOT_ASKED)
        compose.onNodeWithText(text(R.string.mic_why)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.mic_allow)).performClick()
        assertEquals(1, allowClicks)
        compose.onNodeWithText(text(R.string.recorder_title)).assertDoesNotExist()
    }

    @Test
    fun deniedOnce_offersToAskAgain() {
        show(MicAccess.DENIED_CAN_ASK_AGAIN)
        compose.onNodeWithText(text(R.string.mic_denied_can_ask_again)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.mic_allow)).performClick()
        assertEquals(1, allowClicks)
    }

    @Test
    fun deniedPermanently_offersSettingsInsteadOfAsking() {
        show(MicAccess.DENIED_PERMANENTLY)
        compose.onNodeWithText(text(R.string.mic_denied_permanently)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.mic_open_settings)).performClick()
        assertEquals(1, settingsClicks)
        compose.onNodeWithText(text(R.string.mic_allow)).assertDoesNotExist()
    }

    @Test
    fun granted_idle_showsRecorder_andStartSendsTheEvent() {
        show(MicAccess.GRANTED)
        compose.onNodeWithText(text(R.string.recorder_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.recorder_start)).performClick()
        assertEquals(listOf<UiEvent>(UiEvent.StartTestRecording), events)
        compose.onNodeWithText(text(R.string.mic_allow)).assertDoesNotExist()
    }

    @Test
    fun countdown_showsSecondsLeft_andCancelSendsTheEvent() {
        show(MicAccess.GRANTED, MeasurePhase.Countdown(secondsLeft = 2))
        compose.onNodeWithText(text(R.string.recorder_countdown, 2)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.recorder_cancel)).performClick()
        assertEquals(listOf<UiEvent>(UiEvent.CancelRecording), events)
    }

    @Test
    fun recording_showsDuration_andCanBeCancelled() {
        show(MicAccess.GRANTED, MeasurePhase.Recording(durationS = 5.0))
        compose.onNodeWithText(text(R.string.recorder_recording, 5.0)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.recorder_cancel)).performClick()
        assertEquals(listOf<UiEvent>(UiEvent.CancelRecording), events)
    }

    @Test
    fun done_showsVerdictAndSummary_inPlainWords() {
        val summary = RecordingSummary(5.0, 48_000, AudioSourceKind.UNPROCESSED, -9.0, -42.0, ClipQuality.CLIPPED)
        show(MicAccess.GRANTED, MeasurePhase.Done(summary))
        compose.onNodeWithText(text(R.string.quality_clipped)).assertIsDisplayed()
        val source = text(R.string.source_unprocessed)
        val summaryText = InstrumentationRegistry.getInstrumentation().targetContext.resources
            .getQuantityString(R.plurals.recorder_summary, 48_000, 5.0, 48_000, source)
        compose.onNodeWithText(summaryText).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.recorder_again)).performClick()
        assertEquals(listOf<UiEvent>(UiEvent.StartTestRecording), events)
    }

    @Test
    fun failed_explainsTheProblem_andOffersARetry() {
        show(MicAccess.GRANTED, MeasurePhase.Failed(CaptureProblem.MIC_UNAVAILABLE))
        compose.onNodeWithText(text(R.string.problem_mic_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.recorder_again)).assertIsDisplayed()
    }
}
