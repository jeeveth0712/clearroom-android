package com.clearroom.app.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max

/** Level reported for digital silence instead of -Infinity, so it stays finite and plottable. */
const val SILENCE_DB = -120.0

/**
 * RMS level of `samples[from until from + count]` in dB relative to full scale
 * (a constant full-scale signal is 0 dBFS, a full-scale sine about -3 dBFS).
 */
fun rmsDbFs(samples: FloatArray, from: Int = 0, count: Int = samples.size - from): Double {
    require(from >= 0 && count >= 0 && from + count <= samples.size) {
        "range from=$from count=$count is outside 0..${samples.size}"
    }
    if (count == 0) return SILENCE_DB
    var sumOfSquares = 0.0
    for (i in from until from + count) {
        val x = samples[i].toDouble()
        sumOfSquares += x * x
    }
    return meanSquareDb(sumOfSquares / count)
}

/** Largest absolute sample in dBFS. */
fun peakDbFs(samples: FloatArray): Double {
    var peak = 0.0
    for (x in samples) peak = max(peak, abs(x.toDouble()))
    return meanSquareDb(peak * peak)
}

/**
 * A mean-square (energy) value as dB, never below [SILENCE_DB] and never -Infinity.
 * NaN stays NaN on purpose: it means a bug upstream, and BandLevels refuses it loudly.
 */
fun meanSquareDb(meanSquare: Double): Double =
    if (meanSquare <= 0.0) SILENCE_DB else max(SILENCE_DB, 10 * log10(meanSquare))

/**
 * Exponential moving average of dB readings, so the live level bar moves smoothly instead of
 * jumping every 50 ms chunk. Stateful: one instance per recording.
 */
class LevelSmoother(private val timeConstantS: Double = 0.1) {
    private var current: Double? = null

    init {
        require(timeConstantS > 0.0) { "timeConstantS must be > 0, was $timeConstantS" }
    }

    /** Folds in a reading that covers [stepS] seconds and returns the smoothed level. */
    fun next(levelDb: Double, stepS: Double): Double {
        require(stepS > 0.0) { "stepS must be > 0, was $stepS" }
        val previous = current ?: levelDb // the first reading passes straight through
        val weight = 1.0 - exp(-stepS / timeConstantS)
        return (previous + weight * (levelDb - previous)).also { current = it }
    }
}
