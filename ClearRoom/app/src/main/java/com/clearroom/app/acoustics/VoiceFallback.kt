package com.clearroom.app.acoustics

import com.clearroom.app.data.BandLevels

/**
 * FR-5 fallback when no voice reference was recorded: "assume 60 dBA normal speech".
 *
 * Spectrum: ANSI S3.5-1997 Table 4 (octave-band SII procedure), "normal" vocal effort, which is the
 * standard spectrum for a talker in a room at 1 m. (The IEC 60268-16 spectra are for a microphone
 * about 5 cm from the mouth.) Octave level = standard speech spectrum level + octave conversion,
 * both from that table. ANSI has no 125 Hz value; it is set 6 dB below 250 Hz, the convention of
 * BB93 and the Odeon STI application note. The total is about 60 dBA, matching the doc.
 *
 * Level: the phone measures in dBFS, so an absolute speech level needs an assumed microphone
 * sensitivity. Android's Compatibility Definition (5.4.2, VOICE_RECOGNITION capture) says a 90 dB
 * SPL 1 kHz tone should read -22.35 dBFS (±3 dB), i.e. SPL ≈ dBFS + 112.35. Results using this
 * fallback are therefore "uncalibrated" (NFR-5).
 */
object VoiceFallback {
    private val ANSI_NORMAL_SPECTRUM_LEVEL_DB = listOf(34.75, 34.27, 25.01, 17.32, 9.33, 1.13) // 250 Hz–8 kHz, dB/Hz
    private val ANSI_OCTAVE_CONVERSION_DB = listOf(22.48, 25.48, 28.48, 31.48, 34.48, 37.48)
    private const val MISSING_125_HZ_BELOW_250_DB = 6.0

    /** Android CDD 5.4.2: SPL ≈ dBFS + 112.35 for the VOICE_RECOGNITION source (±3 dB). */
    const val ASSUMED_SPL_MINUS_DBFS = 112.35

    /** Normal speech at 1 m in front of the mouth, dB SPL per octave band (125 Hz … 8 kHz). */
    val NORMAL_SPEECH_AT_1M_SPL_DB: List<Double> = run {
        val ansi = ANSI_NORMAL_SPECTRUM_LEVEL_DB.zip(ANSI_OCTAVE_CONVERSION_DB) { level, conversion -> level + conversion }
        listOf(ansi.first() - MISSING_125_HZ_BELOW_250_DB) + ansi
    }

    /** The fallback voice in the phone's own units, to compare with noise measured in dBFS. */
    val normalVoiceDbFs: BandLevels = BandLevels(NORMAL_SPEECH_AT_1M_SPL_DB.map { it - ASSUMED_SPL_MINUS_DBFS })
}
