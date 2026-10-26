package com.clearroom.app.report

import androidx.annotation.StringRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clearroom.app.R
import com.clearroom.app.acoustics.Cause
import com.clearroom.app.audio.AudioSourceKind
import com.clearroom.app.data.MicSetup
import com.clearroom.app.data.RtTake
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.Session
import com.clearroom.app.ui.formatDateTime
import com.clearroom.app.ui.screens.nameRes
import com.clearroom.app.ui.screens.phraseRes
import com.clearroom.app.ui.screens.rangeText
import com.clearroom.app.ui.screens.rowVerdict
import com.clearroom.app.ui.screens.textRes
import com.clearroom.app.ui.screens.whyRes
import com.clearroom.app.viz.decimals
import com.clearroom.app.viz.rt60Caption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

/** The report's words come from strings.xml: plain words, scores with their ranges. */
@RunWith(AndroidJUnit4::class)
class ReportTextTest {

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val utc = ZoneId.of("UTC")
    private val made = 1_800_000_000_000L

    private fun text(@StringRes id: Int, vararg args: Any) = res.getString(id, *args)

    private val sample = SampleData.session

    /** The sample's readings in the user's own room. */
    private val mine = sample.copy(room = SampleData.room.copy(id = "mine"))

    private fun reportOf(session: Session) = requireNotNull(Report.of(session, made))

    private fun wordsOf(session: Session) = ReportText.of(reportOf(session), res, utc)

    @Test
    fun theHeader_namesTheRoomItsSizeAndWhenItWasMade() {
        val words = wordsOf(mine)
        assertEquals(text(R.string.report_title), words.title)
        assertEquals(text(R.string.report_room_line, mine.room.name, formatDateTime(made, utc)), words.roomLine)
        val rows = res.getQuantityString(R.plurals.room_plan_rows, 5, 5)
        val seats = res.getQuantityString(R.plurals.room_plan_seats_per_row, 6, 6)
        assertEquals(text(R.string.report_size, "9", "7", "3.2", rows, seats), words.sizeLine)
        assertNull(words.sampleNote)
    }

    @Test
    fun theSampleReport_saysItsReadingsAreMadeUp() {
        assertEquals(text(R.string.report_sample), wordsOf(sample).sampleNote)
    }

    @Test
    fun theVerdict_givesTheBackRowsBandRangeAndCauseInPlainWords() {
        val report = reportOf(mine)
        val back = rowVerdict(report.now.clarity, report.now.clarity.rows - 1)
        val words = ReportText.of(report, res, utc)
        assertEquals(text(R.string.results_verdict_back, text(back.band.phraseRes())), words.headline)
        assertEquals(text(R.string.report_verdict, rangeText(res, back.band, back.lowSti, back.highSti), text(back.cause.nameRes())), words.verdict)
        assertEquals(text(back.cause.whyRes()), words.why)
    }

    @Test
    fun theFixes_areNumberedWithTheirGainAndCost() {
        val report = reportOf(mine)
        val words = ReportText.of(report, res, utc)
        assertEquals(report.topFixes.size + 1, words.fixes.size) // and the echo note
        val best = report.topFixes.first()
        assertTrue(words.fixes.first(), words.fixes.first().startsWith("1. " + text(best.preset.nameRes())))
        assertTrue(words.fixes.first(), words.fixes.first().endsWith("(" + text(best.preset.cost.textRes()) + ")"))
    }

    @Test
    fun withNoFixThatHelps_theReportSaysSo() {
        val words = ReportText.of(reportOf(mine).copy(topFixes = emptyList()), res, utc)
        assertEquals(text(R.string.report_fixes_none), words.fixes.first())
    }

    @Test
    fun whenEchoLimitsTheBackRow_theFixesSayWhyALouderVoiceBarelyHelps() {
        val report = reportOf(mine)
        assertEquals(Cause.ECHO, rowVerdict(report.now.clarity, report.now.clarity.rows - 1).cause)
        assertEquals(text(R.string.fixes_ranking_echo_note), ReportText.of(report, res, utc).fixes.last())
    }

    @Test
    fun theConditions_sayHowItWasMeasured() {
        val conditions = wordsOf(mine.withMic(MicSetup(AudioSourceKind.VOICE_RECOGNITION, 48_000))).conditions
        listOf(
            text(R.string.report_mic_voice, "48"),
            text(R.string.report_seats, 3, 30),
            text(R.string.report_fans, "fast"),
            text(R.string.report_students, 0),
            text(R.string.report_voice_measured),
        ).forEach { assertTrue("$it in $conditions", it in conditions) }
    }

    @Test
    fun readingsWithoutAMicNote_sayItWasntRecorded() {
        assertTrue(text(R.string.report_mic_unknown) in wordsOf(mine).conditions)
    }

    @Test
    fun withoutAVoice_theScoresAreMarkedUncalibrated() {
        val words = wordsOf(mine.copy(voice = null))
        assertTrue(text(R.string.report_uncalibrated) in words.notes)
        assertTrue(text(R.string.report_voice_typical) in words.conditions)
    }

    @Test
    fun whileComparing_theNotesAndTitlesCoverBeforeAndAfter() {
        val kept = 1_700_000_000_000L
        val shorter = mine.seats.map { seat -> seat.copy(rtTakes = seat.rtTakes.map { take -> RtTake(take.t60S.map { it?.times(0.7) }) }) }
        val report = reportOf(mine.startRemeasuring(kept).copy(seats = shorter))
        val words = ReportText.of(report, res, utc)
        assertEquals(listOf(text(R.string.report_map_before), text(R.string.report_map_after)), words.mapTitles)
        assertEquals(listOf(text(R.string.compare_echo_before), text(R.string.compare_echo_after)), words.echoTitles)
        val before = requireNotNull(report.before).clarity
        val was = rowVerdict(before, before.rows - 1)
        assertTrue(words.notes.toString(), text(R.string.report_before, formatDateTime(kept, utc), rangeText(res, was.band, was.lowSti, was.highSti)) in words.notes)
    }

    @Test
    fun sampleRates_readAsKilohertz() {
        assertEquals("48", kiloHertz(48_000))
        assertEquals("44.1", kiloHertz(44_100))
    }

    @Test
    fun theEchoCaption_comparesTheSpeechEchoWithTheTarget_andNamesWhatHelps() {
        val echo = reportOf(mine).now.echo
        val caption = rt60Caption(res, echo)
        val over = text(R.string.results_rt_caption_over, decimals(echo.speechRt60S, 1), decimals(echo.targetS, 1))
        assertTrue(caption, caption.startsWith(over))
        assertTrue(caption, caption.endsWith(text(R.string.results_rt_hint_low)))
        assertEquals(caption, ReportText.of(reportOf(mine), res, utc).echoCaption)
    }
}
