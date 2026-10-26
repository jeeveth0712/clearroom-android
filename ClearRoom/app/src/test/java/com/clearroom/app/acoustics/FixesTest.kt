package com.clearroom.app.acoustics

import com.clearroom.app.data.FanState
import com.clearroom.app.data.RtTake
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SeatMeasure
import com.clearroom.app.data.Session
import com.clearroom.app.testing.aRoom
import com.clearroom.app.testing.flatBands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FixesTest {

    private val sample = SampleData.session
    private val room = sample.room

    /** A dead room (0.4 s) with fans roaring: noise limits it, so fans off and a mic help most. */
    private val noisy = Session(
        room = aRoom(),
        voice = flatBands(-40.0),
        seats = listOf(SeatMeasure(4, 2, noise = flatBands(-45.0), fans = FanState.FAST, rtTakes = listOf(RtTake(List(7) { 0.4 })))),
    )

    // --- Settings → what-if -------------------------------------------------------------------------

    @Test
    fun `as measured changes nothing`() {
        assertEquals(WhatIf(), FixSettings().toWhatIf(room))
        assertTrue(FixSettings().isAsMeasured)
        assertFalse(FixSettings(microphone = true).isAsMeasured)
    }

    @Test
    fun `every control maps onto the engine's what-if`() {
        val whatIf = FixSettings(curtainM2 = 20.0, studentsPresent = 30, fansOff = true, microphone = true, voiceChangeDb = 6.0, speakerFromFrontM = 3.0).toWhatIf(room)
        assertEquals(listOf(Absorber(20.0, Absorbers.HEAVY_CURTAIN_ALPHA)), whatIf.absorbers)
        assertEquals(30, whatIf.extraStudents)
        assertEquals(Fixes.FANS_OFF_NOISE_DB, whatIf.noiseChangeDb)
        assertEquals(Fixes.MIC_GAIN_DB, whatIf.micGainDb)
        assertEquals(6.0, whatIf.voiceChangeDb)
        assertEquals(FloorPoint(room.widthM / 2, 3.0), whatIf.speaker)
    }

    @Test
    fun `the speaker spot stays inside the room`() {
        assertEquals(room.lengthM - Fixes.WALL_GAP_M, assertNotNull(FixSettings(speakerFromFrontM = 50.0).toWhatIf(room).speaker).yM)
        assertEquals(Fixes.WALL_GAP_M, assertNotNull(FixSettings(speakerFromFrontM = 0.1).toWhatIf(room).speaker).yM)
    }

    @Test
    fun `settings that make no sense are refused`() {
        assertFailsWith<IllegalArgumentException> { FixSettings(curtainM2 = -1.0) }
        assertFailsWith<IllegalArgumentException> { FixSettings(studentsPresent = -1) }
        assertFailsWith<IllegalArgumentException> { FixSettings(voiceChangeDb = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { FixSettings(speakerFromFrontM = 0.0) }
    }

    // --- Limits for the sliders and switches -------------------------------------------------------

    @Test
    fun `curtains go up to half the wall area, at most 60 m2`() {
        assertEquals(51.2, Fixes.maxCurtainM2(room), 1e-9) // walls 2 × (9 + 7) × 3.2 = 102.4 m²
        assertEquals(60.0, Fixes.maxCurtainM2(aRoom(lengthM = 20.0, widthM = 15.0, heightM = 4.0)))
    }

    @Test
    fun `the students slider fits a full class`() {
        assertEquals(30, Fixes.maxStudents(room)) // 5 × 6 seats
        assertEquals(45, Fixes.maxStudents(aRoom(students = 45)))
    }

    @Test
    fun `fans off is offered only when fans were on during a noise reading`() {
        assertTrue(Fixes.fansWereOn(sample))
        assertTrue(FixPreset.FANS_OFF in Fixes.presets(sample))
        val quietFans = sample.copy(seats = sample.seats.map { it.copy(fans = FanState.OFF) })
        assertFalse(Fixes.fansWereOn(quietFans))
        assertFalse(FixPreset.FANS_OFF in Fixes.presets(quietFans))
    }

    // --- Ranking (V6) ---------------------------------------------------------------------------------

    @Test
    fun `in the echoey sample room curtains are the best single fix`() {
        val ranking = Fixes.rank(sample)
        assertEquals(FixPreset.CURTAINS, ranking.first().preset)
        assertTrue(ranking.first().gain > 0.05, "curtains gain ${ranking.first().gain}")
        assertEquals(Fixes.presets(sample).toSet(), ranking.map { it.preset }.toSet())
    }

    @Test
    fun `in a noisy, dead room the voice and noise fixes win`() {
        val best = Fixes.rank(noisy).first().preset
        assertTrue(best == FixPreset.MICROPHONE || best == FixPreset.FANS_OFF || best == FixPreset.RAISED_VOICE, "best was $best")
    }

    @Test
    fun `the ranking runs best first and each range holds its gain`() {
        val ranking = Fixes.rank(sample)
        assertEquals(ranking.sortedByDescending { it.gain }, ranking)
        ranking.forEach { assertTrue(it.gain in it.gainLow..it.gainHigh, "$it") }
    }

    @Test
    fun `a preset's gain is the back row's change with that preset alone`() {
        val curtains = Fixes.rank(sample).single { it.preset == FixPreset.CURTAINS }
        val before = (ClarityModel.evaluate(sample) as ClarityResult.Ready).room.backRowSti
        val after = (ClarityModel.evaluate(sample, FixPreset.CURTAINS.settings.toWhatIf(room)) as ClarityResult.Ready).room.backRowSti
        assertEquals(after - before, curtains.gain, 1e-12)
    }

    @Test
    fun `there is no ranking before the room is measured`() {
        assertTrue(Fixes.rank(Session(aRoom())).isEmpty())
    }

    // --- Outcome --------------------------------------------------------------------------------------

    @Test
    fun `curtains shorten the echo and lift the back row`() {
        val outcome = assertNotNull(Fixes.outcome(sample, FixSettings(curtainM2 = 40.0)))
        assertTrue(outcome.after.rt60S[3] < outcome.now.rt60S[3])
        assertTrue(outcome.after.backRowSti > outcome.now.backRowSti)
        assertEquals(sample.room.rows, outcome.voice.size)
    }

    @Test
    fun `the voice needed doesn't count the voice slider, but does count a microphone`() {
        val plain = assertNotNull(Fixes.outcome(noisy, FixSettings()))
        val louder = assertNotNull(Fixes.outcome(noisy, FixSettings(voiceChangeDb = 6.0)))
        val withMic = assertNotNull(Fixes.outcome(noisy, FixSettings(microphone = true)))
        assertEquals(plain.voice, louder.voice)
        val need = { o: FixesOutcome -> (o.voice.last().need as VoiceNeed.Required).changeDb }
        assertEquals(need(plain) - Fixes.MIC_GAIN_DB, need(withMic), 0.2)
    }

    @Test
    fun `there is no outcome before the room is measured`() {
        assertNull(Fixes.outcome(Session(aRoom()), FixSettings()))
    }

    @Test
    fun `each preset changes just its own control`() {
        assertEquals(FixSettings(curtainM2 = Fixes.PRESET_CURTAIN_M2), FixPreset.CURTAINS.settings)
        assertEquals(FixSettings(fansOff = true), FixPreset.FANS_OFF.settings)
        assertEquals(FixSettings(microphone = true), FixPreset.MICROPHONE.settings)
        assertEquals(FixSettings(speakerFromFrontM = 3.0), FixPreset.SPEAKER_CLOSER.settings)
        assertEquals(FixSettings(voiceChangeDb = Fixes.RAISED_VOICE_DB), FixPreset.RAISED_VOICE.settings)
        assertEquals(CostTier.FREE, FixPreset.FANS_OFF.cost)
    }
}
