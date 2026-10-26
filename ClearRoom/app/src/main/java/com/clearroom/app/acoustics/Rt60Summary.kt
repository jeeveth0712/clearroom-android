package com.clearroom.app.acoustics

import com.clearroom.app.data.RtTake
import com.clearroom.app.dsp.OctaveBands
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * One band's echo time across the takes at a seat (FR-3: mean and spread). [estimated] bands had
 * no valid take and borrow the nearest measured band's value, so the STI engine still gets all 7.
 */
data class BandRt60(val meanS: Double, val spreadS: Double, val takes: Int, val estimated: Boolean) {
    /** Coefficient of variation: spread relative to the mean (NFR-3 asks for ≤ 10 % at 500 Hz–2 kHz). */
    val variation: Double get() = if (meanS > 0.0) spreadS / meanS else 0.0
}

data class Rt60Summary(val bands: List<BandRt60>) {
    /** The single number people quote: the mean RT60 over the speech bands, 500 Hz–2 kHz. */
    val speechRt60S: Double get() = OctaveBands.SPEECH_BANDS.map { bands[it].meanS }.average()

    companion object {
        /** ANSI S12.60: classrooms up to about 280 m³ should have RT60 ≤ 0.6 s (V4 target line). */
        const val CLASSROOM_TARGET_S = 0.6

        /** Null when no take has any valid band yet. */
        fun of(takes: List<RtTake>): Rt60Summary? {
            val measured = (0 until OctaveBands.COUNT).map { band -> takes.mapNotNull { it.t60S[band] } }
            if (measured.all { it.isEmpty() }) return null
            val bands = measured.mapIndexed { band, values ->
                if (values.isNotEmpty()) {
                    BandRt60(values.average(), sampleSpread(values), values.size, estimated = false)
                } else {
                    val donor = measured.indices
                        .filter { measured[it].isNotEmpty() }
                        .minWith(compareBy<Int> { abs(it - band) }.thenByDescending { it })
                    BandRt60(measured[donor].average(), sampleSpread(measured[donor]), takes = 0, estimated = true)
                }
            }
            return Rt60Summary(bands)
        }

        /** Sample standard deviation; one take has no spread to report, so 0. */
        private fun sampleSpread(values: List<Double>): Double {
            if (values.size < 2) return 0.0
            val mean = values.average()
            return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
        }
    }
}
