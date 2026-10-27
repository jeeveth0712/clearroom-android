package com.clearroom.app.selftest

import com.clearroom.app.audio.ClipQuality
import com.clearroom.app.dsp.OctaveBands
import com.clearroom.app.dsp.Reverb
import com.clearroom.app.dsp.Sti
import com.clearroom.app.measure.ClapProblem
import com.clearroom.app.measure.ClapTake
import com.clearroom.app.measure.ClapVerdict
import kotlin.math.abs
import kotlin.math.max

/** What the app makes of a recording: accepted as a clap, or refused with this advice (FR-2). */
enum class ClapOutcome { ACCEPTED, CLIPPED, SILENT, NO_CLAP, CLAP_LOUDER, TOO_NOISY, UNCLEAR }

/** Recordings the app must refuse rather than turn into an echo time. */
enum class RefusalKind { CLIPPED, SILENCE, STEADY_NOISE, BARELY_ABOVE_NOISE, ECHO_TOO_LONG }

/**
 * One check the phone runs on itself, with a known answer (doc "Analysis methods" step 4, NFR-3,
 * NFR-4). The unit tests run the very same list ([SelfTest.cases]), so the phone checks exactly
 * what the laptop checks.
 */
sealed interface SelfTestCase {
    /** NFR-4: STI from an echo time and signal-to-noise ratio per band, against the independent reference. */
    data class Clarity(val t60S: List<Double>, val snrDb: List<Double>, val expectedSti: Double) : SelfTestCase {
        init {
            require(t60S.size == OctaveBands.COUNT && snrDb.size == OctaveBands.COUNT) { "need a value per band" }
        }

        /** The same echo time and SNR in every band (all golden cases but the last). */
        val sameInEveryBand: Boolean get() = t60S.distinct().size == 1 && snrDb.distinct().size == 1
    }

    /** NFR-3: echo time from synthetic claps with a known decay, averaged over [seeds] like a seat's claps. */
    data class Echo(val t60S: Double, val floorBelowPeakDb: Double, val seeds: List<Long>) : SelfTestCase

    /** FR-2: a recording that must be refused, and the advice that may be given for it. */
    data class Refusal(val kind: RefusalKind, val allowed: Set<ClapOutcome>) : SelfTestCase
}

data class SelfTestResult(
    val case: SelfTestCase,
    val passed: Boolean,
    /** Clarity: the STI. Echo: the echo time measured at speech pitches (500 Hz–2 kHz), s. Null for refusals. */
    val value: Double? = null,
    /** Echo: the slowest single clap's analysis, ms (NFR-9 allows 5 s). */
    val slowestMillis: Long? = null,
    /** Refusal: what the app made of the recording. */
    val outcome: ClapOutcome? = null,
)

object SelfTest {
    /** NFR-4: STI within 0.02 of the expected value. */
    const val STI_TOLERANCE = 0.02

    /** NFR-9: a clap's result within 5 s. */
    const val MAX_ANALYSIS_MILLIS = 5_000L

    private val noNoise = List(OctaveBands.COUNT) { Sti.NO_NOISE_DB }

    private fun flat(value: Double) = List(OctaveBands.COUNT) { value }

    /**
     * The golden table (clearroom-testing skill), from
     * `.claude/skills/clearroom-testing/scripts/sti_reference.py --t60 T --snr S`, an independent
     * Python version of the doc's formulas. With no echo, STI = (SNR + 15) / 30 by hand.
     */
    private val clarityCases = listOf(
        SelfTestCase.Clarity(flat(0.0), noNoise, 1.0000),
        SelfTestCase.Clarity(flat(0.0), flat(0.0), 0.5000),
        SelfTestCase.Clarity(flat(0.0), flat(15.0), 1.0000),
        SelfTestCase.Clarity(flat(0.0), flat(-15.0), 0.0000),
        SelfTestCase.Clarity(flat(0.0), flat(6.0), 0.7000),
        SelfTestCase.Clarity(flat(0.3), noNoise, 0.8273),
        SelfTestCase.Clarity(flat(0.5), noNoise, 0.7361),
        SelfTestCase.Clarity(flat(0.6), noNoise, 0.6999),
        SelfTestCase.Clarity(flat(1.0), noNoise, 0.5882),
        SelfTestCase.Clarity(flat(1.5), noNoise, 0.4977),
        SelfTestCase.Clarity(flat(2.0), noNoise, 0.4371),
        SelfTestCase.Clarity(flat(0.6), flat(10.0), 0.6134),
        SelfTestCase.Clarity(flat(1.0), flat(5.0), 0.4634),
        SelfTestCase.Clarity(flat(1.5), flat(0.0), 0.3109),
        // sti_reference.py --t60 0.5,0.6,0.7,0.8,0.8,0.7,0.6 --snr 20,18,15,12,10,8,6
        SelfTestCase.Clarity(listOf(0.5, 0.6, 0.7, 0.8, 0.8, 0.7, 0.6), listOf(20.0, 18.0, 15.0, 12.0, 10.0, 8.0, 6.0), 0.5913),
    )

    /**
     * The skill's grid: echo times 0.3–2 s with the noise floor 45 or 60 dB under the clap, three
     * claps each like a seat. The seeds are the first trial of each case in ReverbTest's seat check.
     */
    private val echoCases = listOf(0.3, 0.5, 0.8, 1.2, 2.0).flatMapIndexed { i, t60 ->
        listOf(45.0, 60.0).mapIndexed { j, floor ->
            val first = 1_000L + 12 * (2 * i + j)
            SelfTestCase.Echo(t60, floor, listOf(first, first + 1, first + 2))
        }
    }

    private val refused = ClapOutcome.entries.toSet() - ClapOutcome.ACCEPTED

    private val refusalCases = listOf(
        SelfTestCase.Refusal(RefusalKind.CLIPPED, setOf(ClapOutcome.CLIPPED)),
        SelfTestCase.Refusal(RefusalKind.SILENCE, setOf(ClapOutcome.SILENT)),
        SelfTestCase.Refusal(RefusalKind.STEADY_NOISE, setOf(ClapOutcome.NO_CLAP)),
        SelfTestCase.Refusal(RefusalKind.BARELY_ABOVE_NOISE, setOf(ClapOutcome.CLAP_LOUDER, ClapOutcome.TOO_NOISY)),
        // Whatever the advice, the app must not invent an echo time for a decay no room has.
        SelfTestCase.Refusal(RefusalKind.ECHO_TOO_LONG, refused),
    )

    val cases: List<SelfTestCase> = clarityCases + echoCases + refusalCases

    /** Runs one case. Pure maths on generated signals: call it off the main thread. */
    fun run(case: SelfTestCase, nanoTime: () -> Long = System::nanoTime): SelfTestResult = when (case) {
        is SelfTestCase.Clarity -> {
            val sti = Sti.sti(case.t60S.toDoubleArray(), case.snrDb.toDoubleArray())
            SelfTestResult(case, passed = abs(sti - case.expectedSti) <= STI_TOLERANCE, value = sti)
        }
        is SelfTestCase.Echo -> runEcho(case, nanoTime)
        is SelfTestCase.Refusal -> {
            val outcome = outcomeOf(recording(case.kind))
            SelfTestResult(case, passed = outcome in case.allowed, outcome = outcome)
        }
    }

    /** NFR-3: within ±10 % or ±0.1 s, whichever is larger. */
    fun withinNfr3(expectedS: Double, measuredS: Double): Boolean = abs(measuredS - expectedS) <= max(0.10 * expectedS, 0.10)

    /**
     * 125 Hz is outside FR-3's 250 Hz–4 kHz, and the doc's risk table expects weak, scattered claps
     * there: ±25 % or ±0.15 s, the allowance ReverbTest uses.
     */
    fun withinLowBandAllowance(expectedS: Double, measuredS: Double): Boolean = abs(measuredS - expectedS) <= max(0.25 * expectedS, 0.15)

    /** What the app would tell the user about [samples]: the clip check first, then the clap analysis. */
    fun outcomeOf(samples: FloatArray, sampleRate: Int = SyntheticSignals.SAMPLE_RATE): ClapOutcome {
        when (ClipQuality.of(samples)) {
            ClipQuality.CLIPPED -> return ClapOutcome.CLIPPED
            ClipQuality.SILENT -> return ClapOutcome.SILENT
            ClipQuality.OK -> Unit
        }
        return when (val verdict = ClapTake.judge(Reverb.analyse(samples, sampleRate))) {
            is ClapVerdict.Accepted -> ClapOutcome.ACCEPTED
            is ClapVerdict.Rejected -> when (verdict.problem) {
                ClapProblem.NO_CLAP -> ClapOutcome.NO_CLAP
                ClapProblem.CLAP_LOUDER -> ClapOutcome.CLAP_LOUDER
                ClapProblem.TOO_NOISY -> ClapOutcome.TOO_NOISY
                ClapProblem.UNCLEAR -> ClapOutcome.UNCLEAR
            }
        }
    }

    private fun runEcho(case: SelfTestCase.Echo, nanoTime: () -> Long): SelfTestResult {
        var slowestNanos = 0L
        val takes = case.seeds.map { seed ->
            val samples = SyntheticSignals.clap(case.t60S, floorBelowPeakDb = case.floorBelowPeakDb, seed = seed)
            val start = nanoTime()
            val verdict = ClapTake.judge(Reverb.analyse(samples, SyntheticSignals.SAMPLE_RATE))
            slowestNanos = max(slowestNanos, nanoTime() - start)
            (verdict as? ClapVerdict.Accepted)?.take
        }
        val accepted = takes.filterNotNull()
        // Per band, the mean of the claps that gave a value there, like a seat's result (FR-3).
        val means = List(OctaveBands.COUNT) { band -> accepted.mapNotNull { it.t60S[band] }.takeIf { it.isNotEmpty() }?.average() }
        val bandsOk = means.indices.all { band ->
            val mean = means[band]
            if (band == 0) mean == null || withinLowBandAllowance(case.t60S, mean) else mean != null && withinNfr3(case.t60S, mean)
        }
        val slowestMillis = slowestNanos / 1_000_000
        return SelfTestResult(
            case,
            passed = accepted.size == case.seeds.size && bandsOk && slowestMillis <= MAX_ANALYSIS_MILLIS,
            value = OctaveBands.SPEECH_BANDS.mapNotNull { means[it] }.takeIf { it.isNotEmpty() }?.average(),
            slowestMillis = slowestMillis,
        )
    }

    private fun recording(kind: RefusalKind): FloatArray = when (kind) {
        // A good clap ten times too loud for the microphone.
        RefusalKind.CLIPPED -> SyntheticSignals.clap(0.8, seed = 7).map { (it * 10).coerceIn(-1f, 1f) }.toFloatArray()
        // What Android hands an app while another one holds the microphone.
        RefusalKind.SILENCE -> FloatArray(5 * SyntheticSignals.SAMPLE_RATE)
        // The "clap" no louder than the room's noise.
        RefusalKind.STEADY_NOISE -> SyntheticSignals.clap(0.8, floorBelowPeakDb = 0.0, seed = 8)
        // A decay only 20 dB above the noise: too short to measure.
        RefusalKind.BARELY_ABOVE_NOISE -> SyntheticSignals.clap(0.8, floorBelowPeakDb = 20.0, seed = 9)
        // 12 s, far beyond any room (the doc accepts 0.1–6 s).
        RefusalKind.ECHO_TOO_LONG -> SyntheticSignals.clap(12.0, seed = 10)
    }
}
