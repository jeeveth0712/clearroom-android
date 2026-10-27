package com.clearroom.app.selftest

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Recordings with known answers, made on the phone for the self-test (doc "Analysis methods"
 * step 4) and used by the unit tests too: one generator, so the phone checks what the tests check.
 */
object SyntheticSignals {
    const val SAMPLE_RATE = 48_000

    /**
     * A synthetic clap (clearroom-testing skill): white noise whose energy falls 60 dB in [t60S],
     * starting at [clapAtS], on top of a steady noise floor [floorBelowPeakDb] under the decay's
     * start. Seeded, so every run gives the same samples. Every octave band decays at [t60S].
     */
    fun clap(
        t60S: Double,
        sampleRate: Int = SAMPLE_RATE,
        lengthS: Double = 5.0,
        clapAtS: Double = 1.0,
        floorBelowPeakDb: Double = 60.0,
        seed: Long = 42,
    ): FloatArray {
        val rng = java.util.Random(seed)
        val peak = 0.2 // 4.5 sigma stays below 0.99, so the clip is never flagged as clipped
        val floor = peak * 10.0.pow(-floorBelowPeakDb / 20)
        return FloatArray((lengthS * sampleRate).roundToInt()) { i ->
            val t = i.toDouble() / sampleRate - clapAtS
            val decay = if (t < 0) 0.0 else peak * exp(-6.9078 * t / t60S) // ln(1000): amplitude -60 dB at t60S
            (sqrt(decay * decay + floor * floor) * rng.nextGaussian()).toFloat()
        }
    }
}
