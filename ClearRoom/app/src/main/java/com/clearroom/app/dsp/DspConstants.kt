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

    // --- Clap analysis (doc "Analysis methods" step 1 and "Exact parameters") ---------------

    /** The clap must leave this much pre-roll for the noise estimate (doc: noise of the 0.3 s before the clap). */
    const val PRE_CLAP_NOISE_S = 0.3

    /** Gap left out just before the clap, so the time-reversed filter's pre-ringing isn't counted as noise. */
    const val PRE_CLAP_GAP_S = 0.05

    /** Doc: noise floor is the lower of the pre-clap mean and the mean of the last 0.5 s. */
    const val TAIL_NOISE_S = 0.5

    /** The clap must leave at least this much decay to analyse (and the tail window above). */
    const val MIN_AFTER_CLAP_S = 1.5

    /**
     * A clap must stand this far above the room's background to count as a clap at all (app choice).
     * Steady noise peaks only 2–3 dB above its own average; weaker claps above this limit go on to
     * the dynamic-range check, so the user hears "clap louder" or "too noisy", not "no clap".
     */
    const val MIN_CLAP_PROMINENCE_DB = 12.0

    /** Doc: 10 ms smoothing of the squared band signal. */
    const val ENVELOPE_SMOOTHING_S = 0.010

    /** Parameters table: reject bands with less dynamic range than this (FR-2 says 25 dB; table chosen). */
    const val MIN_DYNAMIC_RANGE_DB = 30.0

    /** Doc: truncate the Schroeder integral where the decay reaches noise + 5 dB. */
    const val TRUNCATE_ABOVE_NOISE_DB = 5.0

    /** Doc: T20 fit range on the Schroeder curve. */
    const val T20_FROM_DB = -5.0
    const val T20_TO_DB = -25.0

    /** T30 fit range (FR-3), used only when the decay reaches it before the truncation point. */
    const val T30_TO_DB = -35.0

    /** Doc: reject an RT60 outside 0.1–6 s. */
    const val MIN_T60_S = 0.1
    const val MAX_T60_S = 6.0

    /** A fit over less than this much decay is too short to trust. */
    const val MIN_FIT_SPAN_S = 0.02

    /**
     * ISO 3382 non-linearity ξ = 1000·(1 − r²) of the T20 fit. ISO calls a decay curved above 10 ‰,
     * but short decays in the narrow 125–250 Hz bands wiggle to 20–40 ‰ while still giving the right
     * slope. Synthetic tests (Oct 26) put every band with ξ ≤ 40 ‰ inside NFR-3 and both failures
     * above it, so the app rejects only above 40 ‰ (app choice).
     */
    const val MAX_NONLINEARITY_PERMILLE = 40.0

    /** Time step of the decay curves and the echo waterfall kept for the charts (V3, V10). */
    const val CURVE_STEP_S = 0.010

    /** V10 waterfall: 2 s after the clap, levels clamped at -60 dB. */
    const val WATERFALL_SPAN_S = 2.0
    const val WATERFALL_FLOOR_DB = -60.0
}
