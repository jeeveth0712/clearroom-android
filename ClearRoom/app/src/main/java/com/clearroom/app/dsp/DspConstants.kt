package com.clearroom.app.dsp

/** Every DSP number from the doc's "Exact parameters" table, with its unit in the name. */
object DspConstants {
    /** RBJ band-pass quality factor for each section (doc: Q = 1.414). */
    const val BAND_FILTER_Q = 1.414

    /** Seconds skipped at the start of a filtered clip so the filters' start-up transient isn't counted. */
    const val FILTER_SETTLE_S = 0.05

    /**
     * A-weighting at the octave centres, 125 Hz … 8 kHz (IEC 61672), in dB. Summing weighted bands
     * gives an approximate dBA: content below 88 Hz and above 11 kHz is outside the bands.
     */
    val A_WEIGHTING_DB: List<Double> = listOf(-16.1, -8.6, -3.2, 0.0, 1.2, 1.0, -1.1)
}
