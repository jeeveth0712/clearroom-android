package com.clearroom.app.acoustics

import com.clearroom.app.data.BandLevels
import com.clearroom.app.data.DistanceReading
import com.clearroom.app.data.RtTake
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.Session
import com.clearroom.app.dsp.OctaveBands
import kotlin.math.log10
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The field tests of the doc's testing table that the app works out itself (M13). */
class FieldChecksTest {

    /** The sample's readings in the user's own room. */
    private val mine = SampleData.session.copy(room = SampleData.room.copy(id = "mine"))

    private fun take(vararg speech: Double) = RtTake(listOf(1.4, 1.3, speech[0], speech[1], speech[2], 0.9, 0.8))

    private fun ready(session: Session, whatIf: WhatIf = WhatIf()) = (ClarityModel.evaluate(session, whatIf) as ClarityResult.Ready).room

    private fun speechEcho(room: RoomClarity) = OctaveBands.SPEECH_BANDS.map { room.rt60S[it] }.average()

    // --- Echo repeatability -----------------------------------------------------------------------------

    @Test
    fun `repeatability needs two claps at every speech pitch`() {
        assertNull(FieldChecks.repeatability(emptyList()))
        assertNull(FieldChecks.repeatability(listOf(take(1.2, 1.1, 1.0))))
        val noOneKilohertz = RtTake(listOf(1.4, 1.3, 1.2, null, 1.0, 0.9, 0.8))
        assertNull(FieldChecks.repeatability(listOf(noOneKilohertz, noOneKilohertz)), "a borrowed speech band can't show repeatability")
    }

    @Test
    fun `claps that agree pass, claps that spread more than 10 percent fail`() {
        val same = FieldChecks.repeatability(List(5) { take(1.2, 1.1, 1.0) })
        assertEquals(Repeatability(claps = 5, worstVariation = 0.0), same)
        assertTrue(assertNotNull(same).passed)
        val spread = assertNotNull(FieldChecks.repeatability(listOf(take(1.2, 1.1, 1.0), take(1.2, 1.1, 1.5))))
        assertTrue(spread.worstVariation > FieldChecks.MAX_CLAP_VARIATION, "$spread")
        assertFalse(spread.passed)
    }

    // --- What-if realism (curtains) ------------------------------------------------------------------------

    /** [before] re-measured with every clap's echo time set by [t60]. */
    private fun after(before: Session, t60: (Int) -> Double): Session =
        before.startRemeasuring(1L).copy(seats = before.seats.map { seat -> seat.copy(rtTakes = List(3) { RtTake(List(OctaveBands.COUNT, t60)) }) })

    @Test
    fun `no curtains or no results means nothing to check`() {
        assertNull(FieldChecks.curtainCheck(mine, after(mine) { 1.0 }, curtainM2 = 0.0))
        assertNull(FieldChecks.curtainCheck(Session(mine.room), after(mine) { 1.0 }, curtainM2 = 20.0))
    }

    @Test
    fun `the prediction is the Fixes model applied to before, and the measurement is after`() {
        val hung = 20.0
        val predicted = ready(mine, WhatIf(absorbers = listOf(Absorber(hung, Absorbers.HEAVY_CURTAIN_ALPHA))))
        val measuredAsPredicted = after(mine) { band -> predicted.rt60S[band] }
        val check = assertNotNull(FieldChecks.curtainCheck(mine, measuredAsPredicted, hung))
        assertEquals(speechEcho(ready(mine)), check.beforeS, 1e-9)
        assertEquals(speechEcho(predicted), check.predictedS, 1e-9)
        assertEquals(check.predictedS, check.measuredS, 0.01)
        assertTrue(check.passed)
        val zone = assertNotNull(check.passZoneS)
        assertTrue(check.measuredS in zone)
        assertTrue(check.predictedS in zone)
    }

    @Test
    fun `a measured change far from the predicted one fails, and so does no change at all`() {
        val unchanged = assertNotNull(FieldChecks.curtainCheck(mine, after(mine) { band -> ready(mine).rt60S[band] }, 20.0))
        assertFalse(unchanged.passed, "the echo didn't drop")
        val muchShorter = assertNotNull(FieldChecks.curtainCheck(mine, after(mine) { 0.3 }, 20.0))
        assertTrue(muchShorter.measuredChangeS > 1.5 * muchShorter.predictedChangeS, "$muchShorter")
        assertFalse(muchShorter.passed)
    }

    // --- Distance model --------------------------------------------------------------------------------------

    private fun speechLevel(bands: List<Double>) = 10 * log10(bands.sumOf { 10.0.pow(it / 10) })

    /** The model's predicted voice per band [distanceM] away, shifted by [offsetDb]. */
    private fun predictedReading(session: Session, distanceM: Double, offsetDb: Double = 0.0): DistanceReading {
        val room = ready(session)
        val voice = requireNotNull(session.voice)
        return DistanceReading(distanceM, BandLevels(List(OctaveBands.COUNT) { SpeechPropagation.levelDb(distanceM, voice[it], room.roomConstantM2[it]) + offsetDb }))
    }

    @Test
    fun `the distance check needs the 1 m voice, a reading and the room's results`() {
        assertNull(FieldChecks.distanceCheck(mine), "no reading yet")
        val reading = predictedReading(mine, 5.0)
        assertNull(FieldChecks.distanceCheck(mine.copy(voice = null).withDistanceReading(reading)))
        assertNull(FieldChecks.distanceCheck(Session(mine.room, mine.voice).withDistanceReading(reading)))
    }

    @Test
    fun `readings that match the model pass, readings 5 dB off fail`() {
        val matching = mine.withDistanceReading(predictedReading(mine, 3.0)).withDistanceReading(predictedReading(mine, 8.0))
        val check = assertNotNull(FieldChecks.distanceCheck(matching))
        assertEquals(listOf(3.0, 8.0), check.points.map { it.distanceM })
        assertEquals(0.0, check.meanErrorDb, 1e-9)
        assertTrue(check.passed)

        val louder = assertNotNull(FieldChecks.distanceCheck(mine.withDistanceReading(predictedReading(mine, 5.0, offsetDb = 5.0))))
        assertEquals(5.0, louder.points.single().errorDb, 1e-9)
        assertFalse(louder.passed)
    }

    @Test
    fun `the compared level is the speech pitches' energies added up`() {
        val point = assertNotNull(FieldChecks.distanceCheck(mine.withDistanceReading(predictedReading(mine, 5.0)))).points.single()
        val bands = predictedReading(mine, 5.0).voice
        assertEquals(speechLevel(OctaveBands.SPEECH_BANDS.map { bands[it] }), point.measuredDb, 1e-9)
    }

    // --- Listening check ----------------------------------------------------------------------------------------

    private val front = SeatId(0, 2)
    private val back = SeatId(4, 2)

    @Test
    fun `the listening check needs two scored seats`() {
        assertNull(FieldChecks.listeningCheck(mine.withListeningScore(front, 18)))
    }

    @Test
    fun `when the poorer-rated seat scores lower the app agrees with the listeners`() {
        val room = ready(mine)
        assertTrue(room.at(back).sti < room.at(front).sti - FieldChecks.MIN_STI_GAP, "the sample's back seat is clearly poorer")
        val agrees = assertNotNull(FieldChecks.listeningCheck(mine.withListeningScore(front, 18).withListeningScore(back, 12)))
        assertEquals(1, agrees.comparedPairs)
        assertTrue(agrees.passed)

        val disagrees = assertNotNull(FieldChecks.listeningCheck(mine.withListeningScore(front, 12).withListeningScore(back, 18)))
        assertEquals(1, disagrees.disagreements)
        assertFalse(disagrees.passed)
    }

    @Test
    fun `seats too close in clarity are not compared`() {
        val room = ready(mine)
        val (a, b) = room.seats.sortedBy { it.sti }.zipWithNext().first { (x, y) -> y.sti - x.sti < FieldChecks.MIN_STI_GAP }
        val check = assertNotNull(FieldChecks.listeningCheck(mine.withListeningScore(a.seat, 20).withListeningScore(b.seat, 0)))
        assertEquals(0, check.comparedPairs)
        assertFalse(check.passed, "nothing compared, nothing shown")
    }
}
