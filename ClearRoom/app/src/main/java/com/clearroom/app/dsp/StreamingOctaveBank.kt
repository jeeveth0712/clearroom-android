package com.clearroom.app.dsp

import kotlin.math.roundToInt

/**
 * The seven octave bands for a continuous stream (the day log): unlike [OctaveBank], the filters
 * keep their state from one block to the next, so a new block doesn't restart them. Restarting
 * every second would add a start-up transient each time, seen as a 1 Hz ripple in the low bands.
 * Only the very first block skips the filters' settling time.
 */
class StreamingOctaveBank(val sampleRate: Int) {
    private val sections = OctaveBands.CENTRES_HZ.map { centreHz ->
        List(2) { Biquad.bandPass(centreHz.toDouble(), DspConstants.BAND_FILTER_Q, sampleRate) }
    }
    private var settleLeft = (DspConstants.FILTER_SETTLE_S * sampleRate).roundToInt()

    init {
        require(sampleRate > 0) { "sampleRate must be > 0, was $sampleRate" }
    }

    /** Leq of each band over [block], in dBFS; [SILENCE_DB] for a band with nothing counted yet. */
    fun bandLevelsDb(block: FloatArray): DoubleArray {
        val skip = minOf(settleLeft, block.size)
        settleLeft -= skip
        val counted = block.size - skip
        return DoubleArray(OctaveBands.COUNT) { band ->
            val (first, second) = sections[band]
            var sumOfSquares = 0.0
            for (i in block.indices) {
                val y = second.process(first.process(block[i].toDouble()))
                if (i >= skip) sumOfSquares += y * y
            }
            if (counted == 0) SILENCE_DB else meanSquareDb(sumOfSquares / counted)
        }
    }
}
