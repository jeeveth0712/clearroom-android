package com.clearroom.app.acoustics

import com.clearroom.app.data.Room
import com.clearroom.app.data.SeatId
import kotlin.math.hypot

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

    private fun spread(start: Double, end: Double, index: Int, count: Int, singleAt: Double): Double =
        if (count == 1) singleAt else start + (end - start) * index / (count - 1)
}
