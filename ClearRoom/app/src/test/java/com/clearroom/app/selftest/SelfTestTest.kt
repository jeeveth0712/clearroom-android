package com.clearroom.app.selftest

import com.clearroom.app.dsp.OctaveBands
import com.clearroom.app.dsp.Sti
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The self-test's own cases, run on the laptop: the phone must pass exactly these. */
class SelfTestTest {

    private val results get() = allResults

    @Test
    fun `every self-test case passes`() {
        val failed = results.filterNot { it.passed }
        assertTrue(failed.isEmpty(), "failed: ${failed.joinToString("\n") { "${it.case} -> value ${it.value}, outcome ${it.outcome}, ${it.slowestMillis} ms" }}")
    }

    @Test
    fun `the self-test covers the golden table, the echo grid and the refusals`() {
        val cases = SelfTest.cases
        assertEquals(15, cases.count { it is SelfTestCase.Clarity })
        assertEquals(10, cases.count { it is SelfTestCase.Echo })
        assertEquals(RefusalKind.entries.toSet(), cases.filterIsInstance<SelfTestCase.Refusal>().map { it.kind }.toSet())
        assertEquals(cases.size, cases.distinct().size, "no case twice")
    }

    @Test
    fun `clarity results are the engine's STI, held to NFR-4`() {
        val case = SelfTestCase.Clarity(List(7) { 1.0 }, List(7) { Sti.NO_NOISE_DB }, 0.5882)
        val result = SelfTest.run(case)
        assertEquals(Sti.sti(DoubleArray(7) { 1.0 }, DoubleArray(7) { Sti.NO_NOISE_DB }), result.value)
        assertFalse(SelfTest.run(case.copy(expectedSti = 0.5882 + 0.021)).passed, "0.021 off is outside NFR-4")
        assertTrue(SelfTest.run(case.copy(expectedSti = 0.5882 + 0.019)).passed)
    }

    @Test
    fun `NFR-3 allows 10 percent or 0,1 s, whichever is larger`() {
        assertTrue(SelfTest.withinNfr3(2.0, 2.19))
        assertFalse(SelfTest.withinNfr3(2.0, 2.21))
        assertTrue(SelfTest.withinNfr3(0.3, 0.39)) // 0.1 s beats 10 % of 0.3 s
        assertFalse(SelfTest.withinNfr3(0.3, 0.41))
        assertTrue(SelfTest.withinLowBandAllowance(0.3, 0.44))
        assertFalse(SelfTest.withinLowBandAllowance(2.0, 2.6))
    }

    @Test
    fun `echo results report the speech-pitch echo time and how long a clap took`() {
        val result = results.first { (it.case as? SelfTestCase.Echo)?.t60S == 0.8 }
        assertTrue(SelfTest.withinNfr3(0.8, requireNotNull(result.value)))
        assertTrue(requireNotNull(result.slowestMillis) in 0..SelfTest.MAX_ANALYSIS_MILLIS)
    }

    @Test
    fun `an echo case fails when its claps give no echo time`() {
        val impossible = SelfTestCase.Echo(t60S = 12.0, floorBelowPeakDb = 60.0, seeds = listOf(1_000L, 1_001L))
        val result = SelfTest.run(impossible)
        assertFalse(result.passed)
        assertEquals(null, result.value)
    }

    @Test
    fun `a slow phone fails the timing part of an echo case`() {
        var now = 0L
        val slowClock = { now.also { now += 6_000_000_000L } } // every reading 6 s later
        val result = SelfTest.run(SelfTest.cases.filterIsInstance<SelfTestCase.Echo>().first(), slowClock)
        assertEquals(6_000L, result.slowestMillis)
        assertFalse(result.passed)
    }

    @Test
    fun `each refusal gets the advice the app really gives`() {
        val outcomes = results.filter { it.case is SelfTestCase.Refusal }.associate { (it.case as SelfTestCase.Refusal).kind to it.outcome }
        assertEquals(ClapOutcome.CLIPPED, outcomes[RefusalKind.CLIPPED])
        assertEquals(ClapOutcome.SILENT, outcomes[RefusalKind.SILENCE])
        assertEquals(ClapOutcome.NO_CLAP, outcomes[RefusalKind.STEADY_NOISE])
        assertTrue(outcomes[RefusalKind.BARELY_ABOVE_NOISE] in setOf(ClapOutcome.CLAP_LOUDER, ClapOutcome.TOO_NOISY))
        assertTrue(outcomes[RefusalKind.ECHO_TOO_LONG] != ClapOutcome.ACCEPTED)
    }

    @Test
    fun `a good clap is accepted, so the refusals aren't refusing everything`() {
        assertEquals(ClapOutcome.ACCEPTED, SelfTest.outcomeOf(SyntheticSignals.clap(0.8)))
    }

    @Test
    fun `clarity cases need a value for every band`() {
        val short = List(OctaveBands.COUNT - 1) { 1.0 }
        assertFailsWith<IllegalArgumentException> { SelfTestCase.Clarity(short, short, 0.5) }
    }

    private companion object {
        /** Run once for the class: JUnit makes a new instance per test. */
        val allResults: List<SelfTestResult> by lazy { SelfTest.cases.map { SelfTest.run(it) } }
    }
}
