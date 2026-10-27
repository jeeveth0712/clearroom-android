package com.clearroom.app.data

import com.clearroom.app.audio.AudioSourceKind
import com.clearroom.app.dsp.DspConstants
import com.clearroom.app.dsp.OctaveBands

/**
 * Seven octave-band levels in dB, in [OctaveBands] order. A List rather than an array so two
 * equal readings compare equal (StateFlow skips "unchanged" values by equality).
 */
@JvmInline
value class BandLevels(val valuesDb: List<Double>) {
    init {
        require(valuesDb.size == OctaveBands.COUNT) { "need ${OctaveBands.COUNT} bands, got ${valuesDb.size}" }
        require(valuesDb.all { it.isFinite() }) { "band levels must be finite: $valuesDb" }
    }

    operator fun get(band: Int): Double = valuesDb[band]
}

/** FR-1 pick list of surface materials. Seated students are counted separately in [Room.students]. */
enum class Material { CONCRETE, TILE, GLASS, WOOD, CURTAIN, SOFT_BOARD }

data class Surfaces(val floor: Material, val walls: Material, val ceiling: Material)

/**
 * A room as the user describes it (FR-1). [id] is a stable machine id used for file names;
 * the user-typed [name] never touches the file system.
 */
data class Room(
    val id: String,
    val name: String,
    val lengthM: Double,
    val widthM: Double,
    val heightM: Double,
    val rows: Int,
    val cols: Int,
    val surfaces: Surfaces,
    val students: Int,
) {
    init {
        require(id.isNotBlank()) { "room id is blank" }
        require(listOf(lengthM, widthM, heightM).all { it.isFinite() && it > 0.0 }) {
            "room dimensions must be positive: $lengthM x $widthM x $heightM"
        }
        require(rows >= 1 && cols >= 1) { "need at least one seat, got $rows x $cols" }
        require(students >= 0) { "students can't be negative: $students" }
    }

    val volumeM3: Double get() = lengthM * widthM * heightM

    /** Floor, ceiling and four walls; Sabine's equation needs it (doc Analysis step 2). */
    val surfaceAreaM2: Double get() = 2 * (lengthM * widthM + lengthM * heightM + widthM * heightM)

    /** The four walls only (where curtains can hang), m². */
    val wallAreaM2: Double get() = 2 * (lengthM + widthM) * heightM

    val seatCount: Int get() = rows * cols
}

/** FR-4: fans or AC state while the noise was measured. */
enum class FanState { OFF, SLOW, FAST }

/** A seat's place in the grid; row 0 is the front row. */
data class SeatId(val row: Int, val col: Int) {
    init {
        require(row >= 0 && col >= 0) { "seat position can't be negative: ($row, $col)" }
    }
}

/** One clap's RT60 per octave band, in seconds; null where that band's fit was rejected. */
data class RtTake(val t60S: List<Double?>) {
    init {
        require(t60S.size == OctaveBands.COUNT) { "need ${OctaveBands.COUNT} bands, got ${t60S.size}" }
        require(t60S.all { it == null || (it.isFinite() && it > 0.0) }) { "RT60 values must be positive: $t60S" }
    }
}

/** The T20 line fitted to one band's decay: level = interceptDb + slopeDbPerS · t, t in s from the peak. */
data class DecayFit(val slopeDbPerS: Double, val interceptDb: Double, val xiPermille: Double) {
    init {
        require(slopeDbPerS < 0.0 && slopeDbPerS.isFinite()) { "a decay must fall: $slopeDbPerS dB/s" }
        require(interceptDb.isFinite() && xiPermille.isFinite()) { "fit must be finite: $interceptDb dB, $xiPermille ‰" }
    }

    /** The echo time this line gives: how long it takes to fall 60 dB. */
    val t60S: Double get() = -60.0 / slopeDbPerS
}

/** One band of a clap for the decay chart (V3): an empty curve and no fit where the band was rejected. */
data class BandCurve(val schroederDb: List<Double>, val fit: DecayFit?) {
    init {
        require(schroederDb.all { it.isFinite() }) { "curve levels must be finite" }
    }
}

/**
 * The latest accepted clap at a seat, kept for the decay chart (V3) and the echo waterfall (V10).
 * Levels in dB, one value every [STEP_S]. Numbers only, never audio (NFR-6).
 */
data class ClapCurves(val bands: List<BandCurve>, val waterfallDb: List<List<Double>>) {
    init {
        require(bands.size == OctaveBands.COUNT && waterfallDb.size == OctaveBands.COUNT) { "need ${OctaveBands.COUNT} bands" }
        require(waterfallDb.all { band -> band.all { it.isFinite() } }) { "waterfall levels must be finite" }
    }

    companion object {
        const val STEP_S = DspConstants.CURVE_STEP_S
    }
}

/** Everything measured at one seat (doc data model). Row 0 is the front row. */
data class SeatMeasure(
    val row: Int,
    val col: Int,
    val noise: BandLevels? = null,
    val fans: FanState? = null,
    val rtTakes: List<RtTake> = emptyList(),
    /** Curves of the latest accepted clap here; null before any clap, and for older saved rooms. */
    val lastClap: ClapCurves? = null,
) {
    init {
        require(row >= 0 && col >= 0) { "seat position can't be negative: ($row, $col)" }
    }

    val seat: SeatId get() = SeatId(row, col)

    /** True when anything was measured here: a noise reading or at least one clap. */
    val hasReadings: Boolean get() = noise != null || rtTakes.isNotEmpty()
}

/** How the room's latest reading was captured, for the report's measurement conditions (FR-12). */
data class MicSetup(val source: AudioSourceKind, val sampleRateHz: Int) {
    init {
        require(sampleRateHz > 0) { "sample rate must be > 0, was $sampleRateHz" }
    }
}

/**
 * FR-13: the readings as they were before a change, kept when re-measuring starts (Memento). The
 * session's own readings then fill in "after"; the room itself is shared by both.
 */
data class Baseline(val takenAtMillis: Long, val voice: BandLevels?, val seats: List<SeatMeasure>)

/**
 * Field test "distance model" (doc testing table): the speaker's voice recorded [distanceM] from
 * them, to compare with the model's prediction from the voice at 1 m.
 */
data class DistanceReading(val distanceM: Double, val voice: BandLevels) {
    init {
        require(distanceM.isFinite() && distanceM > 0.0) { "distance must be positive: $distanceM" }
    }
}

/** Field test "listening check": how many of [WORDS] words read from the front a listener at [seat] wrote down right. */
data class ListeningScore(val seat: SeatId, val wordsRight: Int) {
    init {
        require(wordsRight in 0..WORDS) { "words right must be 0–$WORDS, was $wordsRight" }
    }

    companion object {
        /** The doc's listening check reads 20 short words. */
        const val WORDS = 20
    }
}

/** A room plus its measurements. Audio is never part of it (NFR-6). */
data class Session(
    val room: Room,
    val voice: BandLevels? = null,
    val seats: List<SeatMeasure> = emptyList(),
    /** How the latest reading was captured; null before any, and for rooms saved before this was kept. */
    val mic: MicSetup? = null,
    /** FR-13: the readings from before a change while re-measuring; null otherwise. */
    val baseline: Baseline? = null,
    /** Field test: the voice recorded at distances from the speaker, one per distance, nearest first. */
    val distanceReadings: List<DistanceReading> = emptyList(),
    /** Field test: listening-check scores, one per seat, front to back. */
    val listening: List<ListeningScore> = emptyList(),
) {
    init {
        requireSeatsFit(seats, room)
        baseline?.let { requireSeatsFit(it.seats, room) }
        require(distanceReadings.distinctBy { it.distanceM }.size == distanceReadings.size) { "a distance is recorded twice" }
        require(listening.all { contains(it.seat) }) { "a listening score lies outside the grid" }
        require(listening.distinctBy { it.seat }.size == listening.size) { "a seat is scored twice" }
    }

    /** The same session for an edited room, dropping measurements of seats that no longer exist (before and after). */
    fun withRoom(newRoom: Room): Session = copy(
        room = newRoom,
        seats = seats.inside(newRoom),
        baseline = baseline?.let { it.copy(seats = it.seats.inside(newRoom)) },
        listening = listening.filter { it.seat.row < newRoom.rows && it.seat.col < newRoom.cols },
    )

    /** Field test: records (or replaces) the voice heard [reading]'s distance from the speaker. */
    fun withDistanceReading(reading: DistanceReading): Session =
        copy(distanceReadings = (distanceReadings.filterNot { it.distanceM == reading.distanceM } + reading).sortedBy { it.distanceM })

    /** Field test: sets [seat]'s listening-check score, or clears it with null. */
    fun withListeningScore(seat: SeatId, wordsRight: Int?): Session {
        require(contains(seat)) { "seat $seat is outside the ${room.rows} x ${room.cols} grid" }
        val others = listening.filterNot { it.seat == seat }
        val scores = if (wordsRight == null) others else others + ListeningScore(seat, wordsRight)
        return copy(listening = scores.sortedWith(compareBy({ it.seat.row }, { it.seat.col })))
    }

    /** The room as it was before the change, measured with the "before" readings; null when not comparing. */
    val before: Session? get() = baseline?.let { Session(room, it.voice, it.seats) }

    /**
     * FR-13: keeps the current readings as "before" and clears the seats for new readings. The
     * voice reference stays: a change to the room hardly changes the voice at 1 m. A comparison
     * already running keeps its "before".
     */
    fun startRemeasuring(atMillis: Long): Session =
        if (baseline != null) this else copy(baseline = Baseline(atMillis, voice, seats), seats = emptyList())

    /**
     * Ends a comparison. New readings win and "before" is dropped; with nothing measured since it
     * started, the "before" readings come back, so ending early never loses a measurement.
     */
    fun endComparison(): Session = when {
        baseline == null -> this
        seats.isEmpty() -> copy(voice = voice ?: baseline.voice, seats = baseline.seats, baseline = null)
        else -> copy(baseline = null)
    }

    fun withMic(setup: MicSetup): Session = copy(mic = setup)

    fun contains(seat: SeatId): Boolean = seat.row < room.rows && seat.col < room.cols

    fun measureAt(seat: SeatId): SeatMeasure? = seats.firstOrNull { it.seat == seat }

    /** FR-4: records (or replaces) the noise snapshot at [seat], keeping that seat's echo takes. */
    fun withNoise(seat: SeatId, noise: BandLevels, fans: FanState): Session =
        updateSeat(seat) { it.copy(noise = noise, fans = fans) }

    /** FR-5: records (or replaces) the speaker's voice reference at 1 m. */
    fun withVoice(voice: BandLevels): Session = copy(voice = voice)

    /** FR-2: adds one accepted clap take at [seat], keeping the earlier takes and the noise; [curves] become its latest. */
    fun withClap(seat: SeatId, take: RtTake, curves: ClapCurves? = null): Session =
        updateSeat(seat) { it.copy(rtTakes = it.rtTakes + take, lastClap = curves ?: it.lastClap) }

    /** Removes everything measured at [seat] (noise and claps), as if it had never been measured. */
    fun withoutSeat(seat: SeatId): Session = copy(seats = seats.filterNot { it.seat == seat })

    /** Clears the clap takes at [seat] so they can be redone; its noise reading stays. */
    fun withoutClaps(seat: SeatId): Session =
        if (measureAt(seat) == null) this else updateSeat(seat) { it.copy(rtTakes = emptyList(), lastClap = null) }

    private fun updateSeat(seat: SeatId, change: (SeatMeasure) -> SeatMeasure): Session {
        require(contains(seat)) { "seat $seat is outside the ${room.rows} x ${room.cols} grid" }
        val updated = change(measureAt(seat) ?: SeatMeasure(seat.row, seat.col))
        return copy(seats = (seats.filterNot { it.seat == seat } + updated).sortedWith(compareBy({ it.row }, { it.col })))
    }

    private companion object {
        fun requireSeatsFit(seats: List<SeatMeasure>, room: Room) {
            require(seats.all { it.row < room.rows && it.col < room.cols }) { "a measured seat lies outside the grid" }
            require(seats.distinctBy { it.row to it.col }.size == seats.size) { "a seat is measured twice" }
        }

        fun List<SeatMeasure>.inside(room: Room) = filter { it.row < room.rows && it.col < room.cols }
    }
}

/** What the saved-rooms list shows, without loading every measurement. */
data class RoomSummary(
    val id: String,
    val name: String,
    val lengthM: Double,
    val widthM: Double,
    val seatCount: Int,
    val measuredSeats: Int,
    val savedAtMillis: Long,
)

sealed interface SavedRoom {
    val id: String

    data class Readable(val summary: RoomSummary) : SavedRoom {
        override val id: String get() = summary.id
    }

    /** The file exists but can't be read (corrupt, or written by a newer app); can only be deleted. */
    data class Damaged(override val id: String) : SavedRoom
}

/** Where rooms live. The app uses JSON files; tests use an in-memory fake. */
interface RoomRepository {
    /** Saves (or replaces) the session's room and measurements. */
    suspend fun save(session: Session)

    /** Null when the room doesn't exist or its file can't be read. */
    suspend fun load(roomId: String): Session?

    /** Newest first; damaged files are listed last so the user can delete them. */
    suspend fun list(): List<SavedRoom>

    suspend fun delete(roomId: String)
}
