package com.clearroom.app.viz

import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.daylog.LogPeriod
import com.clearroom.app.daylog.LogStats
import com.clearroom.app.daylog.SoundLabel
import com.clearroom.app.testing.flatBands
import com.clearroom.app.ui.theme.color
import com.clearroom.app.viz.math3d.Argb
import java.time.ZoneId
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** V5 day timeline helpers and the V11 noise landscape builder. */
class DayLogChartsTest {

    private var savedLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun useEnglishNumbers() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private val utc = ZoneId.of("UTC")
    private val nineAm = 9 * 3_600_000L // 1970-01-01 09:00 UTC
    private val minute = 60_000L

    // --- Time ------------------------------------------------------------------------------------------

    @Test
    fun `clock times read as hours and minutes in the phone's zone`() {
        assertEquals("09:00", clockTime(nineAm, utc))
        assertEquals("14:30", clockTime(nineAm, ZoneId.of("Asia/Kolkata"))) // UTC+5:30
    }

    @Test
    fun `durations read as h-mm-ss`() {
        assertEquals("0:00:05", formatDuration(5_000))
        assertEquals("1:05:30", formatDuration(3_930_000))
        assertEquals("0:00:00", formatDuration(-1))
    }

    @Test
    fun `time ticks land on round minutes, a handful at most`() {
        val ticks = timeTicks(nineAm + 20_000, nineAm + 10 * minute)
        assertEquals(listOf(2L, 4, 6, 8, 10).map { nineAm + it * minute }, ticks)
        val day = timeTicks(nineAm, nineAm + 8 * 60 * minute)
        assertTrue(day.size <= 5 && day.all { it % (60 * minute) == 0L }, "$day")
    }

    @Test
    fun `an empty time range has no ticks`() {
        assertFailsWith<IllegalArgumentException> { timeTicks(nineAm, nineAm) }
    }

    @Test
    fun `phone levels become rough dBA with the phone's sensitivity`() {
        assertEquals(-60.0 + LogStats.PHONE_TO_SPL_DB, approxDba(-60.0), 1e-12)
    }

    @Test
    fun `sound labels never borrow a clarity band colour`() {
        val bands = StiBand.entries.map { it.color() }.toSet()
        assertTrue(SoundLabel.entries.none { it.chartColor() in bands })
    }

    // --- V11 --------------------------------------------------------------------------------------------

    private val labels = LandscapeLabels("125 Hz", "1 kHz", "8 kHz", "09:00", "09:02")
    private fun period(at: Int, db: Double) = LogPeriod(nineAm + at * minute, nineAm + (at + 1) * minute, flatBands(db), db, db, 60)

    @Test
    fun `levels scale from just under the quietest to the loudest`() {
        assertEquals(1.0, NoiseLandscapeScene.fraction(-40.0, -60.0, -40.0), 1e-12)
        assertTrue(NoiseLandscapeScene.fraction(-60.0, -60.0, -40.0) in 0.1..0.2) // 3 dB above the floor of the range
        assertEquals(0.0, NoiseLandscapeScene.fraction(-90.0, -60.0, -40.0))
        assertTrue(NoiseLandscapeScene.fraction(-55.0, -60.0, -59.0) < 1.0, "a nearly flat log still spans 10 dB")
    }

    @Test
    fun `there is a bar for every period and band, taller where louder`() {
        val scene = NoiseLandscapeScene.build(listOf(period(0, -60.0), period(1, -40.0), period(2, -50.0)), labels, Argb(1), ::levelRamp)
        assertEquals(3 * 7, scene.solids.size)
        val heights = scene.solids.map { solid -> solid.faces.flatMap { it.points }.maxOf { it.y } }
        assertEquals(NoiseLandscapeScene.HEIGHT_M, heights[7], 1e-9) // period 1 is the loudest
        assertTrue(heights[0] < heights[14] && heights[14] < heights[7])
        assertEquals(listOf("125 Hz", "1 kHz", "8 kHz", "09:00", "09:02"), scene.labels.map { it.text })
        assertEquals(1, scene.ground.size)
    }

    @Test
    fun `with only a couple of periods just the start time is named`() {
        val scene = NoiseLandscapeScene.build(listOf(period(0, -60.0), period(1, -40.0)), labels, Argb(1), ::levelRamp)
        assertEquals(listOf("125 Hz", "1 kHz", "8 kHz", "09:00"), scene.labels.map { it.text })
    }

    @Test
    fun `a landscape needs at least one period`() {
        assertFailsWith<IllegalArgumentException> { NoiseLandscapeScene.build(emptyList(), labels, Argb(1), ::levelRamp) }
    }
}
