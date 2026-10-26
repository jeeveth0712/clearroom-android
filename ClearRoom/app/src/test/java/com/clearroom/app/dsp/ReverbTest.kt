package com.clearroom.app.dsp

import com.clearroom.app.testing.Signals
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReverbTest {

    private val rate = 48_000

    private fun analyse(samples: FloatArray) = Reverb.analyse(samples, rate)

    /** NFR-3: within ±10 % or ±0.1 s, whichever is larger. */
    private fun assertWithinNfr3(expectedS: Double, actualS: Double, context: String) {
        val tolerance = max(0.10 * expectedS, 0.10)
        assertTrue(abs(actualS - expectedS) <= tolerance, "$context: expected $expectedS s ± $tolerance, got $actualS s")
    }

    /**
     * 125 Hz is outside FR-3's 250 Hz–4 kHz and the doc's risk table expects a poor fit there: one
     * short decay in the narrowest band scatters most (sd up to 12 % per clap on these decays).
     */
    private fun assertWithinLowBandAllowance(expectedS: Double, actualS: Double, context: String) {
        val tolerance = max(0.25 * expectedS, 0.15)
        assertTrue(abs(actualS - expectedS) <= tolerance, "$context: expected $expectedS s ± $tolerance, got $actualS s")
    }

    private val echoTimes = listOf(0.3, 0.5, 0.8, 1.2, 2.0)

    /** T20 per band of one synthetic clap; null where the band declined. */
    private fun t20s(t60: Double, floorDb: Double, seed: Long): List<Double?> {
        val samples = Signals.syntheticClap(t60, floorBelowPeakDb = floorDb, seed = seed)
        val result = assertIs<ReverbResult.Analysed>(
            analyse(samples),
            "T60=$t60 floor -$floorDb dB seed $seed: loudest sample at ${samples.indices.maxBy { abs(samples[it]) } / rate.toDouble()} s",
        )
        result.bands.forEachIndexed { band, decay ->
            if (decay is BandDecay.Rejected) {
                assertTrue(band <= 1, "T60=$t60 seed $seed: only 125–250 Hz may decline, band $band gave $decay")
                assertEquals(RejectReason.FIT_POOR, decay.reason)
            }
        }
        return result.bands.map { (it as? BandDecay.Ok)?.t20S }
    }

    // --- NFR-3 accuracy on synthetic decays ---------------------------------------------------
    // One clap's echo time scatters randomly around the truth, most in the narrow low bands
    // (measured over 40 seeds: sd 1–5 % from 1 kHz up, 4–9 % at 250–500 Hz, 6–12 % at 125 Hz).
    // That is why FR-2 asks for 3 claps per seat and FR-3 reports their mean, so NFR-3 is checked
    // on that mean; single claps must already meet it in the speech bands.

    @Test
    fun `single claps are unbiased - on average every band reads the true echo time`() {
        for (t60 in listOf(0.3, 0.8, 2.0)) {
            val takes = (1L..20L).map { seed -> t20s(t60, floorDb = 60.0, seed = seed) }
            (0 until OctaveBands.COUNT).forEach { band ->
                val values = takes.mapNotNull { it[band] }
                val biasPercent = (values.average() - t60) / t60 * 100
                assertTrue(abs(biasPercent) <= 5.0, "T60=$t60, ${OctaveBands.CENTRES_HZ[band]} Hz: bias $biasPercent % over ${values.size} claps")
            }
        }
    }

    @Test
    fun `a single clap meets NFR-3 in the speech bands and always gives them an echo time`() {
        for (t60 in echoTimes) for (floorDb in listOf(45.0, 60.0)) for (seed in 1L..6L) {
            val t20 = t20s(t60, floorDb, seed)
            OctaveBands.SPEECH_BANDS.forEach { band ->
                val value = assertNotNull(t20[band], "T60=$t60 floor $floorDb seed $seed: speech band $band declined")
                assertWithinNfr3(t60, value, "T60=$t60 floor -$floorDb dB seed $seed, ${OctaveBands.CENTRES_HZ[band]} Hz")
            }
        }
    }

    @Test
    fun `a seat's result, the mean of three claps, meets NFR-3 in every FR-3 band and at 8 kHz`() {
        var trial = 0L
        for (t60 in echoTimes) for (floorDb in listOf(45.0, 60.0)) repeat(4) {
            val claps = (1..3).map { t20s(t60, floorDb, seed = 1_000 + trial++) }
            (0 until OctaveBands.COUNT).forEach { band ->
                val values = claps.mapNotNull { it[band] }
                val context = "T60=$t60 floor -$floorDb dB trial $trial, ${OctaveBands.CENTRES_HZ[band]} Hz (${values.size} of 3 claps)"
                if (band == 0) {
                    if (values.isNotEmpty()) assertWithinLowBandAllowance(t60, values.average(), context)
                } else {
                    assertTrue(values.isNotEmpty(), "$context: no clap gave an echo time")
                    assertWithinNfr3(t60, values.average(), context)
                }
            }
        }
    }

    @Test
    fun `with a deep noise floor T30 is measured too and agrees with T20`() {
        val result = assertIs<ReverbResult.Analysed>(analyse(Signals.syntheticClap(1.0, floorBelowPeakDb = 60.0)))
        OctaveBands.SPEECH_BANDS.forEach { band ->
            val ok = assertIs<BandDecay.Ok>(result.bands[band])
            val t30 = assertNotNull(ok.t30S, "band $band")
            assertWithinNfr3(1.0, t30, "T30 band $band")
            assertTrue(ok.xiPermille < 10.0, "a clean exponential decay should be straight: ξ=${ok.xiPermille}")
        }
    }

    // --- Finding the clap ------------------------------------------------------------------------

    @Test
    fun `the clap starts where the sound first rises, even when the loudest moment comes later`() {
        // Regression (Oct 26): in this clap the loudest sample comes 66 ms into a slow decay. Taking it
        // as the clap put the first 16 ms of decay inside the noise window, and the clap was refused.
        val samples = Signals.syntheticClap(1.2, floorBelowPeakDb = 60.0, seed = 1095)
        val signal = DoubleArray(samples.size) { samples[it].toDouble() }
        val onset = assertNotNull(Reverb.findClap(signal, rate), "clap not found")
        assertTrue(abs(onset - rate) <= rate / 100, "onset at ${onset.toDouble() / rate} s; the clap starts at 1.0 s")
    }

    // --- Rejections (FR-2) -------------------------------------------------------------------

    @Test
    fun `a clap too early or too late for the noise window and decay is not analysed`() {
        assertEquals(ReverbResult.NoClap, analyse(Signals.syntheticClap(0.8, clapAtS = 0.1)))
        assertEquals(ReverbResult.NoClap, analyse(Signals.syntheticClap(0.8, clapAtS = 4.0)))
    }

    @Test
    fun `steady noise or silence is not mistaken for a clap`() {
        val steadyNoise = Signals.syntheticClap(0.8, floorBelowPeakDb = 0.0) // floor as loud as the "clap"
        assertEquals(ReverbResult.NoClap, analyse(steadyNoise))
        assertEquals(ReverbResult.NoClap, analyse(FloatArray(5 * rate)))
    }

    @Test
    fun `a decay only 20 dB above the noise is rejected for low dynamic range`() {
        val result = assertIs<ReverbResult.Analysed>(analyse(Signals.syntheticClap(0.8, floorBelowPeakDb = 20.0)))
        OctaveBands.SPEECH_BANDS.forEach { band ->
            assertEquals(RejectReason.LOW_DYNAMIC_RANGE, assertIs<BandDecay.Rejected>(result.bands[band]).reason)
        }
    }

    @Test
    fun `an echo far beyond 6 s gives no echo time`() {
        val result = assertIs<ReverbResult.Analysed>(analyse(Signals.syntheticClap(12.0, floorBelowPeakDb = 60.0)))
        OctaveBands.SPEECH_BANDS.forEach { band -> assertIs<BandDecay.Rejected>(result.bands[band], "band $band") }
    }

    // --- Chart data (V3, V10) ----------------------------------------------------------------

    @Test
    fun `the decay curve starts at 0 dB and never rises, sampled every 10 ms`() {
        val result = assertIs<ReverbResult.Analysed>(analyse(Signals.syntheticClap(1.0)))
        val ok = assertIs<BandDecay.Ok>(result.bands[3])
        assertEquals(0.0, ok.schroederDb.first(), absoluteTolerance = 1e-9)
        assertTrue(ok.schroederDb.zipWithNext().all { (a, b) -> b <= a + 1e-9 })
        // A 1-s echo time falls 60 dB per second; the fitted line for V3 should say so (±10 %).
        assertEquals(-60.0, ok.fitSlopeDbPerS, absoluteTolerance = 6.0)
    }

    @Test
    fun `the echo waterfall is 7 bands by 2 s, from 0 dB down to the -60 dB floor`() {
        val result = assertIs<ReverbResult.Analysed>(analyse(Signals.syntheticClap(0.8)))
        assertEquals(7, result.waterfallDb.size)
        result.waterfallDb.forEach { row ->
            assertEquals(200, row.size)
            assertEquals(0.0, row.first(), absoluteTolerance = 1e-9)
            assertTrue(row.all { it in DspConstants.WATERFALL_FLOOR_DB..0.0 }, "$row")
            assertTrue(row.take(10).average() > row.takeLast(10).average())
        }
    }

    // --- The small steps ---------------------------------------------------------------------

    @Test
    fun `the moving average keeps a constant constant and spreads a spike evenly`() {
        assertContentEquals(DoubleArray(5) { 2.0 }, Reverb.movingAverage(DoubleArray(5) { 2.0 }, 3))
        val spread = Reverb.movingAverage(doubleArrayOf(0.0, 0.0, 3.0, 0.0, 0.0), 3)
        assertContentEquals(doubleArrayOf(0.0, 1.0, 1.0, 1.0, 0.0), spread)
    }

    @Test
    fun `Schroeder integration of an exponential decay is a straight line in dB`() {
        val decayPerSample = 0.999
        val squares = DoubleArray(20_000) { Math.pow(decayPerSample, it.toDouble()) }
        val curve = Reverb.schroederDb(squares, 0, squares.size)
        assertEquals(0.0, curve[0], absoluteTolerance = 1e-12)
        val expectedStepDb = 10 * kotlin.math.log10(decayPerSample)
        assertEquals(expectedStepDb * 1_000, curve[1_000], absoluteTolerance = 0.01)
    }

    @Test
    fun `the line fit recovers an exact slope and reports a perfect fit`() {
        val curve = DoubleArray(rate) { -60.0 * it / rate } // -60 dB/s
        val fit = assertNotNull(Reverb.fitLine(curve, -5.0, -25.0, rate))
        assertEquals(-60.0, fit.slopeDbPerS, absoluteTolerance = 1e-6)
        assertEquals(1.0, fit.rSquared, absoluteTolerance = 1e-9)
        assertEquals(20.0 / 60.0, fit.spanS, absoluteTolerance = 1e-3)
    }

    @Test
    fun `no line fit when the curve never reaches the end of the range`() {
        val shallow = DoubleArray(rate) { -10.0 * it / rate } // only reaches -10 dB
        assertNull(Reverb.fitLine(shallow, -5.0, -25.0, rate))
    }

    @Test
    fun `energy ratios never hit infinity`() {
        assertEquals(10.0, Reverb.ratioDb(10.0, 1.0), absoluteTolerance = 1e-12)
        assertTrue(Reverb.ratioDb(0.0, 1.0).isFinite())
        assertTrue(Reverb.ratioDb(1.0, 0.0).isFinite())
    }

    @Test
    fun `time-reversed filtering keeps a centre-band tone at full level`() {
        val tone = DoubleArray(rate) { kotlin.math.sin(2 * Math.PI * 1_000.0 * it / rate) }
        val out = Reverb.filterTimeReversed(tone, 1_000.0, rate)
        val middle = rate / 4 until 3 * rate / 4
        val ratio = middle.sumOf { out[it] * out[it] } / middle.sumOf { tone[it] * tone[it] }
        assertEquals(1.0, ratio, absoluteTolerance = 0.02)
    }

    @Test
    fun `an impossible sample rate is rejected`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { Reverb.analyse(FloatArray(10), 0) }
    }
}
