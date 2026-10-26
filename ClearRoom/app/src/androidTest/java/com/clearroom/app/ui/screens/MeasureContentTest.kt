package com.clearroom.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clearroom.app.R
import com.clearroom.app.audio.CaptureProblem
import com.clearroom.app.audio.ClipQuality
import com.clearroom.app.data.FanState
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.Session
import com.clearroom.app.measure.MeasureKind
import com.clearroom.app.permission.MicAccess
import com.clearroom.app.ui.MeasurePhase
import com.clearroom.app.ui.MeasureUiState
import com.clearroom.app.ui.UiEvent
import com.clearroom.app.ui.theme.ClearRoomTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The stateless Measure content for every permission state, room situation and phase; no real mic. */
@RunWith(AndroidJUnit4::class)
class MeasureContentTest {

    @get:Rule
    val compose = createComposeRule()

    private var allowClicks = 0
    private var settingsClicks = 0
    private val events = mutableListOf<UiEvent>()

    /** The user's own copy of the sample: seats (0,2), (2,3), (4,2) measured with fans fast, voice recorded. */
    private val ownRoom = SampleData.session.withRoom(SampleData.room.copy(id = "mine"))

    private fun text(@StringRes id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun show(access: MicAccess = MicAccess.GRANTED, session: Session? = ownRoom, measure: MeasureUiState = MeasureUiState()) =
        compose.setContent {
            ClearRoomTheme {
                MeasureContent(
                    micAccess = access,
                    session = session,
                    measure = measure,
                    liveLevelDb = { -40.0 },
                    onAllowMic = { allowClicks++ },
                    onOpenSettings = { settingsClicks++ },
                    onEvent = { events += it },
                )
            }
        }

    private fun node(@StringRes id: Int, vararg args: Any) = compose.onNodeWithText(text(id, *args)).performScrollTo()

    @Test
    fun notAsked_explainsWhy_andAllowButtonRequestsPermission() {
        show(MicAccess.NOT_ASKED)
        node(R.string.mic_why).assertIsDisplayed()
        node(R.string.mic_allow).performClick()
        assertEquals(1, allowClicks)
        compose.onNodeWithText(text(R.string.measure_seat_title)).assertDoesNotExist()
    }

    @Test
    fun deniedOnce_offersToAskAgain() {
        show(MicAccess.DENIED_CAN_ASK_AGAIN)
        node(R.string.mic_denied_can_ask_again).assertIsDisplayed()
        node(R.string.mic_allow).performClick()
        assertEquals(1, allowClicks)
    }

    @Test
    fun deniedPermanently_offersSettingsInsteadOfAsking() {
        show(MicAccess.DENIED_PERMANENTLY)
        node(R.string.mic_denied_permanently).assertIsDisplayed()
        node(R.string.mic_open_settings).performClick()
        assertEquals(1, settingsClicks)
    }

    @Test
    fun withoutARoom_pointsToTheRoomTab() {
        show(session = null)
        node(R.string.measure_no_room).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.measure_seat_title)).assertDoesNotExist()
    }

    @Test
    fun theSampleRoom_isNotMeasuredInto() {
        show(session = SampleData.session)
        node(R.string.measure_sample_room).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.measure_seat_title)).assertDoesNotExist()
    }

    @Test
    fun tappingASeat_selectsIt() {
        show()
        compose.onNode(hasContentDescription(text(R.string.seat_label, 2, 4))).performScrollTo().performClick()
        assertEquals(UiEvent.SelectSeat(SeatId(1, 3)), events.last())
    }

    @Test
    fun noiseCantStart_beforeASeatIsPicked() {
        show(measure = MeasureUiState(selectedSeat = null))
        node(R.string.measure_noise_start, MeasureKind.NOISE.durationS).assertIsNotEnabled()
    }

    @Test
    fun withASeatPicked_noiseButtonStartsANoiseMeasurement() {
        show(measure = MeasureUiState(selectedSeat = SeatId(1, 1)))
        node(R.string.measure_noise_start, MeasureKind.NOISE.durationS).assertIsEnabled().performClick()
        assertEquals(UiEvent.StartMeasurement(MeasureKind.NOISE), events.last())
    }

    @Test
    fun choosingTheFanState_sendsIt() {
        show(measure = MeasureUiState(selectedSeat = SeatId(1, 1)))
        node(R.string.fans_fast).performClick()
        assertEquals(UiEvent.SetFans(FanState.FAST), events.last())
    }

    @Test
    fun voiceButton_startsTheVoiceReference() {
        show()
        node(R.string.measure_voice_start, MeasureKind.VOICE.durationS).performClick()
        assertEquals(UiEvent.StartMeasurement(MeasureKind.VOICE), events.last())
    }

    @Test
    fun aMeasuredSeat_showsItsSevenBandsAndFanState() {
        show(measure = MeasureUiState(selectedSeat = SeatId(2, 3)))
        node(R.string.measure_noise_result, text(R.string.fans_fast)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.seat_label, 3, 4)).assertIsDisplayed()
    }

    @Test
    fun theVoiceReference_isShownWhenRecorded() {
        show()
        node(R.string.measure_voice_result).assertIsDisplayed()
    }

    @Test
    fun countdown_showsSecondsLeft_disablesStarts_andCancelSendsTheEvent() {
        show(measure = MeasureUiState(selectedSeat = SeatId(0, 0), phase = MeasurePhase.Countdown(MeasureKind.NOISE, 2)))
        node(R.string.measure_countdown, 2).assertIsDisplayed()
        node(R.string.measure_noise_start, MeasureKind.NOISE.durationS).assertIsNotEnabled()
        node(R.string.measure_voice_start, MeasureKind.VOICE.durationS).assertIsNotEnabled()
        node(R.string.measure_cancel).performClick()
        assertEquals(UiEvent.CancelRecording, events.last())
    }

    @Test
    fun recording_saysWhatIsBeingRecorded() {
        show(measure = MeasureUiState(phase = MeasurePhase.Recording(MeasureKind.VOICE)))
        node(R.string.measure_recording_voice, MeasureKind.VOICE.durationS).assertIsDisplayed()
    }

    @Test
    fun aSavedNoiseReading_namesTheSeat() {
        show(measure = MeasureUiState(phase = MeasurePhase.Saved(MeasureKind.NOISE, SeatId(2, 3))))
        node(R.string.measure_saved_noise, text(R.string.seat_label, 3, 4)).assertIsDisplayed()
    }

    @Test
    fun aClippedRecording_saysToMoveThePhoneAway() {
        show(measure = MeasureUiState(phase = MeasurePhase.Rejected(MeasureKind.NOISE, ClipQuality.CLIPPED)))
        node(R.string.quality_clipped).assertIsDisplayed()
    }

    @Test
    fun aMicrophoneProblem_isExplained() {
        show(measure = MeasureUiState(phase = MeasurePhase.Failed(CaptureProblem.MIC_UNAVAILABLE)))
        node(R.string.problem_mic_unavailable).assertIsDisplayed()
    }

    @Test
    fun aFailedSave_isExplained() {
        show(measure = MeasureUiState(phase = MeasurePhase.SaveFailed))
        node(R.string.measure_save_failed).assertIsDisplayed()
    }
}
