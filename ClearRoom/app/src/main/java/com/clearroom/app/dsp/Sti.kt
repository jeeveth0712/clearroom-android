package com.clearroom.app.dsp

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Indirect Speech Transmission Index (doc "Analysis methods" step 4): how much of speech's slow
 * loudness modulation survives echo and noise in each octave band, weighted into one 0–1 score.
 * Simplified per the doc: IEC 60268-16's auditory masking and reception threshold aren't modelled,
 * which is why results are shown as bands with ranges (NFR-10), never as certified values.
 */
object Sti {
    /** Doc "Exact parameters": the 14 modulation frequencies, 0.63–12.5 Hz. */
    val MOD_FREQS_HZ = doubleArrayOf(0.63, 0.8, 1.0, 1.25, 1.6, 2.0, 2.5, 3.15, 4.0, 5.0, 6.3, 8.0, 10.0, 12.5)

    /** Doc "Exact parameters": male band weights (IEC 60268-16); ALPHA.sum() − BETA.sum() = 1. */
    val ALPHA = doubleArrayOf(0.085, 0.127, 0.230, 0.233, 0.309, 0.224, 0.173)

    /** Redundancy factors between neighbouring bands. */
    val BETA = doubleArrayOf(0.085, 0.078, 0.065, 0.011, 0.047, 0.095)

    /** Apparent SNR is clipped to ±15 dB (doc formula). */
    const val SNR_APP_LIMIT_DB = 15.0

    /** "No noise at all": the noise-removed variant used for the limiting cause. */
    const val NO_NOISE_DB = Double.POSITIVE_INFINITY

    /**
     * Modulation index for one band and modulation frequency.
     *
     * The doc's formula, m = 1/√(1 + (2πF·T/13.8)²) · 1/(1 + 10^(−SNR/10)), treats all speech as
     * reverberant. [directToReverberant] adds the direct sound (Houtgast & Steeneken): the direct
     * part arrives unsmeared, so m_reverb becomes |d + 1/(1 + jx)| / (d + 1) with x = 2πF·T/13.8.
     * With d = 0 this is exactly the doc's formula.
     */
    fun modulationIndex(fModHz: Double, t60S: Double, snrDb: Double, directToReverberant: Double = 0.0): Double {
        val x = 2 * PI * fModHz * t60S / 13.8
        val real = directToReverberant + 1 / (1 + x * x)
        val imaginary = x / (1 + x * x)
        val reverb = sqrt(real * real + imaginary * imaginary) / (directToReverberant + 1)
        val noise = if (snrDb == NO_NOISE_DB) 1.0 else 1 / (1 + 10.0.pow(-snrDb / 10))
        return reverb * noise
    }

    /** Transmission index: apparent SNR from m, clipped to ±15 dB, mapped to 0–1. */
    fun transmissionIndex(m: Double): Double {
        val snrApp = when {
            m >= 1.0 -> SNR_APP_LIMIT_DB
            m <= 0.0 -> -SNR_APP_LIMIT_DB
            else -> (10 * log10(m / (1 - m))).coerceIn(-SNR_APP_LIMIT_DB, SNR_APP_LIMIT_DB)
        }
        return (snrApp + SNR_APP_LIMIT_DB) / (2 * SNR_APP_LIMIT_DB)
    }

    /** Modulation transfer index of one band: the mean TI over the 14 modulation frequencies. */
    fun bandMti(t60S: Double, snrDb: Double, directToReverberant: Double = 0.0): Double =
        MOD_FREQS_HZ.sumOf { transmissionIndex(modulationIndex(it, t60S, snrDb, directToReverberant)) } / MOD_FREQS_HZ.size

    /**
     * STI from per-band echo time, SNR and (optionally) direct-to-reverberant energy ratio:
     * Σ αₖ·MTIₖ − Σ βₖ·√(MTIₖ·MTIₖ₊₁), clamped to [0, 1].
     */
    fun sti(t60PerBand: DoubleArray, snrPerBand: DoubleArray, directToReverberant: DoubleArray = DoubleArray(BANDS)): Double {
        require(t60PerBand.size == BANDS && snrPerBand.size == BANDS && directToReverberant.size == BANDS) { "need $BANDS bands" }
        require(t60PerBand.all { it.isFinite() && it >= 0.0 }) { "T60 must be finite and >= 0: ${t60PerBand.contentToString()}" }
        require(snrPerBand.none { it.isNaN() || it == Double.NEGATIVE_INFINITY }) { "bad SNR: ${snrPerBand.contentToString()}" }
        require(directToReverberant.all { it.isFinite() && it >= 0.0 }) { "bad direct ratio: ${directToReverberant.contentToString()}" }
        val mti = DoubleArray(BANDS) { bandMti(t60PerBand[it], snrPerBand[it], directToReverberant[it]) }
        val weighted = (0 until BANDS).sumOf { ALPHA[it] * mti[it] }
        val redundancy = (0 until BANDS - 1).sumOf { BETA[it] * sqrt(mti[it] * mti[it + 1]) }
        return (weighted - redundancy).coerceIn(0.0, 1.0)
    }

    private val BANDS = OctaveBands.COUNT
}
