package com.clearroom.app.dsp

import com.clearroom.app.dsp.DspConstants.CURVE_STEP_S
import com.clearroom.app.dsp.DspConstants.ENVELOPE_SMOOTHING_S
import com.clearroom.app.dsp.DspConstants.MAX_NONLINEARITY_PERMILLE
import com.clearroom.app.dsp.DspConstants.MAX_T60_S
import com.clearroom.app.dsp.DspConstants.MIN_AFTER_CLAP_S
import com.clearroom.app.dsp.DspConstants.MIN_CLAP_PROMINENCE_DB
import com.clearroom.app.dsp.DspConstants.MIN_DYNAMIC_RANGE_DB
import com.clearroom.app.dsp.DspConstants.MIN_FIT_SPAN_S
import com.clearroom.app.dsp.DspConstants.MIN_T60_S
import com.clearroom.app.dsp.DspConstants.PRE_CLAP_GAP_S
import com.clearroom.app.dsp.DspConstants.PRE_CLAP_NOISE_S
import com.clearroom.app.dsp.DspConstants.T20_FROM_DB
import com.clearroom.app.dsp.DspConstants.T20_TO_DB
import com.clearroom.app.dsp.DspConstants.T30_TO_DB
import com.clearroom.app.dsp.DspConstants.TAIL_NOISE_S
import com.clearroom.app.dsp.DspConstants.TRUNCATE_ABOVE_NOISE_DB
import com.clearroom.app.dsp.DspConstants.WATERFALL_FLOOR_DB
import com.clearroom.app.dsp.DspConstants.WATERFALL_SPAN_S
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Why one band of a clap couldn't give an echo time. */
enum class RejectReason { LOW_DYNAMIC_RANGE, FIT_POOR, OUT_OF_RANGE }

/** One octave band of one clap. */
sealed interface BandDecay {
    /** Noise floor of this band in dBFS; tells "clap louder" apart from "room too noisy". */
    val noiseFloorDb: Double

    data class Ok(
        val t20S: Double,
        val t30S: Double?,
        val xiPermille: Double,
        val dynamicRangeDb: Double,
        override val noiseFloorDb: Double,
        /** Schroeder curve in dB, every [CURVE_STEP_S] from the band's peak to the truncation point (V3). */
        val schroederDb: List<Double>,
        /** The T20 fit: level = interceptDb + slopeDbPerS · t, t in s from the band's peak (V3). */
        val fitSlopeDbPerS: Double,
        val fitInterceptDb: Double,
    ) : BandDecay

    data class Rejected(val reason: RejectReason, val dynamicRangeDb: Double, override val noiseFloorDb: Double) : BandDecay
}

sealed interface ReverbResult {
    /** No clear clap: nothing stood out, or it came too early or too late to analyse. */
    data object NoClap : ReverbResult

    data class Analysed(
        val bands: List<BandDecay>,
        /** V10 echo waterfall: per band, level in dB every [CURVE_STEP_S] for [WATERFALL_SPAN_S], floor -60 dB. */
        val waterfallDb: List<List<Double>>,
    ) : ReverbResult
}

/**
 * Clap → echo time (RT60) per octave band, following the doc's "Analysis methods" step 1:
 * band-pass, square, Schroeder backward integration, then a T20 line fit extrapolated to 60 dB.
 * Pure Kotlin on DoubleArray; each step is a small function that can be tested alone.
 */
object Reverb {

    fun analyse(samples: FloatArray, sampleRate: Int): ReverbResult {
        require(sampleRate > 0) { "sampleRate must be > 0, was $sampleRate" }
        val signal = DoubleArray(samples.size) { samples[it].toDouble() }
        val clap = findClap(signal, sampleRate) ?: return ReverbResult.NoClap
        val waterfall = ArrayList<List<Double>>(OctaveBands.COUNT)
        val bands = OctaveBands.CENTRES_HZ.map { centreHz ->
            val band = BandSignal.of(signal, centreHz.toDouble(), sampleRate, clap)
            waterfall += band.waterfallDb()
            band.decay()
        }
        return ReverbResult.Analysed(bands, waterfall)
    }

    /**
     * Index where the clap starts, if it leaves room before and after and stands out from the
     * background. The loudest moment can come tens of ms after the start (a strong reflection, or
     * a random peak in a slow decay), so the onset is where the energy first rose to within
     * [ONSET_BELOW_PEAK_DB] of that loudest moment. The noise window must end before the onset.
     */
    internal fun findClap(signal: DoubleArray, sampleRate: Int): Int? {
        if (signal.isEmpty()) return null
        val squares = squared(signal)
        val envelope = movingAverage(squares, samples(ENVELOPE_SMOOTHING_S, sampleRate))
        var loudest = 0
        for (i in envelope.indices) if (envelope[i] > envelope[loudest]) loudest = i
        val riseLimit = envelope[loudest] * 10.0.pow(-ONSET_BELOW_PEAK_DB / 10)
        var onset = loudest
        val earliest = max(0, loudest - samples(ONSET_LOOKBACK_S, sampleRate))
        while (onset > earliest && envelope[onset - 1] >= riseLimit) onset--

        val preStart = onset - samples(PRE_CLAP_NOISE_S, sampleRate)
        if (preStart < 0 || onset > signal.size - samples(MIN_AFTER_CLAP_S, sampleRate)) return null
        val background = mean(squares, preStart, onset - samples(PRE_CLAP_GAP_S, sampleRate))
        return if (ratioDb(envelope[loudest], background) >= MIN_CLAP_PROMINENCE_DB) onset else null
    }

    /** The clap onset is where the energy first comes within this much of its loudest point. */
    private const val ONSET_BELOW_PEAK_DB = 20.0

    /** How far before the loudest moment the onset may lie. */
    private const val ONSET_LOOKBACK_S = 0.1

    /** One band of the clip, filtered and ready for the decay analysis. */
    private class BandSignal(
        val squares: DoubleArray,
        val envelope: DoubleArray,
        val sampleRate: Int,
        val peak: Int,
        val noiseEnergy: Double,
    ) {
        val noiseFloorDb = meanSquareDb(noiseEnergy)
        val dynamicRangeDb = ratioDb(envelope[peak], noiseEnergy)

        fun decay(): BandDecay {
            if (dynamicRangeDb < MIN_DYNAMIC_RANGE_DB) return rejected(RejectReason.LOW_DYNAMIC_RANGE)
            val curve = schroederDb(squares, peak, truncationIndex())
            val t20 = fitLine(curve, T20_FROM_DB, T20_TO_DB, sampleRate) ?: return rejected(RejectReason.LOW_DYNAMIC_RANGE)
            if (t20.spanS < MIN_FIT_SPAN_S || t20.slopeDbPerS >= 0.0) return rejected(RejectReason.FIT_POOR)
            val t60 = -60.0 / t20.slopeDbPerS
            if (t60 !in MIN_T60_S..MAX_T60_S) return rejected(RejectReason.OUT_OF_RANGE)
            val xi = 1_000 * (1 - t20.rSquared)
            if (xi > MAX_NONLINEARITY_PERMILLE) return rejected(RejectReason.FIT_POOR)
            val t30 = fitLine(curve, T20_FROM_DB, T30_TO_DB, sampleRate)
                ?.takeIf { it.slopeDbPerS < 0.0 }
                ?.let { -60.0 / it.slopeDbPerS }
                ?.takeIf { it in MIN_T60_S..MAX_T60_S }
            return BandDecay.Ok(
                t20S = t60,
                t30S = t30,
                xiPermille = xi,
                dynamicRangeDb = dynamicRangeDb,
                noiseFloorDb = noiseFloorDb,
                schroederDb = everyStep(curve, sampleRate),
                fitSlopeDbPerS = t20.slopeDbPerS,
                fitInterceptDb = t20.interceptDb,
            )
        }

        fun waterfallDb(): List<Double> {
            val step = samples(CURVE_STEP_S, sampleRate)
            val reference = envelope[peak]
            return List((WATERFALL_SPAN_S / CURVE_STEP_S).roundToInt()) { column ->
                val index = peak + column * step
                if (index >= envelope.size) WATERFALL_FLOOR_DB else max(WATERFALL_FLOOR_DB, ratioDb(envelope[index], reference))
            }
        }

        /** Doc: the first point after the peak where the decay has sunk to noise + 5 dB. */
        private fun truncationIndex(): Int {
            val limit = noiseEnergy * 10.0.pow(TRUNCATE_ABOVE_NOISE_DB / 10)
            for (i in peak until envelope.size) if (envelope[i] <= limit) return i
            return envelope.size
        }

        private fun rejected(reason: RejectReason) = BandDecay.Rejected(reason, dynamicRangeDb, noiseFloorDb)

        companion object {
            fun of(signal: DoubleArray, centreHz: Double, sampleRate: Int, clap: Int): BandSignal {
                val filtered = filterTimeReversed(signal, centreHz, sampleRate)
                val squares = squared(filtered)
                val envelope = movingAverage(squares, samples(ENVELOPE_SMOOTHING_S, sampleRate))
                // The band's own peak sits within a few ms of the broadband clap.
                val searchFrom = max(0, clap - samples(0.02, sampleRate))
                val searchTo = min(envelope.size, clap + samples(0.1, sampleRate))
                val peak = (searchFrom until searchTo).maxBy { envelope[it] }
                val preClap = mean(
                    squares,
                    clap - samples(PRE_CLAP_NOISE_S, sampleRate),
                    clap - samples(PRE_CLAP_GAP_S, sampleRate),
                )
                val tail = mean(squares, squares.size - samples(TAIL_NOISE_S, sampleRate), squares.size)
                return BandSignal(squares, envelope, sampleRate, peak, noiseEnergy = min(preClap, tail))
            }
        }
    }

    // --- Small steps, each tested on its own --------------------------------------------------

    /**
     * Band-pass the time-reversed clip and reverse the result back: the filters' own ringing then
     * falls before the clap instead of lengthening the decay (allowed by ISO 3382-1).
     */
    internal fun filterTimeReversed(signal: DoubleArray, centreHz: Double, sampleRate: Int): DoubleArray {
        val first = Biquad.bandPass(centreHz, DspConstants.BAND_FILTER_Q, sampleRate)
        val second = Biquad.bandPass(centreHz, DspConstants.BAND_FILTER_Q, sampleRate)
        val out = DoubleArray(signal.size)
        for (i in signal.indices.reversed()) out[i] = second.process(first.process(signal[i]))
        return out
    }

    /**
     * Schroeder backward integration (doc formula E(t) = ∫ₜ^∞ p² dτ), truncated at [end], in dB
     * relative to its value at [start]: 0 dB at the clap, falling from there.
     */
    internal fun schroederDb(squares: DoubleArray, start: Int, end: Int): DoubleArray {
        require(start in 0 until end && end <= squares.size) { "bad range $start..$end of ${squares.size}" }
        val energy = DoubleArray(end - start)
        var sum = 0.0
        for (i in end - 1 downTo start) {
            sum += squares[i]
            energy[i - start] = sum
        }
        val total = energy[0]
        return DoubleArray(energy.size) { ratioDb(energy[it], total) }
    }

    /** Least-squares line through the curve between [fromDb] and [toDb]; null if the curve never reaches [toDb]. */
    internal fun fitLine(curveDb: DoubleArray, fromDb: Double, toDb: Double, sampleRate: Int): LineFit? {
        val start = curveDb.indexOfFirst { it <= fromDb }
        val end = curveDb.indexOfFirst { it <= toDb }
        if (start < 0 || end < 0 || end - start < 2) return null
        val n = end - start
        var sumT = 0.0
        var sumL = 0.0
        for (i in start until end) {
            sumT += i.toDouble() / sampleRate
            sumL += curveDb[i]
        }
        val meanT = sumT / n
        val meanL = sumL / n
        var sTT = 0.0
        var sTL = 0.0
        var sLL = 0.0
        for (i in start until end) {
            val dt = i.toDouble() / sampleRate - meanT
            val dl = curveDb[i] - meanL
            sTT += dt * dt
            sTL += dt * dl
            sLL += dl * dl
        }
        if (sTT <= 0.0) return null
        val slope = sTL / sTT
        val rSquared = if (sLL <= 0.0) 1.0 else (sTL * sTL) / (sTT * sLL)
        return LineFit(slope, meanL - slope * meanT, rSquared, spanS = n.toDouble() / sampleRate)
    }

    internal data class LineFit(val slopeDbPerS: Double, val interceptDb: Double, val rSquared: Double, val spanS: Double)

    /** Centred moving average over [window] samples (shorter at the edges). */
    internal fun movingAverage(values: DoubleArray, window: Int): DoubleArray {
        require(window >= 1) { "window must be >= 1, was $window" }
        val prefix = DoubleArray(values.size + 1)
        for (i in values.indices) prefix[i + 1] = prefix[i] + values[i]
        val half = window / 2
        return DoubleArray(values.size) { i ->
            val from = max(0, i - half)
            val to = min(values.size, i + half + 1)
            (prefix[to] - prefix[from]) / (to - from)
        }
    }

    private fun everyStep(curve: DoubleArray, sampleRate: Int): List<Double> {
        val step = max(1, samples(CURVE_STEP_S, sampleRate))
        return List((curve.size + step - 1) / step) { curve[it * step] }
    }

    private fun squared(x: DoubleArray) = DoubleArray(x.size) { x[it] * x[it] }

    private fun mean(values: DoubleArray, from: Int, until: Int): Double {
        val start = max(0, from)
        val end = min(values.size, until)
        if (end <= start) return 0.0
        var sum = 0.0
        for (i in start until end) sum += values[i]
        return sum / (end - start)
    }

    /** 10·log10(a / b) for energies, without -Infinity or division by zero. */
    internal fun ratioDb(a: Double, b: Double): Double = 10 * log10(max(a, TINY_ENERGY) / max(b, TINY_ENERGY))

    private const val TINY_ENERGY = 1e-30

    private fun samples(seconds: Double, sampleRate: Int) = (seconds * sampleRate).roundToInt()
}
