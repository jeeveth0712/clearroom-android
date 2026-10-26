package com.clearroom.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clearroom.app.R
import com.clearroom.app.acoustics.ClarityModel
import com.clearroom.app.acoustics.ClarityResult
import com.clearroom.app.acoustics.MissingInput
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.Session
import com.clearroom.app.ui.Evaluation
import com.clearroom.app.ui.theme.ClearRoomTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The stateless Results screen: what it shows for each stage of measuring. */
@RunWith(AndroidJUnit4::class)
class ResultsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun text(@StringRes id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun show(session: Session?, evaluation: Evaluation?) = compose.setContent {
        ClearRoomTheme { ResultsScreen(session, evaluation) }
    }

    private fun showEvaluated(session: Session) = show(session, Evaluation(session, ClarityModel.evaluate(session)))

    private fun assertShown(text: String) {
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private val sample = SampleData.session
    private val sampleClarity = (ClarityModel.evaluate(sample) as ClarityResult.Ready).room
    private val sceneDescription = text(
        R.string.results_3d_description,
        text(StiBand.from(sampleClarity.frontRowSti).labelRes()),
        text(StiBand.from(sampleClarity.backRowSti).labelRes()),
    )

    @Test
    fun withNoRoom_pointsToTheRoomTab() {
        show(session = null, evaluation = null)
        compose.onNodeWithText(text(R.string.results_no_room)).assertIsDisplayed()
    }

    @Test
    fun whileTheEngineIsWorking_saysSo() {
        show(session = sample, evaluation = null)
        compose.onNodeWithText(text(R.string.results_working)).assertIsDisplayed()
    }

    @Test
    fun withNothingMeasured_listsBothMissingMeasurements() {
        val bare = Session(sample.room)
        show(bare, Evaluation(bare, ClarityResult.NeedsMore(setOf(MissingInput.ECHO, MissingInput.NOISE))))
        compose.onNodeWithText(text(R.string.results_missing_echo)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.results_missing_noise)).assertIsDisplayed()
    }

    @Test
    fun withOnlyTheEchoMissing_listsJustThat() {
        val bare = Session(sample.room)
        show(bare, Evaluation(bare, ClarityResult.NeedsMore(setOf(MissingInput.ECHO))))
        compose.onNodeWithText(text(R.string.results_missing_echo)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.results_missing_noise)).assertDoesNotExist()
    }

    @Test
    fun theSampleRoom_showsThe3dRoomWithItsRowsDescribedForTalkBack() {
        showEvaluated(sample)
        compose.onNodeWithText(text(R.string.results_3d_title)).assertIsDisplayed()
        compose.onNode(hasContentDescription(sceneDescription)).assertIsDisplayed()
    }

    @Test
    fun theSampleRoom_namesEveryBandInTheLegendAndExplainsTheGestures() {
        showEvaluated(sample)
        StiBand.entries.forEach { assertShown(text(it.labelRes())) }
        assertShown(text(R.string.results_3d_caption))
    }

    @Test
    fun aMeasuredVoice_hasNoUncalibratedNote() {
        showEvaluated(sample)
        compose.onNodeWithText(text(R.string.results_uncalibrated)).assertDoesNotExist()
    }

    @Test
    fun withoutAVoiceReference_saysTheResultIsUncalibrated() {
        showEvaluated(sample.copy(voice = null))
        assertShown(text(R.string.results_uncalibrated))
    }

    @Test
    fun turningPinchingAndResettingTheRoom_keepsItOnScreen() {
        showEvaluated(sample)
        val scene = compose.onNode(hasContentDescription(sceneDescription))
        scene.performTouchInput { swipeLeft() }
        scene.performTouchInput { swipeUp() }
        scene.performTouchInput {
            pinch(center - Offset(20f, 0f), center - Offset(200f, 0f), center + Offset(20f, 0f), center + Offset(200f, 0f))
        }
        scene.performTouchInput { doubleClick() }
        scene.assertIsDisplayed()
    }
}
