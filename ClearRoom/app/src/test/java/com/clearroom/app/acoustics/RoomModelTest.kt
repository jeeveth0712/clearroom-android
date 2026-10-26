package com.clearroom.app.acoustics

import com.clearroom.app.data.BandLevels
import com.clearroom.app.dsp.OctaveBank
import com.clearroom.app.testing.aRoom
import com.clearroom.app.testing.flatBands
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RoomModelTest {

    private val room = aRoom(lengthM = 9.0, widthM = 7.0, heightM = 3.2) // V = 201.6 m³, S = 228.4 m²
    private val oneSecond = List(7) { 1.0 }

    // --- Sabine (doc step 2) -----------------------------------------------------------------

    @Test
    fun `the fitted room reproduces the measured echo time`() {
        val t60 = listOf(1.4, 1.3, 1.2, 1.1, 1.0, 0.9, 0.8)
        val sabine = SabineRoom.fitted(room, t60)
        t60.indices.forEach { assertEquals(t60[it], sabine.t60S(it), absoluteTolerance = 1e-12) }
        assertEquals(0.161 * 201.6 / 1.1, sabine.absorptionM2[3], absoluteTolerance = 1e-9)
    }

    @Test
    fun `room constant follows R = S·a over 1 - a`() {
        val sabine = SabineRoom.fitted(room, oneSecond)
        val alpha = 0.161 * 201.6 / 228.4
        assertEquals(alpha, sabine.meanAlpha(2), absoluteTolerance = 1e-12)
        assertEquals(228.4 * alpha / (1 - alpha), sabine.roomConstantM2(2), absoluteTolerance = 1e-9)
    }

    @Test
    fun `the mean absorption coefficient is capped below 1`() {
        val veryDead = SabineRoom.fitted(room, List(7) { 0.05 })
        assertEquals(SabineRoom.MAX_MEAN_ALPHA, veryDead.meanAlpha(0))
        assertTrue(veryDead.roomConstantM2(0).isFinite())
    }

    @Test
    fun `20 m² of heavy curtain shortens the echo as Sabine predicts`() {
        val before = SabineRoom.fitted(room, oneSecond)
        val after = before.withAbsorber(20.0, Absorbers.HEAVY_CURTAIN_ALPHA)
        (0 until 7).forEach { band ->
            val expected = 0.161 * 201.6 / (before.absorptionM2[band] + 20.0 * Absorbers.HEAVY_CURTAIN_ALPHA[band])
            assertEquals(expected, after.t60S(band), absoluteTolerance = 1e-12)
            assertTrue(after.t60S(band) < before.t60S(band))
        }
    }

    @Test
    fun `students add their absorption per person`() {
        val before = SabineRoom.fitted(room, oneSecond)
        val after = before.withPeople(30, Absorbers.SEATED_STUDENT_M2)
        assertEquals(before.absorptionM2[3] + 30 * 0.45, after.absorptionM2[3], absoluteTolerance = 1e-12)
        assertEquals(before, before.withPeople(0, Absorbers.SEATED_STUDENT_M2))
    }

    @Test
    fun `impossible rooms and absorbers are refused`() {
        assertFailsWith<IllegalArgumentException> { SabineRoom.fitted(room, List(7) { 0.0 }) }
        assertFailsWith<IllegalArgumentException> { SabineRoom.fitted(room, List(6) { 1.0 }) }
        val sabine = SabineRoom.fitted(room, oneSecond)
        assertFailsWith<IllegalArgumentException> { sabine.withAbsorber(-1.0, Absorbers.HEAVY_CURTAIN_ALPHA) }
        assertFailsWith<IllegalArgumentException> { sabine.withAbsorber(10.0, List(7) { 1.5 }) }
        assertFailsWith<IllegalArgumentException> { sabine.withPeople(-3, Absorbers.SEATED_STUDENT_M2) }
    }

    // --- Speech at a distance (doc step 3) ------------------------------------------------------

    @Test
    fun `speech level at 1 m is the 1 m reading exactly`() {
        assertEquals(-30.0, SpeechPropagation.levelDb(1.0, -30.0, roomConstantM2 = 40.0), absoluteTolerance = 1e-12)
    }

    @Test
    fun `speech fades with distance and levels off at the reverberant floor`() {
        val levels = listOf(1.0, 2.0, 4.0, 8.0, 50.0).map { SpeechPropagation.levelDb(it, 0.0, roomConstantM2 = 40.0) }
        assertTrue(levels.zipWithNext().all { (near, far) -> far < near }, "$levels")
        val q = SpeechPropagation.TALKER_DIRECTIVITY_Q
        val floor = 10 * log10((4 / 40.0) / (q / (4 * PI) + 4 / 40.0))
        assertEquals(floor, levels.last(), absoluteTolerance = 0.05)
    }

    @Test
    fun `seats nearer than half a metre count as half a metre`() {
        assertEquals(SpeechPropagation.levelDb(0.5, 0.0, 40.0), SpeechPropagation.levelDb(0.1, 0.0, 40.0))
    }

    @Test
    fun `direct and reverberant sound are equal at sqrt(QR over 16 pi)`() {
        val r = 40.0
        val equalAt = sqrt(SpeechPropagation.TALKER_DIRECTIVITY_Q * r / (16 * PI))
        assertEquals(1.0, SpeechPropagation.directToReverberant(equalAt, r), absoluteTolerance = 1e-12)
    }

    @Test
    fun `critical distance uses the doc's 0,057 times sqrt(QV over T)`() {
        assertEquals(0.057 * sqrt(2 * 201.6 / 1.0), SpeechPropagation.criticalDistanceM(201.6, 1.0), absoluteTolerance = 1e-12)
        assertFailsWith<IllegalArgumentException> { SpeechPropagation.criticalDistanceM(201.6, 0.0) }
    }

    // --- Noise between measured seats ---------------------------------------------------------

    @Test
    fun `a measured seat keeps its own noise reading`() {
        val readings = listOf(FloorPoint(1.0, 2.0) to flatBands(-60.0), FloorPoint(5.0, 8.0) to flatBands(-40.0))
        assertEquals(flatBands(-60.0), NoiseField.at(FloorPoint(1.0, 2.0), readings))
    }

    @Test
    fun `with one measured seat its noise is used everywhere`() {
        val readings = listOf(FloorPoint(1.0, 2.0) to flatBands(-55.0))
        assertEquals(-55.0, NoiseField.at(FloorPoint(6.0, 8.0), readings)[3], absoluteTolerance = 1e-12)
    }

    @Test
    fun `halfway between two readings the energies average, not the dB`() {
        val readings = listOf(FloorPoint(0.0, 0.0) to flatBands(-60.0), FloorPoint(4.0, 0.0) to flatBands(-40.0))
        val middle = NoiseField.at(FloorPoint(2.0, 0.0), readings)
        val energyMean = 10 * log10((10.0.pow(-6.0) + 10.0.pow(-4.0)) / 2)
        assertEquals(energyMean, middle[0], absoluteTolerance = 1e-9)
        assertTrue(middle[0] > -50.0, "a dB average would give -50; energy averaging weights the louder seat")
    }

    @Test
    fun `nearer readings count for more`() {
        val readings = listOf(FloorPoint(0.0, 0.0) to flatBands(-60.0), FloorPoint(10.0, 0.0) to flatBands(-40.0))
        assertTrue(NoiseField.at(FloorPoint(1.0, 0.0), readings)[0] < NoiseField.at(FloorPoint(9.0, 0.0), readings)[0])
        assertFailsWith<IllegalArgumentException> { NoiseField.at(FloorPoint(0.0, 0.0), emptyList()) }
    }

    // --- FR-5 fallback voice ------------------------------------------------------------------

    @Test
    fun `the fallback voice is ANSI S3,5 normal effort, 125 Hz set 6 dB under 250 Hz`() {
        val expected = listOf(57.23 - 6, 57.23, 59.75, 53.49, 48.80, 43.81, 38.61)
        expected.indices.forEach { assertEquals(expected[it], VoiceFallback.NORMAL_SPEECH_AT_1M_SPL_DB[it], absoluteTolerance = 1e-9) }
    }

    @Test
    fun `the fallback voice totals about 60 dBA, as the doc assumes`() {
        val dba = OctaveBank.aWeightedDb(VoiceFallback.NORMAL_SPEECH_AT_1M_SPL_DB)
        assertTrue(dba in 59.0..61.0, "fallback voice is $dba dBA")
    }

    @Test
    fun `in phone units the fallback sits 112,35 dB below its sound pressure level`() {
        val dbfs: BandLevels = VoiceFallback.normalVoiceDbFs
        assertEquals(VoiceFallback.NORMAL_SPEECH_AT_1M_SPL_DB[3] - 112.35, dbfs[3], absoluteTolerance = 1e-9)
    }
}
