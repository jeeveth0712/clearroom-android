package com.clearroom.app.acoustics

import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import com.clearroom.app.data.RtTake
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.SeatMeasure
import com.clearroom.app.data.Session
import com.clearroom.app.testing.aRoom
import com.clearroom.app.testing.flatBands
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClarityModelTest {

    private fun ready(session: Session, whatIf: WhatIf = WhatIf()) =
        assertIs<ClarityResult.Ready>(ClarityModel.evaluate(session, whatIf)).room

    private val sample = SampleData.session

    /** A carpeted, curtained room (echo 0.3 s) with fans roaring as loud as the talker: noise is the problem. */
    private val deadNoisyRoom = Session(
        room = aRoom(),
        voice = flatBands(-30.0),
        seats = listOf(SeatMeasure(0, 2, noise = flatBands(-30.0), rtTakes = listOf(RtTake(List(7) { 0.3 })))),
    )

    /** The same dead room in silence: anyone can be understood anywhere. */
    private val deadQuietRoom = deadNoisyRoom.copy(seats = listOf(SeatMeasure(0, 2, noise = flatBands(-90.0), rtTakes = listOf(RtTake(List(7) { 0.3 })))))

    // --- What's needed ---------------------------------------------------------------------------

    @Test
    fun `nothing can be said before at least one clap and one noise reading`() {
        val bare = Session(aRoom())
        assertEquals(ClarityResult.NeedsMore(setOf(MissingInput.ECHO, MissingInput.NOISE)), ClarityModel.evaluate(bare))
        val noiseOnly = bare.copy(seats = listOf(SeatMeasure(0, 0, noise = flatBands(-60.0))))
        assertEquals(ClarityResult.NeedsMore(setOf(MissingInput.ECHO)), ClarityModel.evaluate(noiseOnly))
        val clapsOnly = bare.copy(seats = listOf(SeatMeasure(0, 0, rtTakes = listOf(RtTake(List(7) { 1.0 })))))
        assertEquals(ClarityResult.NeedsMore(setOf(MissingInput.NOISE)), ClarityModel.evaluate(clapsOnly))
    }

    // --- The sample room (M6 done check) -------------------------------------------------------

    @Test
    fun `in the sample room the back row is less clear than the front row`() {
        val room = ready(sample)
        assertTrue(room.backRowSti < room.frontRowSti, "front ${room.frontRowSti}, back ${room.backRowSti}")
        assertEquals(StiBand.FAIR, StiBand.from(room.backRowSti), "a bare tiled classroom with 1.1 s of echo")
    }

    @Test
    fun `every seat gets a score between 0 and 1, inside its own uncertainty range`() {
        val room = ready(sample)
        assertEquals(30, room.seats.size)
        room.seats.forEach { seat ->
            assertTrue(seat.sti in 0.0..1.0 && seat.stiLow <= seat.sti && seat.sti <= seat.stiHigh, "$seat")
        }
    }

    @Test
    fun `the room's echo time is the mean over all claps at all seats`() {
        val expected = Rt60Summary.of(sample.seats.flatMap { it.rtTakes })!!.bands.map { it.meanS }
        ready(sample).rt60S.indices.forEach { assertEquals(expected[it], ready(sample).rt60S[it], absoluteTolerance = 1e-9) }
    }

    @Test
    fun `the critical distance uses the echo time at speech pitches`() {
        val room = ready(sample)
        val speechT60 = (2..4).map { room.rt60S[it] }.average()
        assertEquals(0.057 * sqrt(2 * sample.room.volumeM3 / speechT60), room.criticalDistanceM, absoluteTolerance = 1e-9)
    }

    @Test
    fun `seats with their own noise reading are marked as measured`() {
        val room = ready(sample)
        assertEquals(setOf(SeatId(0, 2), SeatId(2, 3), SeatId(4, 2)), room.seats.filter { it.isMeasured }.map { it.seat }.toSet())
    }

    // --- Limiting cause (FR-7) -------------------------------------------------------------------

    @Test
    fun `a bare tiled classroom is limited by echo everywhere`() {
        assertTrue(ready(sample).seats.all { it.cause == Cause.ECHO })
    }

    @Test
    fun `a dead room with loud fans is limited by noise`() {
        val room = ready(deadNoisyRoom)
        assertTrue(room.seats.all { it.cause == Cause.NOISE }, room.seats.groupingBy { it.cause }.eachCount().toString())
    }

    @Test
    fun `the cause is whichever single change helps most, ties going to echo then noise`() {
        assertEquals(Cause.ECHO, ClarityModel.limitingCause(gainIfQuiet = 0.1, gainIfDry = 0.3, gainIfClose = 0.2))
        assertEquals(Cause.NOISE, ClarityModel.limitingCause(gainIfQuiet = 0.4, gainIfDry = 0.3, gainIfClose = 0.2))
        assertEquals(Cause.DISTANCE, ClarityModel.limitingCause(gainIfQuiet = 0.1, gainIfDry = 0.1, gainIfClose = 0.2))
        assertEquals(Cause.ECHO, ClarityModel.limitingCause(gainIfQuiet = 0.2, gainIfDry = 0.2, gainIfClose = 0.2))
        assertEquals(Cause.NOISE, ClarityModel.limitingCause(gainIfQuiet = 0.2, gainIfDry = 0.1, gainIfClose = 0.2))
    }

    // --- Voice reference (FR-5, NFR-5) ---------------------------------------------------------

    @Test
    fun `without a voice reference the 60 dBA fallback is used and the result is flagged uncalibrated`() {
        val room = ready(sample.copy(voice = null))
        assertEquals(false, room.voiceMeasured)
        assertEquals(true, ready(sample).voiceMeasured)
    }

    // --- What-if (FR-8) --------------------------------------------------------------------------

    @Test
    fun `20 m² of curtains shortens the echo in every band and lifts the back row`() {
        val before = ready(sample)
        val after = ready(sample, WhatIf(absorbers = listOf(Absorber(20.0, Absorbers.HEAVY_CURTAIN_ALPHA))))
        after.rt60S.indices.forEach { assertTrue(after.rt60S[it] < before.rt60S[it], "band $it") }
        assertTrue(after.backRowSti > before.backRowSti + 0.03, "${before.backRowSti} -> ${after.backRowSti}")
    }

    @Test
    fun `seated students shorten the echo`() {
        val before = ready(sample)
        val after = ready(sample, WhatIf(extraStudents = 30))
        assertTrue(after.rt60S[3] < before.rt60S[3])
    }

    @Test
    fun `louder noise never helps any seat and quieter noise never hurts`() {
        val base = ready(deadNoisyRoom).seats
        val louder = ready(deadNoisyRoom, WhatIf(noiseChangeDb = 6.0)).seats
        val quieter = ready(deadNoisyRoom, WhatIf(noiseChangeDb = -6.0)).seats
        base.indices.forEach {
            assertTrue(louder[it].sti <= base[it].sti && quieter[it].sti >= base[it].sti, "seat ${base[it].seat}")
        }
    }

    @Test
    fun `a louder voice or a microphone never lowers any seat`() {
        listOf(WhatIf(voiceChangeDb = 6.0), WhatIf(micGainDb = 10.0)).forEach { whatIf ->
            val base = ready(deadNoisyRoom).seats
            val changed = ready(deadNoisyRoom, whatIf).seats
            base.indices.forEach { assertTrue(changed[it].sti >= base[it].sti, "$whatIf, seat ${base[it].seat}") }
        }
    }

    @Test
    fun `moving the talker to the back helps the back row and costs the front`() {
        val base = ready(deadNoisyRoom)
        val moved = ready(deadNoisyRoom, WhatIf(speaker = FloorPoint(3.5, 8.5)))
        assertTrue(moved.backRowSti > base.backRowSti)
        assertTrue(moved.frontRowSti < base.frontRowSti)
    }

    // --- Required voice (FR-11) ----------------------------------------------------------------

    @Test
    fun `in an echo-limited room no voice is loud enough, so the answer is not to shout`() {
        assertTrue(ClarityModel.requiredVoice(sample)!!.all { it.need == VoiceNeed.Unreachable })
    }

    @Test
    fun `in a noisy dead room the back needs a raised voice, and that voice really reaches the target`() {
        val back = ClarityModel.requiredVoice(deadNoisyRoom)!!.last()
        val change = assertIs<VoiceNeed.Required>(back.need).changeDb
        assertTrue(change > 0.0, "the back row should need a louder voice, got $change dB")
        val withThatVoice = ready(deadNoisyRoom, WhatIf(voiceChangeDb = change))
        assertTrue(withThatVoice.backRowSti >= ClarityModel.TARGET_STI - 0.005, "STI ${withThatVoice.backRowSti} at +$change dB")
        val slightlyQuieter = ready(deadNoisyRoom, WhatIf(voiceChangeDb = change - 0.5))
        assertTrue(slightlyQuieter.backRowSti < ClarityModel.TARGET_STI, "the search should find the smallest change")
    }

    @Test
    fun `further back needs a louder voice`() {
        val needs = ClarityModel.requiredVoice(deadNoisyRoom)!!.map { (it.need as VoiceNeed.Required).changeDb }
        assertTrue(needs.zipWithNext().all { (front, back) -> back >= front }, "$needs")
    }

    @Test
    fun `in a quiet dead room even a soft voice is enough`() {
        ClarityModel.requiredVoice(deadQuietRoom)!!.forEach {
            assertEquals(VoiceNeed.Required(ClarityModel.VOICE_SEARCH_MIN_DB), it.need)
        }
    }

    @Test
    fun `no voice advice before there is enough to measure`() {
        assertEquals(null, ClarityModel.requiredVoice(Session(aRoom())))
    }

    // --- What the charts need (V13, V14) ------------------------------------------------------------

    @Test
    fun `results carry the speaker spot, the voice used and the room constant per band`() {
        val room = ready(sample)
        assertEquals(SeatLayout.speaker(sample.room), room.speaker)
        assertEquals(assertNotNull(sample.voice).valuesDb, room.voiceAt1mDb)
        assertEquals(7, room.roomConstantM2.size)
        assertTrue(room.roomConstantM2.all { it > 0.0 })
    }

    @Test
    fun `a louder what-if voice shows in the voice the results assume`() {
        assertEquals(assertNotNull(sample.voice).valuesDb.map { it + 6.0 }, ready(sample, WhatIf(voiceChangeDb = 6.0)).voiceAt1mDb)
    }

    // --- Floor grid (V1, V9) ------------------------------------------------------------------------

    @Test
    fun `the floor grid tiles the room in about 25 cm cells`() {
        val grid = assertNotNull(ClarityModel.floorGrid(sample))
        assertEquals(28, grid.columns) // 7 m / 0.25 m
        assertEquals(36, grid.rows) // 9 m / 0.25 m
        assertEquals(29 * 37, grid.sti.size)
        assertEquals(FloorPoint(7.0, 9.0), grid.cornerPoint(28, 36))
        assertTrue(grid.sti.all { it in 0.0..1.0 })
    }

    @Test
    fun `clarity on the floor is best near the speaker and worst at the back`() {
        val grid = assertNotNull(ClarityModel.floorGrid(sample))
        val nearSpeaker = grid.corner(14, 6) // 3.5 m across, 1.5 m from the front wall
        val backCorner = grid.corner(0, 36)
        assertTrue(nearSpeaker > backCorner, "near $nearSpeaker, back $backCorner")
    }

    @Test
    fun `a grid point at a seat scores what the seat scores`() {
        val room = aRoom(lengthM = 9.0, widthM = 7.0, rows = 1, cols = 1) // the single seat sits at (3.5, 2.5)
        val session = Session(room, voice = flatBands(-30.0), seats = listOf(SeatMeasure(0, 0, noise = flatBands(-60.0), rtTakes = listOf(RtTake(List(7) { 0.8 })))))
        val grid = assertNotNull(ClarityModel.floorGrid(session, stepM = 0.5))
        assertEquals(ready(session).at(SeatId(0, 0)).sti, grid.corner(7, 5), 1e-9)
    }

    @Test
    fun `there is no floor grid before there's enough to say`() {
        assertEquals(null, ClarityModel.floorGrid(Session(aRoom())))
    }

    @Test
    fun `the grid step must be positive`() {
        assertFailsWith<IllegalArgumentException> { ClarityModel.floorGrid(sample, stepM = 0.0) }
    }

    @Test
    fun `a grid cell scores the mean of its corners`() {
        val grid = ClarityGrid(widthM = 2.0, lengthM = 1.0, columns = 2, rows = 1, sti = listOf(0.2, 0.4, 0.6, 0.4, 0.6, 0.8))
        assertEquals(0.4, grid.cell(0, 0), 1e-12)
        assertEquals(0.6, grid.cell(1, 0), 1e-12)
        assertEquals(1.0, grid.cellWidthM)
        assertEquals(0.8, grid.corner(2, 1))
    }

    @Test
    fun `a grid needs a cell and the right number of corners`() {
        assertFailsWith<IllegalArgumentException> { ClarityGrid(1.0, 1.0, 0, 1, emptyList()) }
        assertFailsWith<IllegalArgumentException> { ClarityGrid(1.0, 1.0, 1, 1, listOf(0.5, 0.5, 0.5)) }
    }

    @Test
    fun `a row's worst seat is its least clear one`() {
        val room = ready(sample)
        val worst = room.worstSeat(4)
        assertEquals(4, worst.seat.row)
        assertEquals(room.seats.filter { it.seat.row == 4 }.minOf { it.sti }, worst.sti)
        assertEquals(worst.sti, room.backRowSti)
    }
}
