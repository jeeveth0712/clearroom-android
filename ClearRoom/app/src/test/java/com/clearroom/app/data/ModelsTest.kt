package com.clearroom.app.data

import com.clearroom.app.testing.aMeasuredSeat
import com.clearroom.app.testing.aRoom
import com.clearroom.app.testing.flatBands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModelsTest {

    @Test
    fun `band levels need exactly seven finite values`() {
        assertFailsWith<IllegalArgumentException> { BandLevels(List(6) { -60.0 }) }
        assertFailsWith<IllegalArgumentException> { BandLevels(List(8) { -60.0 }) }
        assertFailsWith<IllegalArgumentException> { BandLevels(List(7) { Double.NaN }) }
        assertFailsWith<IllegalArgumentException> { BandLevels(List(7) { Double.NEGATIVE_INFINITY }) }
    }

    @Test
    fun `equal band levels are equal, so the UI sees an unchanged value as unchanged`() {
        assertEquals(flatBands(-60.0), flatBands(-60.0))
        assertEquals(Session(aRoom(), voice = flatBands(-30.0)), Session(aRoom(), voice = flatBands(-30.0)))
    }

    @Test
    fun `room volume and surface area follow from its size`() {
        val room = aRoom(lengthM = 9.0, widthM = 7.0, heightM = 3.2)
        assertEquals(201.6, room.volumeM3, absoluteTolerance = 1e-9)
        assertEquals(2 * (63.0 + 28.8 + 22.4), room.surfaceAreaM2, absoluteTolerance = 1e-9)
        assertEquals(30, aRoom(rows = 5, cols = 6).seatCount)
    }

    @Test
    fun `impossible rooms are rejected`() {
        assertFailsWith<IllegalArgumentException> { aRoom(id = " ") }
        assertFailsWith<IllegalArgumentException> { aRoom(lengthM = 0.0) }
        assertFailsWith<IllegalArgumentException> { aRoom(widthM = -7.0) }
        assertFailsWith<IllegalArgumentException> { aRoom(heightM = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { aRoom(rows = 0) }
        assertFailsWith<IllegalArgumentException> { aRoom(cols = 0) }
        assertFailsWith<IllegalArgumentException> { aRoom(students = -1) }
    }

    @Test
    fun `an RT60 take has seven bands, null where a band was rejected`() {
        RtTake(listOf(null, 1.2, 1.1, 1.0, 0.9, 0.8, null)) // fine
        assertFailsWith<IllegalArgumentException> { RtTake(List(6) { 1.0 }) }
        assertFailsWith<IllegalArgumentException> { RtTake(List(7) { 0.0 }) }
        assertFailsWith<IllegalArgumentException> { RtTake(List(7) { Double.POSITIVE_INFINITY }) }
    }

    @Test
    fun `seat positions can't be negative`() {
        assertFailsWith<IllegalArgumentException> { SeatMeasure(row = -1, col = 0) }
        assertFailsWith<IllegalArgumentException> { SeatMeasure(row = 0, col = -1) }
    }

    @Test
    fun `a session can't measure seats outside the grid or the same seat twice`() {
        val room = aRoom(rows = 2, cols = 2)
        assertFailsWith<IllegalArgumentException> { Session(room, seats = listOf(aMeasuredSeat(2, 0))) }
        assertFailsWith<IllegalArgumentException> { Session(room, seats = listOf(aMeasuredSeat(0, 2))) }
        assertFailsWith<IllegalArgumentException> { Session(room, seats = listOf(aMeasuredSeat(1, 1), aMeasuredSeat(1, 1))) }
    }

    @Test
    fun `changing the room keeps seats that still exist and the voice reference`() {
        val session = Session(
            room = aRoom(rows = 5, cols = 6),
            voice = flatBands(-30.0),
            seats = listOf(aMeasuredSeat(0, 0), aMeasuredSeat(4, 0), aMeasuredSeat(0, 5)),
        )
        val smaller = session.withRoom(aRoom(rows = 3, cols = 4))
        assertEquals(listOf(aMeasuredSeat(0, 0)), smaller.seats)
        assertEquals(flatBands(-30.0), smaller.voice)
        assertEquals(3, smaller.room.rows)
    }

    @Test
    fun `seat ids can't be negative and a session knows which seats exist`() {
        assertFailsWith<IllegalArgumentException> { SeatId(-1, 0) }
        val session = Session(aRoom(rows = 2, cols = 3))
        assertEquals(true, session.contains(SeatId(1, 2)))
        assertEquals(false, session.contains(SeatId(2, 0)))
        assertEquals(false, session.contains(SeatId(0, 3)))
    }

    @Test
    fun `recording noise at a new seat adds it, in front-to-back order`() {
        val session = Session(aRoom(), seats = listOf(aMeasuredSeat(3, 0)))
        val updated = session.withNoise(SeatId(1, 2), flatBands(-55.0), FanState.SLOW)
        assertEquals(listOf(SeatId(1, 2), SeatId(3, 0)), updated.seats.map { it.seat })
        assertEquals(flatBands(-55.0), updated.measureAt(SeatId(1, 2))?.noise)
        assertEquals(FanState.SLOW, updated.measureAt(SeatId(1, 2))?.fans)
    }

    @Test
    fun `re-recording noise at a seat replaces it and keeps that seat's echo takes`() {
        val session = Session(aRoom(), seats = listOf(aMeasuredSeat(0, 0)))
        val updated = session.withNoise(SeatId(0, 0), flatBands(-70.0), FanState.OFF)
        val seat = updated.seats.single()
        assertEquals(flatBands(-70.0), seat.noise)
        assertEquals(FanState.OFF, seat.fans)
        assertEquals(aMeasuredSeat(0, 0).rtTakes, seat.rtTakes)
    }

    @Test
    fun `noise can't be recorded at a seat outside the grid`() {
        assertFailsWith<IllegalArgumentException> {
            Session(aRoom(rows = 2, cols = 2)).withNoise(SeatId(5, 5), flatBands(-60.0), FanState.OFF)
        }
    }

    @Test
    fun `the voice reference is set or replaced without touching seats`() {
        val session = Session(aRoom(), voice = flatBands(-40.0), seats = listOf(aMeasuredSeat(0, 0)))
        val updated = session.withVoice(flatBands(-30.0))
        assertEquals(flatBands(-30.0), updated.voice)
        assertEquals(session.seats, updated.seats)
        assertEquals(null, session.measureAt(SeatId(4, 4)))
    }

    @Test
    fun `claps add up at a seat and keep its noise reading`() {
        val take1 = RtTake(List(7) { 1.0 })
        val take2 = RtTake(List(7) { 1.1 })
        val session = Session(aRoom()).withNoise(SeatId(1, 1), flatBands(-60.0), FanState.OFF)
            .withClap(SeatId(1, 1), take1)
            .withClap(SeatId(1, 1), take2)
        val seat = assertNotNullSeat(session, SeatId(1, 1))
        assertEquals(listOf(take1, take2), seat.rtTakes)
        assertEquals(flatBands(-60.0), seat.noise)
    }

    @Test
    fun `a clap at a seat with nothing measured yet creates that seat`() {
        val session = Session(aRoom()).withClap(SeatId(0, 4), RtTake(List(7) { 0.9 }))
        assertEquals(1, assertNotNullSeat(session, SeatId(0, 4)).rtTakes.size)
        assertFailsWith<IllegalArgumentException> { Session(aRoom(rows = 2)).withClap(SeatId(3, 0), RtTake(List(7) { 0.9 })) }
    }

    @Test
    fun `clearing claps empties only that seat's takes`() {
        val session = Session(aRoom(), seats = listOf(aMeasuredSeat(0, 0), aMeasuredSeat(1, 1)))
        val cleared = session.withoutClaps(SeatId(0, 0))
        assertEquals(emptyList(), assertNotNullSeat(cleared, SeatId(0, 0)).rtTakes)
        assertEquals(aMeasuredSeat(0, 0).noise, assertNotNullSeat(cleared, SeatId(0, 0)).noise)
        assertEquals(aMeasuredSeat(1, 1), cleared.measureAt(SeatId(1, 1)))
        assertEquals(session, session.withoutClaps(SeatId(4, 4))) // nothing there: unchanged
    }

    private fun assertNotNullSeat(session: Session, seat: SeatId) =
        session.measureAt(seat) ?: throw AssertionError("no measurement at $seat")

    @Test
    fun `saved-room entries expose their id whether readable or damaged`() {
        val summary = RoomSummary("a", "A", 9.0, 7.0, 30, 0, savedAtMillis = 1)
        assertEquals("a", SavedRoom.Readable(summary).id)
        assertEquals("b", SavedRoom.Damaged("b").id)
    }
}
