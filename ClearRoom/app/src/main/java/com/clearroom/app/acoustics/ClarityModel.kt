package com.clearroom.app.acoustics

import com.clearroom.app.data.BandLevels
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.Session
import com.clearroom.app.dsp.OctaveBands
import com.clearroom.app.dsp.Sti
import kotlin.math.max

/** A change to try before spending money (FR-8). Everything defaults to "as measured". */
data class WhatIf(
    val absorbers: List<Absorber> = emptyList(),
    val extraStudents: Int = 0,
    val noiseChangeDb: Double = 0.0,
    val voiceChangeDb: Double = 0.0,
    val micGainDb: Double = 0.0,
    val speaker: FloorPoint? = null,
) {
    init {
        require(extraStudents >= 0) { "extraStudents can't be negative: $extraStudents" }
        require(listOf(noiseChangeDb, voiceChangeDb, micGainDb).all { it.isFinite() }) { "level changes must be finite" }
    }
}

data class Absorber(val areaM2: Double, val alpha: List<Double>)

/** Doc "Exact parameters": typical what-if absorbers per band, 125 Hz … 8 kHz. */
object Absorbers {
    val HEAVY_CURTAIN_ALPHA = listOf(0.14, 0.35, 0.55, 0.72, 0.70, 0.65, 0.65)

    /** Absorption per seated student, m² (not a coefficient). */
    val SEATED_STUDENT_M2 = listOf(0.15, 0.25, 0.35, 0.45, 0.50, 0.50, 0.50)
}

/** Why a seat isn't clearer (FR-7, V2): what would help it most. */
enum class Cause { ECHO, NOISE, DISTANCE }

enum class MissingInput { ECHO, NOISE }

data class SeatClarity(
    val seat: SeatId,
    val distanceM: Double,
    val sti: Double,
    /** NFR-10: STI with the echo time at mean ± its spread over the claps. */
    val stiLow: Double,
    val stiHigh: Double,
    val cause: Cause,
    val isMeasured: Boolean,
    val speechDb: List<Double>,
    val noiseDb: List<Double>,
) {
    val band: StiBand get() = StiBand.from(sti)
}

data class RoomClarity(
    val rows: Int,
    val cols: Int,
    val seats: List<SeatClarity>,
    /** Echo time per band after any what-if changes. */
    val rt60S: List<Double>,
    val criticalDistanceM: Double,
    /** False when the FR-5 fallback voice was used: results are then "uncalibrated" (NFR-5). */
    val voiceMeasured: Boolean,
) {
    fun at(seat: SeatId): SeatClarity = seats.first { it.seat == seat }

    /** A row is as clear as its worst seat: the goal is that every seat can follow. */
    fun rowSti(row: Int): Double = seats.filter { it.seat.row == row }.minOf { it.sti }

    val frontRowSti: Double get() = rowSti(0)
    val backRowSti: Double get() = rowSti(rows - 1)
}

sealed interface ClarityResult {
    data class Ready(val room: RoomClarity) : ClarityResult

    /** Not enough measured yet to say anything: at least one clap and one noise reading are needed. */
    data class NeedsMore(val missing: Set<MissingInput>) : ClarityResult
}

/** FR-11: voice change needed at 1 m for a row to reach STI 0.60, relative to the normal voice. */
sealed interface VoiceNeed {
    data class Required(val changeDb: Double) : VoiceNeed

    /** Echo-limited: even a very loud voice can't reach the target; a mic or absorption is needed. */
    data object Unreachable : VoiceNeed
}

data class RowVoice(val row: Int, val need: VoiceNeed)

/**
 * The clarity engine (FR-6 to FR-8, FR-11): turns a session's echo, noise and voice readings into
 * an STI per seat, its uncertainty range, its main cause, and the voice needed per row.
 */
object ClarityModel {
    /** Doc: the clarity target is STI 0.60 (start of the "good" band). */
    val TARGET_STI = StiBand.GOOD.lowerBound

    /** FR-11 search range for the voice change, dB relative to normal voice. */
    const val VOICE_SEARCH_MIN_DB = -20.0
    const val VOICE_SEARCH_MAX_DB = 40.0
    private const val VOICE_TOLERANCE_DB = 0.1

    /** Shortest echo time allowed when subtracting the spread for the uncertainty range. */
    private const val MIN_T60_S = 0.05

    fun evaluate(session: Session, whatIf: WhatIf = WhatIf()): ClarityResult {
        val inputs = Inputs.of(session, whatIf) ?: return ClarityResult.NeedsMore(missingInputs(session))
        val seats = inputs.layout.map { seat ->
            val noise = inputs.noiseAt(seat.position)
            val distance = seat.position.distanceTo(inputs.speaker)
            val sti = inputs.sti(inputs.mean, noise, distance)
            val quiet = inputs.sti(inputs.mean, noise, distance, noNoise = true)
            val dry = inputs.sti(inputs.mean, noise, distance, noEcho = true)
            val close = inputs.sti(inputs.mean, noise, distanceM = 1.0)
            val range = listOf(inputs.sti(inputs.longer, noise, distance), inputs.sti(inputs.shorter, noise, distance), sti)
            SeatClarity(
                seat = seat.id,
                distanceM = distance,
                sti = sti,
                stiLow = range.min(),
                stiHigh = range.max(),
                cause = limitingCause(gainIfQuiet = quiet - sti, gainIfDry = dry - sti, gainIfClose = close - sti),
                isMeasured = session.measureAt(seat.id)?.noise != null,
                speechDb = inputs.speechAt(inputs.mean, distance),
                noiseDb = noise.valuesDb,
            )
        }
        val speechT60 = OctaveBands.SPEECH_BANDS.map { inputs.mean.t60S(it) }.average()
        return ClarityResult.Ready(
            RoomClarity(
                rows = session.room.rows,
                cols = session.room.cols,
                seats = seats,
                rt60S = inputs.mean.t60S,
                criticalDistanceM = SpeechPropagation.criticalDistanceM(session.room.volumeM3, speechT60),
                voiceMeasured = session.voice != null,
            ),
        )
    }

    /**
     * FR-11: for each row, how much louder (or quieter) than normal the voice must be for its worst
     * seat to reach STI 0.60. STI never falls as the voice gets louder, so a bisection finds it.
     */
    fun requiredVoice(session: Session, whatIf: WhatIf = WhatIf()): List<RowVoice>? {
        val inputs = Inputs.of(session, whatIf) ?: return null
        return (0 until session.room.rows).map { row ->
            val rowSeats = inputs.layout.filter { it.id.row == row }.map { it.position to inputs.noiseAt(it.position) }
            fun worstSti(changeDb: Double) = rowSeats.minOf { (position, noise) ->
                inputs.sti(inputs.mean, noise, position.distanceTo(inputs.speaker), extraVoiceDb = changeDb)
            }
            RowVoice(row, bisectVoice(::worstSti))
        }
    }

    /**
     * Doc step 5, read as "whichever single change helps most": take away the noise, take away the
     * echo, or bring the talker to 1 m (move closer, or use a mic). Ties go to echo, then noise.
     */
    fun limitingCause(gainIfQuiet: Double, gainIfDry: Double, gainIfClose: Double): Cause = when {
        gainIfDry >= gainIfQuiet && gainIfDry >= gainIfClose -> Cause.ECHO
        gainIfQuiet >= gainIfClose -> Cause.NOISE
        else -> Cause.DISTANCE
    }

    private fun bisectVoice(stiFor: (Double) -> Double): VoiceNeed {
        if (stiFor(VOICE_SEARCH_MAX_DB) < TARGET_STI) return VoiceNeed.Unreachable
        if (stiFor(VOICE_SEARCH_MIN_DB) >= TARGET_STI) return VoiceNeed.Required(VOICE_SEARCH_MIN_DB)
        var low = VOICE_SEARCH_MIN_DB
        var high = VOICE_SEARCH_MAX_DB
        while (high - low > VOICE_TOLERANCE_DB) {
            val middle = (low + high) / 2
            if (stiFor(middle) >= TARGET_STI) high = middle else low = middle
        }
        return VoiceNeed.Required(high)
    }

    private fun missingInputs(session: Session): Set<MissingInput> = buildSet {
        if (Rt60Summary.of(session.seats.flatMap { it.rtTakes }) == null) add(MissingInput.ECHO)
        if (session.seats.none { it.noise != null }) add(MissingInput.NOISE)
    }

    /** Everything the seat calculations share, built once per evaluation. */
    private class Inputs(
        val mean: SabineRoom,
        val longer: SabineRoom,
        val shorter: SabineRoom,
        val voiceAt1mDb: List<Double>,
        val readings: List<Pair<FloorPoint, BandLevels>>,
        val speaker: FloorPoint,
        val layout: List<LayoutSeat>,
        val noiseChangeDb: Double,
    ) {
        fun noiseAt(point: FloorPoint): BandLevels =
            NoiseField.at(point, readings).let { base -> BandLevels(base.valuesDb.map { it + noiseChangeDb }) }

        fun speechAt(room: SabineRoom, distanceM: Double, extraVoiceDb: Double = 0.0): List<Double> =
            List(OctaveBands.COUNT) { band ->
                SpeechPropagation.levelDb(distanceM, voiceAt1mDb[band] + extraVoiceDb, room.roomConstantM2(band))
            }

        fun sti(
            room: SabineRoom,
            noise: BandLevels,
            distanceM: Double,
            extraVoiceDb: Double = 0.0,
            noNoise: Boolean = false,
            noEcho: Boolean = false,
        ): Double {
            val speech = speechAt(room, distanceM, extraVoiceDb)
            val t60 = DoubleArray(OctaveBands.COUNT) { if (noEcho) 0.0 else room.t60S(it) }
            val snr = DoubleArray(OctaveBands.COUNT) { if (noNoise) Sti.NO_NOISE_DB else speech[it] - noise[it] }
            val direct = DoubleArray(OctaveBands.COUNT) { SpeechPropagation.directToReverberant(distanceM, room.roomConstantM2(it)) }
            return Sti.sti(t60, snr, direct)
        }

        companion object {
            fun of(session: Session, whatIf: WhatIf): Inputs? {
                val rt = Rt60Summary.of(session.seats.flatMap { it.rtTakes }) ?: return null
                val readings = session.seats.mapNotNull { measure ->
                    measure.noise?.let { SeatLayout.position(session.room, measure.seat) to it }
                }
                if (readings.isEmpty()) return null
                fun room(t60S: List<Double>) = whatIf.applyTo(SabineRoom.fitted(session.room, t60S))
                val voice = (session.voice ?: VoiceFallback.normalVoiceDbFs).valuesDb
                return Inputs(
                    mean = room(rt.bands.map { it.meanS }),
                    longer = room(rt.bands.map { it.meanS + it.spreadS }),
                    shorter = room(rt.bands.map { max(MIN_T60_S, it.meanS - it.spreadS) }),
                    voiceAt1mDb = voice.map { it + whatIf.voiceChangeDb + whatIf.micGainDb },
                    readings = readings,
                    speaker = whatIf.speaker ?: SeatLayout.speaker(session.room),
                    layout = SeatLayout.seats(session.room).map { LayoutSeat(SeatId(it.row, it.col), it.position) },
                    noiseChangeDb = whatIf.noiseChangeDb,
                )
            }
        }
    }

    private class LayoutSeat(val id: SeatId, val position: FloorPoint)

    private fun WhatIf.applyTo(room: SabineRoom): SabineRoom =
        absorbers.fold(room) { acc, absorber -> acc.withAbsorber(absorber.areaM2, absorber.alpha) }
            .withPeople(extraStudents, Absorbers.SEATED_STUDENT_M2)
}
