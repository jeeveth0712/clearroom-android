package com.clearroom.app.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One second-order filter section (transposed direct form II, in Double). Stateful: build a fresh
 * one per signal, or call [reset], or the previous signal's tail leaks into the next.
 */
class Biquad private constructor(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    companion object {
        /** RBJ cookbook band-pass with 0 dB gain at [centerHz]. */
        fun bandPass(centerHz: Double, q: Double, sampleRate: Int): Biquad {
            require(sampleRate > 0) { "sampleRate must be > 0, was $sampleRate" }
            require(centerHz > 0.0 && centerHz < sampleRate / 2.0) { "band $centerHz Hz must lie below Nyquist (${sampleRate / 2} Hz)" }
            require(q > 0.0) { "q must be > 0, was $q" }
            val w0 = 2 * PI * centerHz / sampleRate
            val alpha = sin(w0) / (2 * q)
            val a0 = 1 + alpha
            return Biquad(
                b0 = alpha / a0,
                b1 = 0.0,
                b2 = -alpha / a0,
                a1 = -2 * cos(w0) / a0,
                a2 = (1 - alpha) / a0,
            )
        }
    }
}
