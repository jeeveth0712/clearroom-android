package com.clearroom.app.acoustics

import com.clearroom.app.data.ListeningScore
import com.clearroom.app.data.RtTake
import com.clearroom.app.data.Session
import com.clearroom.app.dsp.OctaveBands
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/** Echo repeatability: how well one seat's claps agree at speech pitches (NFR-3: CV ≤ 10 % at 500 Hz–2 kHz). */
data class Repeatability(val claps: Int, val worstVariation: Double) {
    val passed: Boolean get() = worstVariation <= FieldChecks.MAX_CLAP_VARIATION
}

/** What-if realism: the speech-pitch echo time before a change, as the model predicted it after, and as measured after. */
data class CurtainCheck(val curtainM2: Double, val beforeS: Double, val predictedS: Double, val measuredS: Double) {
    val predictedChangeS: Double get() = beforeS - predictedS
    val measuredChangeS: Double get() = beforeS - measuredS

    /** The doc's test: the predicted change within ±25 % of the measured one. */
    val passed: Boolean
        get() = measuredChangeS > 0.0 && abs(predictedChangeS - measuredChangeS) <= FieldChecks.CURTAIN_TOLERANCE * measuredChangeS
}

/** The voice at one distance: the model's prediction from the voice at 1 m, and the reading (speech pitches, dB). */
data class DistancePoint(val distanceM: Double, val predictedDb: Double, val measuredDb: Double) {
    val errorDb: Double get() = measuredDb - predictedDb
}

/** Distance model: the mean size of the differences (the doc's test: 3 dB or less). */
data class DistanceCheck(val points: List<DistancePoint>) {
    val meanErrorDb: Double get() = points.map { abs(it.errorDb) }.average()
    val passed: Boolean get() = meanErrorDb <= FieldChecks.MAX_DISTANCE_ERROR_DB
}

/** One listener's seat, with the clarity the app worked out there. */
data class ScoredSeat(val score: ListeningScore, val sti: Double)

/**
 * Listening check: do the seats the app rates poorer get lower word scores? Pairs closer in
 * clarity than [FieldChecks.MIN_STI_GAP] aren't compared: the app can't tell those apart.
 */
data class ListeningCheck(val seats: List<ScoredSeat>, val comparedPairs: Int, val disagreements: Int) {
    val passed: Boolean get() = comparedPairs > 0 && disagreements == 0
}

/** The field tests in the doc's testing table that the app works out itself. */
object FieldChecks {
    /** NFR-3: the spread of a seat's claps at most 10 % of their mean. */
    const val MAX_CLAP_VARIATION = 0.10

    /** What-if realism: the predicted echo-time change within 25 % of the measured change. */
    const val CURTAIN_TOLERANCE = 0.25

    /** Distance model: mean difference at most 3 dB. */
    const val MAX_DISTANCE_ERROR_DB = 3.0

    /** Listening check: clarity this close between two seats is within the app's own uncertainty. */
    const val MIN_STI_GAP = 0.02

    /** The distances the doc asks for, besides the 1 m voice reference. */
    val CHECK_DISTANCES_M = listOf(3.0, 5.0, 8.0)

    /** Null until two claps gave values at every speech pitch. Estimated bands don't count. */
    fun repeatability(takes: List<RtTake>): Repeatability? {
        val summary = Rt60Summary.of(takes) ?: return null
        val speech = OctaveBands.SPEECH_BANDS.map { summary.bands[it] }
        if (speech.any { it.estimated || it.takes < 2 }) return null
        return Repeatability(speech.minOf { it.takes }, speech.maxOf { it.variation })
    }

    /**
     * The curtains' effect predicted from "before" ([before]) with the Fixes tab's model, against
     * the room measured after ([after]). Null without curtains, or until both sides have results.
     */
    fun curtainCheck(before: Session, after: Session, curtainM2: Double): CurtainCheck? {
        if (curtainM2 <= 0.0) return null
        val was = ready(before) ?: return null
        val now = ready(after) ?: return null
        val predicted = ready(before, WhatIf(absorbers = listOf(Absorber(curtainM2, Absorbers.HEAVY_CURTAIN_ALPHA)))) ?: return null
        return CurtainCheck(curtainM2, speechEcho(was), speechEcho(predicted), speechEcho(now))
    }

    /**
     * Each distance reading against the speech level the room model predicts there (doc step 3),
     * from the voice measured at 1 m. Null until the voice, an echo and noise and a reading exist.
     */
    fun distanceCheck(session: Session): DistanceCheck? {
        val voice = session.voice ?: return null
        if (session.distanceReadings.isEmpty()) return null
        val room = ready(session) ?: return null
        return DistanceCheck(
            session.distanceReadings.map { reading ->
                val predicted = OctaveBands.SPEECH_BANDS.map { SpeechPropagation.levelDb(reading.distanceM, voice[it], room.roomConstantM2[it]) }
                DistancePoint(reading.distanceM, speechLevel(predicted), speechLevel(OctaveBands.SPEECH_BANDS.map { reading.voice[it] }))
            },
        )
    }

    /** Null until two seats have scores and the room has results. */
    fun listeningCheck(session: Session): ListeningCheck? {
        if (session.listening.size < 2) return null
        val room = ready(session) ?: return null
        val seats = session.listening.map { ScoredSeat(it, room.at(it.seat).sti) }
        var compared = 0
        var disagreements = 0
        for (i in seats.indices) for (j in i + 1 until seats.size) {
            val (a, b) = seats[i] to seats[j]
            if (abs(a.sti - b.sti) < MIN_STI_GAP) continue
            compared++
            val (poorer, better) = if (a.sti < b.sti) a to b else b to a
            if (poorer.score.wordsRight > better.score.wordsRight) disagreements++
        }
        return ListeningCheck(seats, compared, disagreements)
    }

    /** The speech pitches' energies added up, in dB: one number for "how loud is the voice here". */
    private fun speechLevel(bandsDb: List<Double>): Double = 10 * log10(bandsDb.sumOf { 10.0.pow(it / 10) })

    private fun speechEcho(room: RoomClarity): Double = OctaveBands.SPEECH_BANDS.map { room.rt60S[it] }.average()

    private fun ready(session: Session, whatIf: WhatIf = WhatIf()): RoomClarity? =
        (ClarityModel.evaluate(session, whatIf) as? ClarityResult.Ready)?.room
}
