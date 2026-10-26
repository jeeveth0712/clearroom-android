package com.clearroom.app.dsp

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The seven octave bands of [OctaveBands]: each band is two RBJ band-pass sections in series
 * (doc parameter), built fresh for every call so no state leaks between clips.
 */
object OctaveBank {

    /**
     * Leq of each octave band in dBFS, after skipping the filters' start-up transient
     * ([DspConstants.FILTER_SETTLE_S]). Streams through the samples: no filtered copies are kept.
     */
    fun bandLevelsDb(samples: FloatArray, sampleRate: Int): DoubleArray {
        require(sampleRate > 0) { "sampleRate must be > 0, was $sampleRate" }
        val settle = minOf((DspConstants.FILTER_SETTLE_S * sampleRate).roundToInt(), samples.size)
        val counted = samples.size - settle
        return DoubleArray(OctaveBands.COUNT) { band ->
            val centreHz = OctaveBands.CENTRES_HZ[band].toDouble()
            val first = Biquad.bandPass(centreHz, DspConstants.BAND_FILTER_Q, sampleRate)
            val second = Biquad.bandPass(centreHz, DspConstants.BAND_FILTER_Q, sampleRate)
            var sumOfSquares = 0.0
            for (i in samples.indices) {
                val y = second.process(first.process(samples[i].toDouble()))
                if (i >= settle) sumOfSquares += y * y
            }
            if (counted == 0) SILENCE_DB else meanSquareDb(sumOfSquares / counted)
        }
    }

    /** Approximate A-weighted total of seven band levels (see [DspConstants.A_WEIGHTING_DB]). */
    fun aWeightedDb(bandLevelsDb: List<Double>): Double {
        require(bandLevelsDb.size == OctaveBands.COUNT) { "need ${OctaveBands.COUNT} bands, got ${bandLevelsDb.size}" }
        val energy = bandLevelsDb.indices.sumOf { band ->
            10.0.pow((bandLevelsDb[band] + DspConstants.A_WEIGHTING_DB[band]) / 10)
        }
        return meanSquareDb(energy)
    }
}
