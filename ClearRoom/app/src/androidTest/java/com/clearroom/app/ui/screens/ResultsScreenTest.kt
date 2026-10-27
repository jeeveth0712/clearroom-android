package com.clearroom.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clearroom.app.R
import com.clearroom.app.acoustics.Cause
import com.clearroom.app.acoustics.ClarityModel
import com.clearroom.app.acoustics.ClarityResult
import com.clearroom.app.acoustics.FieldChecks
import com.clearroom.app.acoustics.MissingInput
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.data.RtTake
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.Session
import com.clearroom.app.ui.Evaluation
import com.clearroom.app.ui.ReportState
import com.clearroom.app.ui.UiEvent
import com.clearroom.app.ui.theme.ClearRoomTheme
import com.clearroom.app.viz.BAND_NAMES
import com.clearroom.app.viz.causeCounts
import com.clearroom.app.viz.decimals
import com.clearroom.app.viz.easySeatCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private val events = mutableListOf<UiEvent>()

    private fun show(session: Session?, evaluation: Evaluation?, report: ReportState = ReportState.Idle) = compose.setContent {
        ClearRoomTheme { ResultsScreen(session, evaluation, report, onEvent = { events += it }) }
    }

    private fun showEvaluated(session: Session) {
        shown.value = session
        compose.setContent {
            val current = shown.value
            ClearRoomTheme { ResultsScreen(current, current?.let(Evaluation::of), ReportState.Idle, onEvent = { events += it }) }
        }
    }

    private fun assertShown(text: String) {
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private fun tapView(@StringRes label: Int) {
        compose.onNode(hasText(text(label)) and hasClickAction()).performScrollTo().performClick()
    }

    private fun seatTitle(row: Int, col: Int) = text(R.string.results_seat_title, text(R.string.seat_label, row, col))

    private val sample = SampleData.session
    private val sampleClarity = (ClarityModel.evaluate(sample) as ClarityResult.Ready).room
    private val selectedSeat = sampleClarity.worstSeat(sampleClarity.rows - 1)
    private val sceneDescription = text(
        R.string.results_3d_accessibility,
        text(StiBand.from(sampleClarity.frontRowSti).labelRes()),
        text(StiBand.from(sampleClarity.backRowSti).labelRes()),
        text(R.string.seat_label, selectedSeat.seat.row + 1, selectedSeat.seat.col + 1),
        rangeText(context.resources, selectedSeat.band, selectedSeat.stiLow, selectedSeat.stiHigh),
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

    // --- Report (FR-12) -------------------------------------------------------------------------------

    private fun button(@StringRes label: Int) = compose.onNode(hasText(text(label)) and hasClickAction() and !hasAnyAncestor(isDialog()))

    @Test
    fun theShareButton_asksForTheReport() {
        showEvaluated(sample)
        button(R.string.results_share).performScrollTo().performClick()
        assertEquals(listOf(UiEvent.ShareReport), events)
    }

    @Test
    fun whileTheReportIsMade_theButtonWaitsAndSaysSo() {
        show(sample, Evaluation.of(sample), ReportState.Writing)
        button(R.string.results_share).performScrollTo().assertIsNotEnabled()
        assertShown(text(R.string.results_share_writing))
    }

    @Test
    fun aReportThatFailed_saysWhatToCheck() {
        show(sample, Evaluation.of(sample), ReportState.Failed)
        assertShown(text(R.string.results_share_failed))
    }

    // --- Before and after (FR-13) ---------------------------------------------------------------------

    /** The sample's readings in the user's own room, which can be re-measured. */
    private val mine = sample.copy(room = SampleData.room.copy(id = "mine"))

    /** [session] with every clap ringing 30 % shorter, as if curtains went up. */
    private fun withShorterEcho(session: Session, from: Session = mine) =
        session.copy(seats = from.seats.map { seat -> seat.copy(rtTakes = seat.rtTakes.map { take -> RtTake(take.t60S.map { it?.times(0.7) }) }) })

    private fun dialogButton(@StringRes label: Int) = compose.onNode(hasText(text(label)) and hasClickAction() and hasAnyAncestor(isDialog()))

    @Test
    fun aMeasuredRoom_offersReMeasuring_afterAskingFirst() {
        showEvaluated(mine)
        button(R.string.compare_start).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.compare_start_body)).assertIsDisplayed()
        dialogButton(R.string.compare_cancel).performClick()
        assertTrue(events.isEmpty())
        button(R.string.compare_start).performScrollTo().performClick()
        dialogButton(R.string.compare_start_confirm).performClick()
        assertEquals(listOf(UiEvent.StartRemeasuring), events)
    }

    @Test
    fun theSampleRoom_isNotOfferedForReMeasuring() {
        showEvaluated(sample)
        compose.onNodeWithText(text(R.string.compare_offer_title)).assertDoesNotExist()
    }

    @Test
    fun whileReMeasuring_beforeStaysInView_andSaysWhatToMeasure() {
        showEvaluated(mine.startRemeasuring(1_800_000_000_000L))
        assertShown(text(R.string.results_needs_more))
        assertShown(text(R.string.compare_measure_again))
        assertShown(text(R.string.compare_echo_before))
        compose.onNodeWithText(text(R.string.compare_echo_after)).assertDoesNotExist()
    }

    @Test
    fun afterReMeasuring_beforeAndAfterAreComparedOnOneScale() {
        val after = withShorterEcho(mine.startRemeasuring(1_800_000_000_000L))
        showEvaluated(after)
        val was = (ClarityModel.evaluate(mine) as ClarityResult.Ready).room.let { rowVerdict(it, it.rows - 1) }
        val now = (ClarityModel.evaluate(after) as ClarityResult.Ready).room.let { rowVerdict(it, it.rows - 1) }
        assertShown(text(R.string.fixes_summary_back, text(was.band.phraseRes()), text(now.band.phraseRes())))
        assertShown(text(R.string.compare_before_line, rangeText(context.resources, was.band, was.lowSti, was.highSti)))
        assertShown(text(R.string.compare_after_line, rangeText(context.resources, now.band, now.lowSti, now.highSti)))
        assertShown(text(R.string.compare_echo_after))
        assertShown(text(R.string.compare_caption))
        compose.onNodeWithText(text(R.string.compare_offer_title)).assertDoesNotExist()
    }

    @Test
    fun stoppingTheComparison_asksFirst_andSaysTheEarlierReadingsComeBack() {
        showEvaluated(mine.startRemeasuring(1L)) // nothing new measured yet
        button(R.string.compare_stop).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.compare_stop_body_restore)).assertIsDisplayed()
        dialogButton(R.string.compare_keep).performClick()
        assertTrue(events.isEmpty())
        button(R.string.compare_stop).performScrollTo().performClick()
        dialogButton(R.string.compare_stop).performClick()
        assertEquals(listOf(UiEvent.StopComparing), events)
    }

    @Test
    fun stoppingAfterNewReadings_saysBeforeIsDropped() {
        showEvaluated(withShorterEcho(mine.startRemeasuring(1L)))
        button(R.string.compare_stop).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.compare_stop_body_new)).assertIsDisplayed()
    }

    // --- Field tests (M13) -----------------------------------------------------------------------------

    @Test
    fun theCurtainCheck_asksHowMuchWasHung_thenComparesPredictionAndMeasurement() {
        val after = withShorterEcho(mine.startRemeasuring(1L))
        showEvaluated(after)
        assertShown(text(R.string.field_curtain_hint))
        val slider = compose.onNode(hasContentDescription(text(R.string.field_curtain_hung, 0)))
        slider.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        val check = requireNotNull(FieldChecks.curtainCheck(mine, after, 20.0))
        assertShown(text(R.string.field_curtain_row, decimals(check.beforeS, 2), decimals(check.predictedS, 2), decimals(check.measuredS, 2)))
    }

    @Test
    fun typingAListeningScore_sendsIt_andTheSampleTakesNone() {
        showEvaluated(mine)
        compose.onNode(hasText(text(R.string.field_listening_field, 20))).performScrollTo().performTextInput("14")
        assertTrue(events.toString(), UiEvent.SetListeningScore(SeatId(4, 2), 14) in events)
    }

    @Test
    fun theSampleRoom_hasNoListeningScoreField() {
        showEvaluated(sample)
        compose.onNodeWithText(text(R.string.field_listening_field, 20)).assertDoesNotExist()
    }

    @Test
    fun twoScoredSeats_showWhetherTheAppAgreesWithTheListeners() {
        showEvaluated(mine.withListeningScore(SeatId(0, 2), 18).withListeningScore(SeatId(4, 2), 11))
        assertShown(text(R.string.field_listening_ok))
        showEvaluatedAgain(mine.withListeningScore(SeatId(0, 2), 9).withListeningScore(SeatId(4, 2), 17))
        assertShown(text(R.string.field_listening_off, 1, 1))
    }

    @Test
    fun oneScoredSeat_asksForASecond() {
        showEvaluated(mine.withListeningScore(SeatId(0, 2), 18))
        assertShown(text(R.string.field_listening_needs_two))
    }

    private val shown = mutableStateOf<Session?>(null)

    /** Shows a second session in the same test (setContent can't be called twice). */
    private fun showEvaluatedAgain(session: Session) {
        compose.runOnIdle { shown.value = session }
    }
}
