package com.clearroom.app.daylog

import com.clearroom.app.acoustics.ClarityModel
import com.clearroom.app.acoustics.ClarityResult
import com.clearroom.app.data.BandLevels
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.Session
import com.clearroom.app.testing.aRoom
import com.clearroom.app.testing.flatBands
import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogStatsTest {

    private val t0 = 1_800_000_000_000L
    private fun second(at: Int, db: Double = -60.0, label: SoundLabel? = null) = LogSecond(t0 + at * 1_000L, flatBands(db), label)

    // --- Averages --------------------------------------------------------------------------------------

    @Test
    fun `levels average as energy, so a loud second counts for more than a plain mean`() {
        assertEquals(-60.0, LogStats.energyMeanDb(listOf(-60.0, -60.0)), 1e-9)
        assertEquals(-50.0 + 10 * log10(0.5 + 0.05), LogStats.energyMeanDb(listOf(-50.0, -60.0)), 1e-9)
        assertTrue(LogStats.energyMeanDb(listOf(-40.0, -80.0)) > -60.0)
    }

    @Test
    fun `L90 is the level 90 percent of the seconds stay above`() {
        val levels = (1..100).map { -it.toDouble() } // -1 … -100
        assertEquals(-91.0, LogStats.l90Db(levels))
        assertEquals(-5.0, LogStats.l90Db(listOf(-5.0)))
    }

    @Test
    fun `averaging nothing is refused`() {
        assertFailsWith<IllegalArgumentException> { LogStats.energyMeanDb(emptyList()) }
        assertFailsWith<IllegalArgumentException> { LogStats.l90Db(emptyList()) }
    }

    // --- Periods ---------------------------------------------------------------------------------------

    @Test
    fun `periods are a minute for a test log, five for a lesson, an hour for a day`() {
        assertEquals(60_000L, LogStats.periodMillisFor(10 * 60_000L))
        assertEquals(5 * 60_000L, LogStats.periodMillisFor(90 * 60_000L))
        assertEquals(60 * 60_000L, LogStats.periodMillisFor(8 * 60 * 60_000L))
    }

    @Test
    fun `seconds group into periods with their own average and background`() {
        val seconds = (0 until 120).map { second(it, if (it < 60) -60.0 else -40.0) }
        val periods = LogStats.periods(seconds, periodMillis = 60_000L)
        assertEquals(2, periods.size)
        assertEquals(-60.0, periods[0].bands[3], 1e-9)
        assertEquals(-40.0, periods[1].bands[3], 1e-9)
        assertEquals(t0 + 60_000L, periods[1].startMillis)
        assertEquals(60, periods[1].seconds)
    }

    @Test
    fun `a stretch with no seconds leaves no empty period`() {
        val seconds = listOf(second(0), second(1), second(200)) // stopped for over 3 minutes
        assertEquals(2, LogStats.periods(seconds, periodMillis = 60_000L).size)
    }

    @Test
    fun `the noise line steps every 10 s for a short log`() {
        val line = LogStats.noiseLine((0 until 30).map { second(it) })
        assertEquals(3, line.size)
        assertEquals(t0 + 20_000L, line.last().timeMillis)
    }

    @Test
    fun `long logs use one-minute noise points`() {
        assertEquals(10_000L, LogStats.noiseStepMillisFor(30 * 60_000L))
        assertEquals(60_000L, LogStats.noiseStepMillisFor(31 * 60_000L))
    }

    @Test
    fun `day verdict and displayed range come from measured periods only`() {
        val periods = (0 until 4).map { periodLike(flatBands(-60.0)).copy(startMillis = t0 + it * 60_000L) }
        val clear = LogSummary(240_000L, -60.0, -60.0, periods, listOf(0.65, 0.68, 0.61, 0.58), emptyList(), emptyList())
        assertEquals(DayVerdict.MOSTLY_CLEAR, clear.dayVerdict)
        assertEquals(0.58, assertNotNull(clear.backRowRange).start)
        assertEquals(0.68, assertNotNull(clear.backRowRange).endInclusive)
        assertEquals(1, clear.periodsBelowTarget)
        assertEquals(null, clear.copy(backRowSti = List(4) { null }).dayVerdict)
    }

    // --- Events ----------------------------------------------------------------------------------------

    @Test
    fun `seconds of the same sound merge into one event, bridging short gaps`() {
        val seconds = listOf(
            second(0, -50.0, SoundLabel.HORN), second(1, -40.0, SoundLabel.HORN), second(2), second(3, -45.0, SoundLabel.HORN), // a short gap
            second(20, -50.0, SoundLabel.HORN), // more than 10 s later: a new event
        )
        val events = LogStats.events(seconds)
        assertEquals(2, events.size)
        assertEquals(NoiseEvent(t0, t0 + 4_000L, SoundLabel.HORN, seconds[1].dba), events[0])
    }

    @Test
    fun `other sounds never make an event, so they don't chop up a fan`() {
        val seconds = listOf(second(0, label = SoundLabel.FAN), second(1, label = SoundLabel.OTHER), second(2, label = SoundLabel.FAN))
        val events = LogStats.events(seconds)
        assertEquals(listOf(NoiseEvent(t0, t0 + 3_000L, SoundLabel.FAN, seconds[0].dba)), events)
    }

    @Test
    fun `a different sound starts a new event, and quiet is never an event`() {
        val events = LogStats.events(listOf(second(0, label = SoundLabel.FAN), second(1, label = SoundLabel.TRAFFIC), second(2, label = SoundLabel.QUIET), second(3)))
        assertEquals(listOf(SoundLabel.FAN, SoundLabel.TRAFFIC), events.map { it.label })
    }

    // --- FR-10 clarity through the day -----------------------------------------------------------------

    private val sample = SampleData.session
    private val spot = SeatId(4, 2) // a measured seat: its snapshot is its own reading

    private fun periodLike(noise: BandLevels) = LogPeriod(t0, t0 + 60_000L, noise, 0.0, 0.0, 60)

    @Test
    fun `a period as noisy as the snapshot gives the room's measured back-row score`() {
        val measured = (ClarityModel.evaluate(sample) as ClarityResult.Ready).room.backRowSti
        val same = LogStats.backRowSti(sample, spot, listOf(periodLike(assertNotNull(sample.measureAt(spot)?.noise))))
        assertEquals(measured, assertNotNull(same.single()), 1e-9)
    }

    @Test
    fun `a noisier period lowers the back row's score`() {
        val noise = assertNotNull(sample.measureAt(spot)?.noise)
        val (quiet, loud) = LogStats.backRowSti(sample, spot, listOf(periodLike(noise), periodLike(BandLevels(noise.valuesDb.map { it + 25 }))))
        assertTrue(assertNotNull(loud) < assertNotNull(quiet))
    }

    @Test
    fun `without any noise reading there's no score to give`() {
        assertEquals(listOf(null), LogStats.backRowSti(Session(aRoom()), SeatId(0, 0), listOf(periodLike(flatBands(-50.0)))))
    }

    @Test
    fun `the log spot is the picked seat, or the middle of the back row`() {
        assertEquals(SeatId(1, 1), LogStats.logSpot(sample, SeatId(1, 1)))
        assertEquals(SeatId(4, 3), LogStats.logSpot(sample, null)) // 5 rows × 6 seats
        assertEquals(SeatId(4, 3), LogStats.logSpot(sample, SeatId(9, 9))) // a seat outside the grid is ignored
    }

    // --- Summary ---------------------------------------------------------------------------------------

    @Test
    fun `an empty log has no summary`() {
        assertNull(LogSummary.of(emptyList(), sample, spot))
    }

    @Test
    fun `the summary gathers the log's levels, periods, events and clarity`() {
        val seconds = (0 until 180).map { second(it, if (it in 100..110) -30.0 else -60.0, if (it in 100..110) SoundLabel.HORN else null) }
        val summary = assertNotNull(LogSummary.of(seconds, sample, spot))
        assertEquals(180_000L, summary.spanMillis)
        assertEquals(3, summary.periods.size) // 3 minutes of 1-minute periods
        assertEquals(3, summary.backRowSti.size)
        assertEquals(SoundLabel.HORN, assertNotNull(summary.loudestEvent).label)
        assertTrue(summary.leqDba > summary.l90Dba)
        assertEquals(summary.periods[summary.backRowSti.indices.maxBy { summary.backRowSti[it]!! }].startMillis, summary.bestPeriodStartMillis)
        assertEquals(3, summary.periodsWithClarity)
        assertEquals(summary.backRowSti.count { it!! < 0.6 }, summary.periodsBelowTarget)
    }

    @Test
    fun `without a room the summary has levels but no clarity`() {
        val summary = assertNotNull(LogSummary.of(listOf(second(0)), session = null, logSpot = null))
        assertEquals(listOf(null), summary.backRowSti)
        assertNull(summary.bestPeriodStartMillis)
        assertEquals(0, summary.periodsWithClarity)
    }
}
