package com.clearroom.app.measure

import kotlin.test.assertNotNull
import com.clearroom.app.data.DecayFit
import com.clearroom.app.data.BandCurve
import com.clearroom.app.dsp.BandDecay
import com.clearroom.app.dsp.OctaveBands
import com.clearroom.app.dsp.RejectReason
import com.clearroom.app.dsp.Reverb
import com.clearroom.app.dsp.ReverbResult
import com.clearroom.app.testing.Signals
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClapTakeTest {

    private fun ok(t20: Double) = BandDecay.Ok(t20, null, 2.0, 45.0, -85.0, listOf(0.0), -60.0 / t20, 0.0)
    private fun rejected(reason: RejectReason, noiseDb: Double = -85.0) = BandDecay.Rejected(reason, 20.0, noiseDb)
    private fun analysed(vararg bands: BandDecay) = ReverbResult.Analysed(bands.toList(), emptyList())

    @Test
    fun `a take counts when the speech bands work, with failed outer bands left empty`() {
        val result = analysed(
            rejected(RejectReason.FIT_POOR), ok(1.2), ok(1.1), ok(1.0), ok(0.9), ok(0.8), rejected(RejectReason.LOW_DYNAMIC_RANGE),
        )
        val accepted = assertIs<ClapVerdict.Accepted>(ClapTake.judge(result))
        assertEquals(listOf(null, 1.2, 1.1, 1.0, 0.9, 0.8, null), accepted.take.t60S)
        assertEquals(result, accepted.analysis)
    }

    @Test
    fun `no clap means clap again`() {
        assertEquals(ClapVerdict.Rejected(ClapProblem.NO_CLAP), ClapTake.judge(ReverbResult.NoClap))
    }

    @Test
    fun `a weak clap in a quiet room means clap louder`() {
        val result = analysed(ok(1.0), ok(1.0), rejected(RejectReason.LOW_DYNAMIC_RANGE, noiseDb = -85.0), ok(1.0), ok(1.0), ok(1.0), ok(1.0))
        assertEquals(ClapVerdict.Rejected(ClapProblem.CLAP_LOUDER), ClapTake.judge(result))
    }

    @Test
    fun `a short decay in a noisy room means too noisy`() {
        val result = analysed(ok(1.0), ok(1.0), ok(1.0), rejected(RejectReason.LOW_DYNAMIC_RANGE, noiseDb = -50.0), ok(1.0), ok(1.0), ok(1.0))
        assertEquals(ClapVerdict.Rejected(ClapProblem.TOO_NOISY), ClapTake.judge(result))
    }

    @Test
    fun `a curved or impossible decay means unclear, clap again`() {
        listOf(RejectReason.FIT_POOR, RejectReason.OUT_OF_RANGE).forEach { reason ->
            val result = analysed(ok(1.0), ok(1.0), ok(1.0), ok(1.0), rejected(reason), ok(1.0), ok(1.0))
            assertEquals(ClapVerdict.Rejected(ClapProblem.UNCLEAR), ClapTake.judge(result), "$reason")
        }
    }

    @Test
    fun `when speech bands fail for different reasons, a too-short decay is the more useful advice`() {
        val result = analysed(ok(1.0), ok(1.0), rejected(RejectReason.FIT_POOR), rejected(RejectReason.LOW_DYNAMIC_RANGE), ok(1.0), ok(1.0), ok(1.0))
        assertEquals(ClapVerdict.Rejected(ClapProblem.CLAP_LOUDER), ClapTake.judge(result))
    }

    @Test
    fun `a synthetic clap of 0,8 s becomes a take of about 0,8 s in the speech bands, end to end`() {
        val verdict = ClapTake.judge(Reverb.analyse(Signals.syntheticClap(0.8), 48_000))
        val take = assertIs<ClapVerdict.Accepted>(verdict).take
        OctaveBands.SPEECH_BANDS.forEach { band ->
            val t60 = take.t60S[band]
            assertTrue(t60 != null && abs(t60 - 0.8) <= 0.1, "band $band: $t60") // NFR-3: ±0.1 s here
        }
    }

    @Test
    fun `a clap where every band failed is never accepted`() {
        val result = analysed(*Array(7) { rejected(RejectReason.FIT_POOR) })
        assertEquals(ClapVerdict.Rejected(ClapProblem.UNCLEAR), ClapTake.judge(result))
    }

    @Test
    fun `the curves kept from a clap are rounded to a hundredth of a dB`() {
        val band = BandDecay.Ok(1.0, null, 2.5, 45.0, -85.0, listOf(0.0, -1.23456, -2.5), -60.0, 0.1)
        val analysis = ReverbResult.Analysed(List(7) { band }, List(7) { listOf(-0.004, -12.3456) })
        val curves = ClapTake.curvesOf(analysis)
        assertEquals(listOf(0.0, -1.23, -2.5), curves.bands[0].schroederDb)
        assertEquals(listOf(0.0, -12.35), curves.waterfallDb[0])
        assertEquals(DecayFit(-60.0, 0.1, 2.5), curves.bands[0].fit)
    }

    @Test
    fun `a rejected band keeps no curve and no fit`() {
        val bands = List(7) { if (it == 0) rejected(RejectReason.LOW_DYNAMIC_RANGE) else ok(1.0) }
        val curves = ClapTake.curvesOf(ReverbResult.Analysed(bands, List(7) { listOf(0.0) }))
        assertEquals(BandCurve(emptyList(), null), curves.bands[0])
        assertEquals(DecayFit(-60.0, 0.0, 2.0), curves.bands[1].fit)
    }

    @Test
    fun `a real synthetic clap gives curves whose fit matches the echo time`() {
        val clip = Signals.syntheticClap(t60S = 0.8, sampleRate = 48_000)
        val analysis = assertIs<ReverbResult.Analysed>(Reverb.analyse(clip, 48_000))
        val curves = ClapTake.curvesOf(analysis)
        val fit = assertNotNull(curves.bands[3].fit)
        assertEquals(0.8, fit.t60S, 0.08)
        assertEquals(7, curves.waterfallDb.size)
    }
}
