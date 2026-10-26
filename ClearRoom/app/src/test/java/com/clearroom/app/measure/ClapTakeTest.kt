package com.clearroom.app.measure

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
}
