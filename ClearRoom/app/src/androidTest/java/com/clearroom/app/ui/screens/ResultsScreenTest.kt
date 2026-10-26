package com.clearroom.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clearroom.app.R
import com.clearroom.app.acoustics.Cause
import com.clearroom.app.acoustics.ClarityModel
import com.clearroom.app.acoustics.ClarityResult
import com.clearroom.app.acoustics.MissingInput
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.Session
import com.clearroom.app.ui.Evaluation
import com.clearroom.app.ui.theme.ClearRoomTheme
import com.clearroom.app.viz.BAND_NAMES
import com.clearroom.app.viz.causeCounts
import com.clearroom.app.viz.decimals
import com.clearroom.app.viz.easySeatCount
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The stateless Results screen: what it shows for each stage of measuring, and how it responds. */
@RunWith(AndroidJUnit4::class)
class ResultsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private fun show(session: Session?, evaluation: Evaluation?) = compose.setContent {
        ClearRoomTheme { ResultsScreen(session, evaluation) }
    }

    private fun showEvaluated(session: Session) = show(session, Evaluation.of(session))

    private fun assertShown(text: String) {
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private fun tapView(@StringRes label: Int) {
        compose.onNode(hasText(text(label)) and hasClickAction()).performScrollTo().performClick()
    }

    private fun seatTitle(row: Int, col: Int) = text(R.string.results_seat_title, text(R.string.seat_label, row, col))

    private val sample = SampleData.session
    private val sampleClarity = (ClarityModel.evaluate(sample) as ClarityResult.Ready).room
    private val sceneDescription = text(
        R.string.results_3d_description,
        text(StiBand.from(sampleClarity.frontRowSti).labelRes()),
        text(StiBand.from(sampleClarity.backRowSti).labelRes()),
    )
    private val mapDescription = context.resources.getQuantityString(R.plurals.results_map_description, sampleClarity.seats.size, easySeatCount(sampleClarity), sampleClarity.seats.size)

    // --- Before there's a result --------------------------------------------------------------------

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

    // --- Verdict ------------------------------------------------------------------------------------

    @Test
    fun theVerdict_namesTheBackRowsBandAndMainCauseInPlainWords() {
        showEvaluated(sample)
        val back = rowVerdict(sampleClarity, sampleClarity.rows - 1)
        compose.onNodeWithText(text(R.string.results_verdict_back, text(back.band.phraseRes()))).assertIsDisplayed()
        compose.onAllNodes(hasText(text(R.string.results_verdict_cause, text(back.cause.nameRes()))))[0].assertIsDisplayed()
        compose.onAllNodes(hasText(text(back.cause.whyRes())))[0].assertIsDisplayed()
    }

    @Test
    fun aSingleRowRoom_isJudgedAsAllSeats() {
        val oneRow = sample.copy(room = sample.room.copy(rows = 1), seats = sample.seats.filter { it.row == 0 })
        val clarity = (ClarityModel.evaluate(oneRow) as ClarityResult.Ready).room
        showEvaluated(oneRow)
        compose.onNodeWithText(text(R.string.results_verdict_all, text(rowVerdict(clarity, 0).band.phraseRes()))).assertIsDisplayed()
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

    // --- 3D room, seat map, cause map ---------------------------------------------------------------

    @Test
    fun theSampleRoom_opensOnThe3dRoomDescribedForTalkBack() {
        showEvaluated(sample)
        compose.onNode(hasContentDescription(sceneDescription)).performScrollTo().assertIsDisplayed()
        assertShown(text(R.string.results_3d_caption))
    }

    @Test
    fun theSeatMap_saysHowManySeatsAreEasyToFollow() {
        showEvaluated(sample)
        tapView(R.string.results_view_map)
        compose.onNode(hasContentDescription(mapDescription)).performScrollTo().assertIsDisplayed()
        assertShown(text(R.string.results_map_caption))
    }

    @Test
    fun theCauseMap_countsSeatsPerCauseAndExplainsEachIcon() {
        showEvaluated(sample)
        tapView(R.string.results_view_causes)
        val counts = causeCounts(sampleClarity)
        val description = text(R.string.results_causes_description, counts.getValue(Cause.ECHO), counts.getValue(Cause.NOISE), counts.getValue(Cause.DISTANCE))
        compose.onNode(hasContentDescription(description)).performScrollTo().assertIsDisplayed()
        assertShown(text(R.string.cause_echo_legend))
        assertShown(text(R.string.cause_noise_legend))
        assertShown(text(R.string.cause_distance_legend))
    }

    @Test
    fun seatDetails_startOnTheWorstBackSeat_andFollowATapOnTheMap() {
        showEvaluated(sample)
        val worst = worstSeat(sampleClarity, sampleClarity.rows - 1).seat
        assertShown(seatTitle(worst.row + 1, worst.col + 1))

        tapView(R.string.results_view_map)
        val map = compose.onNode(hasContentDescription(mapDescription))
        map.performScrollTo()
        // The sample's front-left seat sits about 11 % across and 28 % down the plan.
        map.performTouchInput { click(Offset(width * 0.115f, height * 0.28f)) }
        assertShown(seatTitle(1, 1))
    }

    @Test
    fun turningPinchingAndResettingTheRoom_keepsItOnScreen() {
        showEvaluated(sample)
        val scene = compose.onNode(hasContentDescription(sceneDescription))
        scene.performScrollTo()
        scene.performTouchInput { swipeLeft() }
        scene.performTouchInput {
            pinch(center - Offset(20f, 0f), center - Offset(200f, 0f), center + Offset(20f, 0f), center + Offset(200f, 0f))
        }
        scene.performTouchInput { doubleClick() }
        scene.assertIsDisplayed()
    }

    // --- Charts ---------------------------------------------------------------------------------------

    @Test
    fun everyChartCard_isOnTheScrollOfResults() {
        showEvaluated(sample)
        listOf(
            R.string.results_spectrum_title,
            R.string.results_terrain_title,
            R.string.results_decay_title,
            R.string.results_rt_title,
            R.string.results_waterfall_title,
            R.string.results_mtf_title,
            R.string.results_distance_title,
        ).forEach { assertShown(text(it)) }
    }

    @Test
    fun theDecayChart_switchesBandWhenAChipIsTapped() {
        showEvaluated(sample)
        val curves = requireNotNull(sample.seats.last().lastClap) // the worst back seat's clap
        fun valueText(band: Int) = text(
            R.string.results_decay_value,
            BAND_NAMES[band],
            decimals(requireNotNull(curves.bands[band].fit).t60S, 2),
            text(R.string.results_decay_fit_clean),
        )
        assertShown(valueText(3))
        compose.onNode(hasText("250") and hasClickAction()).performScrollTo().performClick()
        assertShown(valueText(1))
    }

    @Test
    fun roomsWithoutClapCurves_explainHowToGetTheDecayCharts() {
        val noCurves = sample.copy(seats = sample.seats.map { it.copy(lastClap = null) })
        showEvaluated(noCurves)
        compose.onNodeWithText(text(R.string.results_decay_title)).performScrollTo()
        val explanations = compose.onAllNodes(hasText(text(R.string.results_decay_empty))).fetchSemanticsNodes()
        check(explanations.size == 2) { "the decay chart and the waterfall should both explain, found ${explanations.size}" }
    }
}
