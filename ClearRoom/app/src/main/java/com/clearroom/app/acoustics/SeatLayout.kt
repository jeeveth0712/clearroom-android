package com.clearroom.app.acoustics

import com.clearroom.app.data.Room
import com.clearroom.app.data.SeatId
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A point on the floor plan in metres: x across the room (0 = left wall), y from the front wall. */
data class FloorPoint(val xM: Double, val yM: Double) {
    fun distanceTo(other: FloorPoint): Double = hypot(xM - other.xM, yM - other.yM)
}

data class Seat(val row: Int, val col: Int, val position: FloorPoint, val distanceToSpeakerM: Double)

/**
 * Doc "Room defaults": speaker 1 m from the front wall, centred; seat rows spread evenly from
 * 2.5 m to (length − 0.8 m); seat columns spread evenly between 0.8 m side margins.
 * Row 0 is the front row; a single row or column sits at the start of its range or centred.
 */
object SeatLayout {
    const val SPEAKER_FROM_FRONT_M = 1.0
    const val FIRST_ROW_M = 2.5
    const val BACK_MARGIN_M = 0.8
    const val SIDE_MARGIN_M = 0.8

    /** Neighbouring seats are at least this far apart; closer, the seat map and 3D bars overlap. */
    const val MIN_SPACING_M = 0.5

    fun speaker(room: Room): FloorPoint = FloorPoint(xM = room.widthM / 2, yM = SPEAKER_FROM_FRONT_M)

    fun seats(room: Room): List<Seat> {
        val speaker = speaker(room)
        return (0 until room.rows).flatMap { row ->
            (0 until room.cols).map { col ->
                val position = position(room, SeatId(row, col))
                Seat(row, col, position, position.distanceTo(speaker))
            }
        }
    }

    /** Where one seat is on the floor plan. */
    fun position(room: Room, seat: SeatId): FloorPoint {
        require(seat.row < room.rows && seat.col < room.cols) { "seat $seat is outside the ${room.rows} x ${room.cols} grid" }
        val yM = spread(FIRST_ROW_M, room.lengthM - BACK_MARGIN_M, seat.row, room.rows, singleAt = FIRST_ROW_M)
        val xM = spread(SIDE_MARGIN_M, room.widthM - SIDE_MARGIN_M, seat.col, room.cols, singleAt = room.widthM / 2)
        return FloorPoint(xM, yM)
    }

    /** How many rows fit between the first row and the back margin, [MIN_SPACING_M] apart. */
    fun maxRows(lengthM: Double): Int = fitting(lengthM - BACK_MARGIN_M - FIRST_ROW_M)

    /** How many seats per row fit between the side margins, [MIN_SPACING_M] apart. */
    fun maxCols(widthM: Double): Int = fitting(widthM - 2 * SIDE_MARGIN_M)

    /** Distance to the nearest neighbouring seat, front-to-back or across; infinite for a single seat. */
    fun spacingM(room: Room): Double {
        val rowGap = if (room.rows > 1) (room.lengthM - BACK_MARGIN_M - FIRST_ROW_M) / (room.rows - 1) else Double.POSITIVE_INFINITY
        val colGap = if (room.cols > 1) (room.widthM - 2 * SIDE_MARGIN_M) / (room.cols - 1) else Double.POSITIVE_INFINITY
        return min(rowGap, colGap)
    }

    /** The 1e-9 keeps an exact fit, like 0.5 m for 2 rows, from rounding down to one seat fewer. */
    private fun fitting(spanM: Double): Int = floor(max(0.0, spanM) / MIN_SPACING_M + 1e-9).toInt() + 1

    private fun spread(start: Double, end: Double, index: Int, count: Int, singleAt: Double): Double =
        if (count == 1) singleAt else start + (end - start) * index / (count - 1)
}
