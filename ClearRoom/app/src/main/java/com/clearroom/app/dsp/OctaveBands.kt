package com.clearroom.app.dsp

/** Doc "Exact parameters": the seven octave bands. Band index 0..6 means this order everywhere. */
object OctaveBands {
    val CENTRES_HZ: List<Int> = listOf(125, 250, 500, 1_000, 2_000, 4_000, 8_000)
    val COUNT: Int = CENTRES_HZ.size

    /** 500 Hz, 1 kHz and 2 kHz: the bands that carry most speech clarity (NFR-3 judges accuracy here). */
    val SPEECH_BANDS: IntRange = 2..4
}
