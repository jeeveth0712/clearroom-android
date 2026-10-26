package com.clearroom.app.report

import com.clearroom.app.acoustics.ClarityModel
import com.clearroom.app.acoustics.ClarityResult
import com.clearroom.app.acoustics.Fixes
import com.clearroom.app.audio.AudioSourceKind
import com.clearroom.app.data.FanState
import com.clearroom.app.data.MicSetup
import com.clearroom.app.data.RtTake
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SeatMeasure
import com.clearroom.app.data.Session
import com.clearroom.app.testing.aRoom
import com.clearroom.app.testing.flatBands
import com.clearroom.app.viz.Rt60ChartModel
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What goes into the one-page report (FR-12), with before and after (FR-13). */
class ReportTest {

    private val sample = SampleData.session

    /** The sample's readings as the user's own room, so it can be re-measured. */
    private val mine = sample.copy(room = SampleData.room.copy(id = "mine"))

    @Test
    fun `a room without a result has no report`() {
        assertNull(Report.of(Session(aRoom()), 1L))
    }

    @Test
    fun `the report holds the room's results now and when it was made`() {
        val report = assertNotNull(Report.of(sample, 42L))
        assertEquals(42L, report.createdAtMillis)
        assertEquals(ClarityModel.evaluate(sample), ClarityResult.Ready(report.now.clarity))
        assertEquals(ClarityModel.floorGrid(sample), report.now.grid)
        assertEquals(Rt60ChartModel.of(sample), report.now.echo)
        assertNull(report.before)
    }

    @Test
    fun `only the sample room's report is marked as the sample`() {
        assertTrue(assertNotNull(Report.of(sample, 1L)).isSample)
        assertTrue(!assertNotNull(Report.of(mine, 1L)).isSample)
    }

    @Test
    fun `the report recommends up to three changes that noticeably help, best first`() {
        val fixes = assertNotNull(Report.of(sample, 1L)).topFixes
        assertTrue(fixes.size in 1..Report.TOP_FIXES, "$fixes")
        assertTrue(fixes.all { it.gain >= Report.MIN_GAIN })
        val helping = Fixes.rank(sample).count { it.gain >= Report.MIN_GAIN }
        assertEquals(minOf(helping, Report.TOP_FIXES), fixes.size, "nothing that helps is left out")
        assertEquals(fixes.sortedByDescending { it.gain }, fixes)
        assertEquals(Fixes.rank(sample).first(), fixes.first())
    }

    @Test
    fun `conditions count what was measured, and say how`() {
        val mic = MicSetup(AudioSourceKind.VOICE_RECOGNITION, 48_000)
        val session = Session(
            aRoom(rows = 2, cols = 2, students = 25),
            seats = listOf(
                SeatMeasure(0, 0, noise = flatBands(-60.0), fans = FanState.FAST, rtTakes = listOf(RtTake(List(7) { 1.0 }), RtTake(List(7) { 1.1 }))),
                SeatMeasure(1, 0, rtTakes = listOf(RtTake(List(7) { 0.9 }))),
                SeatMeasure(1, 1, noise = flatBands(-62.0), fans = FanState.OFF),
            ),
        ).withMic(mic)
        val conditions = assertNotNull(Report.of(session, 1L)).conditions
        assertEquals(mic, conditions.mic)
        assertEquals(3, conditions.measuredSeats)
        assertEquals(4, conditions.seatCount)
        assertEquals(3, conditions.claps)
        assertEquals(2, conditions.noiseReadings)
        assertEquals(listOf(FanState.OFF, FanState.FAST), conditions.fans)
        assertEquals(25, conditions.studentsUsually)
        assertEquals(false, conditions.voiceMeasured)
        assertEquals(assertNotNull(Rt60ChartModel.of(session)).speechRt60S, conditions.speechEchoS)
    }

    @Test
    fun `while comparing, the report has both sides on one echo scale`() {
        // After the change every clap rings 30 % shorter, as if curtains went up.
        val shorter = mine.seats.map { seat -> seat.copy(rtTakes = seat.rtTakes.map { take -> RtTake(take.t60S.map { it?.times(0.7) }) }) }
        val after = mine.startRemeasuring(5L).copy(seats = shorter)
        val report = assertNotNull(Report.of(after, 9L))
        val before = assertNotNull(report.before)
        assertEquals(assertNotNull(ReportSide.of(mine)), before)
        assertTrue(report.now.clarity.backRowSti > before.clarity.backRowSti)
        assertEquals(before.echo.axisTopS, report.echoAxisTopS, "the longer echo sets the shared scale")
        assertTrue(report.echoAxisTopS > report.now.echo.axisTopS)
    }

    @Test
    fun `while the after-readings are still missing there is no report yet`() {
        assertNull(Report.of(mine.startRemeasuring(5L), 1L))
    }

    @Test
    fun `report files are named by when they were made, in the phone's zone`() {
        val made = 1_800_000_000_000L // 15 Jan 2027, 08:00 UTC
        assertEquals("ClearRoom-report-20270115-0800.pdf", PdfReportWriter.fileName(made, ZoneId.of("UTC")))
        assertEquals("ClearRoom-report-20270115-1330.pdf", PdfReportWriter.fileName(made, ZoneId.of("Asia/Kolkata")))
    }
}
