package com.clearroom.app.dsp

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StiTest {

    private fun flat(value: Double) = DoubleArray(7) { value }
    private val noNoise = flat(Sti.NO_NOISE_DB)

    // --- Golden values (NFR-4: within ±0.02; held to 0.001 here) ------------------------------
    // From .claude/skills/clearroom-testing/scripts/sti_reference.py, an independent Python version
    // of the doc's formulas. Same T60 and SNR in all seven bands unless stated.

    @Test
    fun `matches the independent reference on the golden table`() {
        val golden = listOf(
            Triple(0.0, Sti.NO_NOISE_DB, 1.0000),
            Triple(0.0, 0.0, 0.5000),
            Triple(0.0, 15.0, 1.0000),
            Triple(0.0, -15.0, 0.0000),
            Triple(0.0, 6.0, 0.7000),
            Triple(0.3, Sti.NO_NOISE_DB, 0.8273),
            Triple(0.5, Sti.NO_NOISE_DB, 0.7361),
            Triple(0.6, Sti.NO_NOISE_DB, 0.6999),
            Triple(1.0, Sti.NO_NOISE_DB, 0.5882),
            Triple(1.5, Sti.NO_NOISE_DB, 0.4977),
            Triple(2.0, Sti.NO_NOISE_DB, 0.4371),
            Triple(0.6, 10.0, 0.6134),
            Triple(1.0, 5.0, 0.4634),
            Triple(1.5, 0.0, 0.3109),
        )
        golden.forEach { (t60, snr, expected) ->
            assertEquals(expected, Sti.sti(flat(t60), flat(snr)), absoluteTolerance = 0.001, "T60=$t60 SNR=$snr")
        }
    }

    @Test
    fun `matches the reference with different values in every band`() {
        val t60 = doubleArrayOf(0.5, 0.6, 0.7, 0.8, 0.8, 0.7, 0.6)
        val snr = doubleArrayOf(20.0, 18.0, 15.0, 12.0, 10.0, 8.0, 6.0)
        assertEquals(0.5913, Sti.sti(t60, snr), absoluteTolerance = 0.001)
    }

    @Test
    fun `matches the reference when direct sound is included`() {
        // sti_reference.py --t60 T --snr S --direct d (complex-number form, written separately)
        assertEquals(0.6419, Sti.sti(flat(1.0), noNoise, flat(0.5)), absoluteTolerance = 0.001)
        assertEquals(0.6911, Sti.sti(flat(1.0), noNoise, flat(1.0)), absoluteTolerance = 0.001)
        assertEquals(0.4539, Sti.sti(flat(2.0), flat(10.0), flat(0.25)), absoluteTolerance = 0.001)
        assertEquals(1.0000, Sti.sti(flat(1.0), noNoise, flat(1_000.0)), absoluteTolerance = 0.001)
    }

    // --- Structure ---------------------------------------------------------------------------------

    @Test
    fun `band weights minus redundancy sum to exactly one, so a perfect channel scores 1`() {
        assertEquals(1.0, Sti.ALPHA.sum() - Sti.BETA.sum(), absoluteTolerance = 1e-9)
        assertEquals(7, Sti.ALPHA.size)
        assertEquals(6, Sti.BETA.size)
        assertEquals(14, Sti.MOD_FREQS_HZ.size)
    }

    @Test
    fun `without direct sound the modulation index is the doc's formula exactly`() {
        for (f in Sti.MOD_FREQS_HZ) for (t60 in listOf(0.3, 1.0, 2.5)) for (snr in listOf(-5.0, 0.0, 12.0)) {
            val docFormula = 1 / sqrt(1 + (2 * PI * f * t60 / 13.8).pow(2)) * (1 / (1 + 10.0.pow(-snr / 10)))
            assertEquals(docFormula, Sti.modulationIndex(f, t60, snr), absoluteTolerance = 1e-12, "F=$f T=$t60 SNR=$snr")
        }
    }

    @Test
    fun `with no echo only noise reduces modulation, and with overwhelming direct sound echo stops mattering`() {
        assertEquals(1 / (1 + 10.0.pow(-0.3)), Sti.modulationIndex(4.0, 0.0, 3.0), absoluteTolerance = 1e-12)
        assertEquals(1.0, Sti.modulationIndex(4.0, 2.0, Sti.NO_NOISE_DB, directToReverberant = 1e9), absoluteTolerance = 1e-6)
    }

    @Test
    fun `transmission index maps apparent SNR -15 to +15 dB onto 0 to 1`() {
        assertEquals(0.5, Sti.transmissionIndex(0.5), absoluteTolerance = 1e-12)
        assertEquals(1.0, Sti.transmissionIndex(1.0))
        assertEquals(1.0, Sti.transmissionIndex(0.9999))
        assertEquals(0.0, Sti.transmissionIndex(0.0))
        assertEquals(0.0, Sti.transmissionIndex(-0.1))
    }

    @Test
    fun `with no echo, STI is (SNR + 15) over 30 - the hand-worked check`() {
        for (snr in listOf(-12.0, -6.0, 0.0, 3.0, 9.0, 14.0)) {
            assertEquals((snr + 15) / 30, Sti.sti(flat(0.0), flat(snr)), absoluteTolerance = 1e-9, "SNR=$snr")
        }
    }

    // --- Properties over a grid --------------------------------------------------------------

    @Test
    fun `more echo never helps, more signal never hurts, more direct sound never hurts`() {
        val t60s = listOf(0.0, 0.3, 0.6, 1.0, 1.5, 2.5, 4.0)
        val snrs = listOf(-20.0, -5.0, 0.0, 5.0, 10.0, 20.0)
        val directs = listOf(0.0, 0.1, 0.5, 1.0, 4.0)
        for (snr in snrs) for (d in directs) {
            val byEcho = t60s.map { Sti.sti(flat(it), flat(snr), flat(d)) }
            assertTrue(byEcho.zipWithNext().all { (a, b) -> b <= a + 1e-12 }, "SNR=$snr d=$d: $byEcho")
        }
        for (t60 in t60s) for (d in directs) {
            val bySnr = snrs.map { Sti.sti(flat(t60), flat(it), flat(d)) }
            assertTrue(bySnr.zipWithNext().all { (a, b) -> b >= a - 1e-12 }, "T60=$t60 d=$d: $bySnr")
        }
        for (t60 in t60s) for (snr in snrs) {
            val byDirect = directs.map { Sti.sti(flat(t60), flat(snr), flat(it)) }
            assertTrue(byDirect.zipWithNext().all { (a, b) -> b >= a - 1e-12 }, "T60=$t60 SNR=$snr: $byDirect")
        }
    }

    @Test
    fun `scores always lie between 0 and 1`() {
        for (t60 in listOf(0.0, 0.5, 6.0, 30.0)) for (snr in listOf(-60.0, 0.0, 60.0, Sti.NO_NOISE_DB)) {
            val sti = Sti.sti(flat(t60), flat(snr))
            assertTrue(sti in 0.0..1.0, "T60=$t60 SNR=$snr gave $sti")
        }
    }

    @Test
    fun `impossible inputs are refused rather than scored`() {
        assertFailsWith<IllegalArgumentException> { Sti.sti(DoubleArray(6), flat(10.0)) }
        assertFailsWith<IllegalArgumentException> { Sti.sti(flat(Double.NaN), flat(10.0)) }
        assertFailsWith<IllegalArgumentException> { Sti.sti(flat(-0.1), flat(10.0)) }
        assertFailsWith<IllegalArgumentException> { Sti.sti(flat(1.0), flat(Double.NaN)) }
        assertFailsWith<IllegalArgumentException> { Sti.sti(flat(1.0), flat(Double.NEGATIVE_INFINITY)) }
        assertFailsWith<IllegalArgumentException> { Sti.sti(flat(1.0), flat(10.0), flat(-1.0)) }
    }
}
