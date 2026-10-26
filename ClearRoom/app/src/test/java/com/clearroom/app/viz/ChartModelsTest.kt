package com.clearroom.app.viz

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.clearroom.app.acoustics.BandRt60
import com.clearroom.app.acoustics.Cause
import com.clearroom.app.acoustics.ClarityModel
import com.clearroom.app.acoustics.ClarityResult
import com.clearroom.app.acoustics.SeatLayout
import com.clearroom.app.acoustics.SpeechPropagation
import com.clearroom.app.data.BandCurve
import com.clearroom.app.data.DecayFit
import com.clearroom.app.data.RtTake
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.SeatMeasure
import com.clearroom.app.data.Session
import com.clearroom.app.dsp.Sti
import com.clearroom.app.testing.aRoom
import com.clearroom.app.testing.flatBands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pure models behind the 2D charts: V1, V2 (seat maps), V3, V4, V13, V14, V15. */
class ChartModelsTest {

    private val sample = SampleData.session
    private val clarity = assertIs<ClarityResult.Ready>(ClarityModel.evaluate(sample)).room

    // --- V1, V2 seat maps -----------------------------------------------------------------------------

    @Test
    fun `a tap picks the nearest seat within reach`() {
        val plan = PlanTransform.fit(sample.room.widthM, sample.room.lengthM, Size(700f, 900f), paddingPx = 0f)
        val seatCentre = plan.toPx(SeatLayout.position(sample.room, SeatId(4, 5)))
        assertEquals(SeatId(4, 5), hitSeat(sample.room, plan, seatCentre + Offset(5f, -5f), reachPx = 30f))
    }

    @Test
    fun `a tap far from every seat picks none`() {
        val plan = PlanTransform.fit(sample.room.widthM, sample.room.lengthM, Size(700f, 900f), paddingPx = 0f)
        assertNull(hitSeat(sample.room, plan, plan.toPx(SeatLayout.speaker(sample.room)), reachPx = 30f))
    }

    @Test
    fun `seats are 0,6 m squares, smaller where seats crowd, never too small to see`() {
        assertEquals(60f, seatSizePx(sample.room, pxPerM = 100f, minPx = 10f), 1e-3f)
        assertEquals(3.75f, seatSizePx(sample.room.copy(lengthM = 3.5, widthM = 2.5), pxPerM = 100f, minPx = 1f), 1e-3f) // 0.75 × 5 cm
        assertEquals(10f, seatSizePx(sample.room.copy(lengthM = 3.5, widthM = 2.5), pxPerM = 100f, minPx = 10f))
    }

    @Test
    fun `the spoken summaries count easy seats and seats per cause`() {
        assertEquals(clarity.seats.count { it.sti >= 0.6 }, easySeatCount(clarity))
        val counts = causeCounts(clarity)
        assertEquals(Cause.entries.toSet(), counts.keys)
        assertEquals(clarity.seats.size, counts.values.sum())
    }

    // --- V4 echo time by pitch --------------------------------------------------------------------------

    private fun rt(vararg means: Double, estimated: Int = -1) =
        Rt60ChartModel(means.mapIndexed { band, mean -> BandRt60(mean, 0.05, 3, estimated = band == estimated) }, targetS = 0.6, speechRt60S = means[3])

    @Test
    fun `the room's echo chart pools every clap and shows the classroom target`() {
        val model = assertNotNull(Rt60ChartModel.of(sample))
        assertEquals(7, model.bands.size)
        assertEquals(0.6, model.targetS)
        assertTrue(model.speechOverTarget)
    }

    @Test
    fun `there is no echo chart before the first clap`() {
        assertNull(Rt60ChartModel.of(Session(aRoom())))
    }

    @Test
    fun `bars over the target are the ones that ring too long`() {
        assertEquals(listOf(true, true, false, false, false, false, false), rt(0.9, 0.7, 0.6, 0.5, 0.5, 0.4, 0.4).overTarget)
    }

    @Test
    fun `the hint names where the longest too-long ringing is`() {
        assertEquals(EchoHint.LOW_PITCHES, rt(1.4, 1.3, 1.2, 1.1, 1.0, 0.9, 0.8).hint)
        assertEquals(EchoHint.MID_PITCHES, rt(0.5, 0.7, 0.9, 1.0, 0.8, 0.6, 0.5).hint)
        assertEquals(EchoHint.HIGH_PITCHES, rt(0.4, 0.5, 0.5, 0.5, 0.6, 0.8, 0.9).hint)
        assertNull(rt(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5).hint)
    }

    @Test
    fun `borrowed bands are flagged for the caption`() {
        assertTrue(rt(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, estimated = 0).anyEstimated)
    }

    // --- V3 decay curve ----------------------------------------------------------------------------------

    @Test
    fun `fit quality follows the straightness limits`() {
        assertEquals(FitQuality.CLEAN, fitQuality(10.0))
        assertEquals(FitQuality.USABLE, fitQuality(25.0))
        assertEquals(FitQuality.ROUGH, fitQuality(41.0))
    }

    @Test
    fun `the decay chart prefers the chosen seat's clap, then any seat's`() {
        assertEquals(SeatId(2, 3), assertNotNull(decaySource(sample, SeatId(2, 3))).seat)
        assertEquals(SeatId(0, 2), assertNotNull(decaySource(sample, SeatId(3, 3))).seat) // no clap there: the first seat with one
        assertNull(decaySource(Session(aRoom()), null))
    }

    @Test
    fun `the time axis runs until the fit reaches -60 dB or the curve ends`() {
        assertEquals(1.2, decayEndS(BandCurve(List(30) { -2.0 * it }, DecayFit(-50.0, 0.0, 2.0))), 1e-9) // fit: 60 / 50
        assertEquals(0.5, decayEndS(BandCurve(List(50) { -0.5 * it }, null)), 1e-9) // no fit: the curve's 50 × 10 ms
        assertEquals(0.2, decayEndS(BandCurve(emptyList(), null)), 1e-9) // nothing: a minimum span
    }

    // --- V13 MTF ---------------------------------------------------------------------------------------

    @Test
    fun `speech rhythm fades faster in an echoey room, and none is lost in a dry, silent one`() {
        val seat = clarity.at(SeatId(4, 2))
        val model = MtfModel.of(clarity, seat)
        assertEquals(7, model.curves.size)
        assertTrue(model.curves.all { it.size == Sti.MOD_FREQS_HZ.size && it.all { m -> m in 0.0..1.0 } })
        assertTrue(model.curves.all { curve -> curve.zipWithNext().all { (a, b) -> b <= a + 1e-12 } }, "m falls with rhythm speed")
        assertEquals(model.curves[3].average(), model.midBandMean)

        val dry = clarity.copy(rt60S = List(7) { 0.0 })
        val quiet = seat.copy(noiseDb = List(7) { -200.0 })
        assertTrue(MtfModel.of(dry, quiet).curves.flatten().all { it > 0.99 })
    }

    // --- V14 speech vs distance ---------------------------------------------------------------------------

    @Test
    fun `speech is loudest near the speaker and levels off with distance`() {
        val model = DistanceModel.of(clarity)
        assertEquals(SpeechPropagation.MIN_DISTANCE_M, model.distancesM.first())
        assertTrue(model.distancesM.last() > clarity.seats.maxOf { it.distanceM })
        assertTrue(model.speechRelDb.zipWithNext().all { (a, b) -> b <= a })
        assertTrue(model.speechRelDb.first() > 0.0, "closer than 1 m is louder than at 1 m")
        assertEquals(clarity.criticalDistanceM, model.criticalDistanceM)
    }

    @Test
    fun `the back row's margin is the worst back seat's voice over noise at 1 kHz`() {
        val model = DistanceModel.of(clarity)
        val expected = clarity.seats.filter { it.seat.row == 4 }.minOf { it.speechDb[3] - it.noiseDb[3] }
        assertEquals(expected, model.backRowMarginDb, 1e-12)
        assertEquals(expected >= COMFORTABLE_SNR_DB, model.backRowComfortable)
    }

    @Test
    fun `noise louder than the voice leaves the back row uncomfortable`() {
        val loud = Session(aRoom(), voice = flatBands(-40.0), seats = listOf(SeatMeasure(4, 2, noise = flatBands(-35.0), rtTakes = listOf(RtTake(List(7) { 0.6 })))))
        val model = DistanceModel.of(assertIs<ClarityResult.Ready>(ClarityModel.evaluate(loud)).room)
        assertTrue(model.backRowMarginDb < 0.0)
        assertTrue(!model.backRowComfortable)
    }

    // --- V15 octave spectrum ------------------------------------------------------------------------------

    @Test
    fun `the spectrum's SNR is voice minus noise, and its summary averages the speech bands`() {
        val model = SpectrumModel(voiceDb = listOf(-30.0, -30.0, -30.0, -32.0, -34.0, -40.0, -50.0), noiseDb = listOf(-40.0, -45.0, -50.0, -50.0, -50.0, -55.0, -60.0))
        assertEquals(listOf(10.0, 15.0, 20.0, 18.0, 16.0, 15.0, 10.0), model.snrDb)
        assertEquals(18.0, model.speechSnrDb, 1e-12)
    }

    @Test
    fun `a seat's spectrum comes from its speech and noise`() {
        val seat = clarity.at(SeatId(0, 0))
        assertEquals(SpectrumModel(seat.speechDb, seat.noiseDb), SpectrumModel.of(seat))
    }
}
