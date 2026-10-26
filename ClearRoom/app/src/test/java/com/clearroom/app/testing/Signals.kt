package com.clearroom.app.testing

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Deterministic test signals; no randomness, so failures always reproduce. */
object Signals {
    fun sine(freqHz: Double, amplitude: Double, seconds: Double, sampleRate: Int = 48_000): FloatArray =
        FloatArray((seconds * sampleRate).roundToInt()) { i ->
            (amplitude * sin(2 * PI * freqHz * i / sampleRate)).toFloat()
        }

    fun constant(value: Float, count: Int): FloatArray = FloatArray(count) { value }

    /**
     * A synthetic clap (clearroom-testing skill): white noise whose energy falls 60 dB in [t60S],
     * starting at [clapAtS], on top of a steady noise floor [floorBelowPeakDb] under the decay's
     * start. Seeded, so every run gives the same samples. Every octave band decays at [t60S].
     */
    fun syntheticClap(
        t60S: Double,
        sampleRate: Int = 48_000,
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

    /** A quiet sine with [run] samples pinned at full scale in the middle. */
    fun clipped(run: Int, seconds: Double = 1.0, sampleRate: Int = 48_000): FloatArray =
        sine(1_000.0, 0.1, seconds, sampleRate).also { samples ->
            val start = samples.size / 2
            for (i in start until start + run) samples[i] = 1f
        }
}
