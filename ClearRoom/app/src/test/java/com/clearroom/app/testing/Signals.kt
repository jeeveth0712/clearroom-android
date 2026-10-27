package com.clearroom.app.testing

import com.clearroom.app.selftest.SyntheticSignals
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Deterministic test signals; no randomness, so failures always reproduce. */
object Signals {
    fun sine(freqHz: Double, amplitude: Double, seconds: Double, sampleRate: Int = 48_000): FloatArray =
        FloatArray((seconds * sampleRate).roundToInt()) { i ->
            (amplitude * sin(2 * PI * freqHz * i / sampleRate)).toFloat()
        }

    fun constant(value: Float, count: Int): FloatArray = FloatArray(count) { value }

    /** A synthetic clap with a known echo time: the self-test's own generator, so tests and phone share it. */
    fun syntheticClap(
        t60S: Double,
        sampleRate: Int = 48_000,
        lengthS: Double = 5.0,
        clapAtS: Double = 1.0,
        floorBelowPeakDb: Double = 60.0,
        seed: Long = 42,
    ): FloatArray = SyntheticSignals.clap(t60S, sampleRate, lengthS, clapAtS, floorBelowPeakDb, seed)

    /** A quiet sine with [run] samples pinned at full scale in the middle. */
    fun clipped(run: Int, seconds: Double = 1.0, sampleRate: Int = 48_000): FloatArray =
        sine(1_000.0, 0.1, seconds, sampleRate).also { samples ->
            val start = samples.size / 2
            for (i in start until start + run) samples[i] = 1f
        }
}
