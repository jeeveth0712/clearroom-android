package com.clearroom.app.acoustics

import com.clearroom.app.testing.aRoom
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeatLayoutTest {

    private val room = aRoom(lengthM = 9.0, widthM = 7.0, rows = 5, cols = 6)
    private val seats = SeatLayout.seats(room)

    @Test
    fun `speaker stands 1 m from the front wall, centred`() {
        assertEquals(FloorPoint(3.5, 1.0), SeatLayout.speaker(room))
    }

    @Test
    fun `every seat is laid out once, front row first`() {
        assertEquals(30, seats.size)
        assertEquals((0 until 5).flatMap { r -> (0 until 6).map { c -> r to c } }, seats.map { it.row to it.col })
    }

    @Test
    fun `rows run from 2,5 m to 0,8 m before the back wall`() {
        assertEquals(2.5, seats.first { it.row == 0 }.position.yM, absoluteTolerance = 1e-12)
        assertEquals(9.0 - 0.8, seats.first { it.row == 4 }.position.yM, absoluteTolerance = 1e-12)
        val rowDepths = (0 until 5).map { r -> seats.first { it.row == r }.position.yM }
        val gaps = rowDepths.zipWithNext { a, b -> b - a }
        gaps.forEach { assertEquals(gaps.first(), it, absoluteTolerance = 1e-12) }
    }

    @Test
    fun `columns keep 0,8 m from each side wall, evenly spaced`() {
        assertEquals(0.8, seats.first { it.col == 0 }.position.xM, absoluteTolerance = 1e-12)
        assertEquals(7.0 - 0.8, seats.first { it.col == 5 }.position.xM, absoluteTolerance = 1e-12)
    }

    @Test
    fun `a single row sits at the front and a single column is centred`() {
        val oneSeat = SeatLayout.seats(aRoom(widthM = 5.0, rows = 1, cols = 1)).single()
        assertEquals(FloorPoint(2.5, 2.5), oneSeat.position)
    }

    @Test
    fun `each seat knows its straight-line distance to the speaker`() {
        val speaker = SeatLayout.speaker(room)
        seats.forEach { seat ->
            val expected = hypot(seat.position.xM - speaker.xM, seat.position.yM - speaker.yM)
            assertEquals(expected, seat.distanceToSpeakerM, absoluteTolerance = 1e-12)
        }
    }

    @Test
    fun `back rows are farther from the speaker than front rows`() {
        val centreColumn = seats.filter { it.col == 2 }.sortedBy { it.row }.map { it.distanceToSpeakerM }
        assertTrue(centreColumn.zipWithNext().all { (front, back) -> back > front }, "$centreColumn")
    }

    @Test
    fun `floor distance is symmetric`() {
        val a = FloorPoint(1.0, 2.0)
        val b = FloorPoint(4.0, 6.0)
        assertEquals(5.0, a.distanceTo(b), absoluteTolerance = 1e-12)
        assertEquals(a.distanceTo(b), b.distanceTo(a))
    }
}
