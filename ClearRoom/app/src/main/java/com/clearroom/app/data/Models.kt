package com.clearroom.app.data

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

/** Everything measured at one seat (doc data model). Row 0 is the front row. */
data class SeatMeasure(
    val row: Int,
    val col: Int,
    val noise: BandLevels? = null,
    val fans: FanState? = null,
    val rtTakes: List<RtTake> = emptyList(),
) {
    init {
        require(row >= 0 && col >= 0) { "seat position can't be negative: ($row, $col)" }
    }

    val seat: SeatId get() = SeatId(row, col)
}

/** A room plus its measurements. Audio is never part of it (NFR-6). */
data class Session(
    val room: Room,
    val voice: BandLevels? = null,
    val seats: List<SeatMeasure> = emptyList(),
) {
    init {
        require(seats.all { it.row < room.rows && it.col < room.cols }) { "a measured seat lies outside the grid" }
        require(seats.distinctBy { it.row to it.col }.size == seats.size) { "a seat is measured twice" }
    }

    /** The same session for an edited room, dropping measurements of seats that no longer exist. */
    fun withRoom(newRoom: Room): Session =
        copy(room = newRoom, seats = seats.filter { it.row < newRoom.rows && it.col < newRoom.cols })

    fun contains(seat: SeatId): Boolean = seat.row < room.rows && seat.col < room.cols

    fun measureAt(seat: SeatId): SeatMeasure? = seats.firstOrNull { it.seat == seat }

    /** FR-4: records (or replaces) the noise snapshot at [seat], keeping that seat's echo takes. */
    fun withNoise(seat: SeatId, noise: BandLevels, fans: FanState): Session =
        updateSeat(seat) { it.copy(noise = noise, fans = fans) }

    /** FR-5: records (or replaces) the speaker's voice reference at 1 m. */
    fun withVoice(voice: BandLevels): Session = copy(voice = voice)

    /** FR-2: adds one accepted clap take at [seat], keeping the earlier takes and the noise. */
    fun withClap(seat: SeatId, take: RtTake): Session =
        updateSeat(seat) { it.copy(rtTakes = it.rtTakes + take) }

    /** Clears the clap takes at [seat] so they can be redone; its noise reading stays. */
    fun withoutClaps(seat: SeatId): Session =
        if (measureAt(seat) == null) this else updateSeat(seat) { it.copy(rtTakes = emptyList()) }

    private fun updateSeat(seat: SeatId, change: (SeatMeasure) -> SeatMeasure): Session {
        require(contains(seat)) { "seat $seat is outside the ${room.rows} x ${room.cols} grid" }
        val updated = change(measureAt(seat) ?: SeatMeasure(seat.row, seat.col))
        return copy(seats = (seats.filterNot { it.seat == seat } + updated).sortedWith(compareBy({ it.row }, { it.col })))
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
