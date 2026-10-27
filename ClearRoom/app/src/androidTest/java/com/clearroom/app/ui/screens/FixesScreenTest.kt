package com.clearroom.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clearroom.app.R
import com.clearroom.app.acoustics.FixSettings
import com.clearroom.app.data.FanState
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.Session
import com.clearroom.app.ui.FixesView
import com.clearroom.app.ui.UiEvent
import com.clearroom.app.ui.theme.ClearRoomTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The stateless Fixes screen: what it shows for each stage, and which events its controls send. */
@RunWith(AndroidJUnit4::class)
class FixesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<UiEvent>()

    private fun text(@StringRes id: Int, vararg args: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private val sample = SampleData.session

    private fun show(session: Session?, settings: FixSettings = FixSettings(), view: FixesView? = session?.let { FixesView.of(it, settings) }) =
        compose.setContent { ClearRoomTheme { FixesScreen(session, settings, view, onEvent = { events += it }) } }

    private fun assertShown(text: String) {
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private fun control(label: String) = compose.onNode(hasContentDescription(label))

    @Test
    fun withNoRoom_pointsToTheRoomTab() {
        show(session = null)
        compose.onNodeWithText(text(R.string.results_no_room)).assertIsDisplayed()
    }

    @Test
    fun beforeTheRoomIsMeasured_asksForTheMeasurementsFirst() {
        show(Session(sample.room))
        compose.onNodeWithText(text(R.string.fixes_needs_measurements)).assertIsDisplayed()
    }

    @Test
    fun asMeasured_showsTheBackRowNowAndInvitesAChange() {
        show(sample)
        compose.onNodeWithText(text(R.string.fixes_summary_title_now)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.fixes_summary_back_now, text(R.string.band_phrase_fair))).assertIsDisplayed()
        assertShown(text(R.string.fixes_summary_hint))
        compose.onNode(hasText(text(R.string.fixes_reset)) and hasClickAction()).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun withCurtains_showsTheBackRowRisingAndOffersAReset() {
        val settings = FixSettings(curtainM2 = 40.0, microphone = true)
        show(sample, settings)
        compose.onNodeWithText(text(R.string.fixes_summary_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.fixes_summary_back, text(R.string.band_phrase_fair), text(R.string.band_phrase_good))).assertIsDisplayed()
        compose.onNode(hasText(text(R.string.fixes_reset)) and hasClickAction()).performScrollTo().assertIsEnabled().performClick()
        assertEquals(UiEvent.ResetFixes, events.last())
    }

    @Test
    fun theMicrophoneSwitch_sendsTheNewSettings() {
        show(sample)
        control(text(R.string.fixes_microphone)).performScrollTo().performClick()
        assertEquals(UiEvent.FixesChanged(FixSettings(microphone = true)), events.last())
    }

    @Test
    fun theFansSwitch_isOffWhenTheFansWereAlreadyOff() {
        val quietFans = sample.copy(seats = sample.seats.map { it.copy(fans = FanState.OFF) })
        show(quietFans)
        control(text(R.string.fixes_fans_off)).performScrollTo().assertIsNotEnabled()
        assertShown(text(R.string.fixes_fans_already_off))
    }

    @Test
    fun theRanking_putsCurtainsFirstAndExplainsWhyVoiceFixesDontHelpHere() {
        show(sample)
        assertShown(text(R.string.fixes_ranking_title))
        assertShown(text(R.string.fixes_ranking_best, text(R.string.fix_curtains), "+0.07"))
        assertShown(text(R.string.fixes_ranking_echo_note))
    }

    @Test
    fun theVoiceChart_saysNoVoiceCanBeatTheSampleRoomsEcho() {
        show(sample)
        assertShown(text(R.string.fixes_voice_title))
        assertShown(text(R.string.fixes_voice_unreachable))
    }

    @Test
    fun the3dRoom_isDescribedForTalkBack() {
        show(sample)
        assertShown(text(R.string.fixes_morph_title))
        compose.onNode(hasContentDescription(text(R.string.fixes_morph_description, text(R.string.band_fair), text(R.string.band_fair)))).performScrollTo().assertIsDisplayed()
    }
}
