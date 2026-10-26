package com.clearroom.app.measure

import com.clearroom.app.data.RtTake
import com.clearroom.app.dsp.BandDecay
import com.clearroom.app.dsp.OctaveBands
import com.clearroom.app.dsp.RejectReason
import com.clearroom.app.dsp.ReverbResult

/** What the user should do differently after a rejected clap (FR-2 wording). */
enum class ClapProblem {
    /** Nothing stood out from the background. */
    NO_CLAP,

    /** The room is quiet, but the clap was too weak for a 30 dB decay. */
    CLAP_LOUDER,

    /** The clap was fine, but background noise hides the decay. */
    TOO_NOISY,

    /** The decay wasn't a clean line, or gave an impossible echo time. */
    UNCLEAR,
}

sealed interface ClapVerdict {
    data class Accepted(val take: RtTake, val analysis: ReverbResult.Analysed) : ClapVerdict
    data class Rejected(val problem: ClapProblem) : ClapVerdict
}

/**
 * Whether one clap counts as a take. The speech bands (500 Hz, 1 kHz, 2 kHz) must all give an
 * echo time: they matter most for clarity and NFR-3 judges accuracy there. Outer bands may fail
 * (claps are weak at 125 Hz); they're stored as null and filled from neighbours later.
 */
object ClapTake {
    /** FR-2: repeat the clap 3 times per seat. */
    const val TAKES_PER_SEAT = 3

    /**
     * Band noise above this level (dBFS) means the room, not the clap, limited the decay. Phone
     * levels are uncalibrated, so this is a rough split between "clap louder" and "too noisy".
     */
    const val NOISY_FLOOR_DBFS = -60.0

    fun judge(result: ReverbResult): ClapVerdict = when (result) {
        ReverbResult.NoClap -> ClapVerdict.Rejected(ClapProblem.NO_CLAP)
        is ReverbResult.Analysed -> {
            val failed = OctaveBands.SPEECH_BANDS.map { result.bands[it] }.filterIsInstance<BandDecay.Rejected>()
            if (failed.isEmpty()) {
                ClapVerdict.Accepted(RtTake(result.bands.map { (it as? BandDecay.Ok)?.t20S }), result)
            } else {
                ClapVerdict.Rejected(problemOf(failed))
            }
        }
    }

    private fun problemOf(failed: List<BandDecay.Rejected>): ClapProblem {
        val tooShort = failed.filter { it.reason == RejectReason.LOW_DYNAMIC_RANGE }
        return when {
            tooShort.isEmpty() -> ClapProblem.UNCLEAR
            tooShort.any { it.noiseFloorDb > NOISY_FLOOR_DBFS } -> ClapProblem.TOO_NOISY
            else -> ClapProblem.CLAP_LOUDER
        }
    }
}
