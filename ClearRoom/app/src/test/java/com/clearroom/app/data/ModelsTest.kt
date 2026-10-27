package com.clearroom.app.data

import com.clearroom.app.audio.AudioSourceKind
import com.clearroom.app.testing.aMeasuredSeat
import com.clearroom.app.testing.aRoom
import com.clearroom.app.testing.flatBands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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

    @Test
    fun `clearing a seat removes all its readings and leaves the others`() {
        val session = Session(aRoom(), seats = listOf(aMeasuredSeat(0, 0), aMeasuredSeat(1, 1)))
        assertEquals(listOf(SeatId(1, 1)), session.withoutSeat(SeatId(0, 0)).seats.map { it.seat })
        assertEquals(session, session.withoutSeat(SeatId(4, 4)))
    }

    @Test
    fun `a seat has readings once it has noise or at least one clap`() {
        assertEquals(false, SeatMeasure(0, 0).hasReadings)
        assertEquals(true, SeatMeasure(0, 0, noise = flatBands(-60.0)).hasReadings)
        assertEquals(true, SeatMeasure(0, 0, rtTakes = listOf(RtTake(List(7) { 1.0 }))).hasReadings)
    }

    private fun assertNotNullSeat(session: Session, seat: SeatId) =
        session.measureAt(seat) ?: throw AssertionError("no measurement at $seat")

    @Test
    fun `saved-room entries expose their id whether readable or damaged`() {
        val summary = RoomSummary("a", "A", 9.0, 7.0, 30, 0, savedAtMillis = 1)
        assertEquals("a", SavedRoom.Readable(summary).id)
        assertEquals("b", SavedRoom.Damaged("b").id)
    }

    // --- Clap curves (V3, V10) ----------------------------------------------------------------------

    private val curves = ClapCurves(List(7) { BandCurve(listOf(0.0, -1.0), DecayFit(-60.0, 0.0, 2.0)) }, List(7) { listOf(0.0, -3.0) })

    @Test
    fun `a decay fit gives the time to fall 60 dB`() {
        assertEquals(1.2, DecayFit(slopeDbPerS = -50.0, interceptDb = 0.5, xiPermille = 3.0).t60S, 1e-12)
    }

    @Test
    fun `a decay fit must fall and be finite`() {
        assertFailsWith<IllegalArgumentException> { DecayFit(0.0, 0.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { DecayFit(10.0, 0.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { DecayFit(-50.0, Double.NaN, 1.0) }
    }

    @Test
    fun `curve levels must be finite`() {
        assertFailsWith<IllegalArgumentException> { BandCurve(listOf(0.0, Double.NEGATIVE_INFINITY), null) }
        assertFailsWith<IllegalArgumentException> { ClapCurves(curves.bands, List(7) { listOf(Double.NaN) }) }
    }

    @Test
    fun `clap curves need all seven bands`() {
        assertFailsWith<IllegalArgumentException> { ClapCurves(curves.bands.take(6), curves.waterfallDb) }
        assertFailsWith<IllegalArgumentException> { ClapCurves(curves.bands, curves.waterfallDb.take(6)) }
    }

    @Test
    fun `an accepted clap with curves becomes the seat's latest clap`() {
        val session = Session(aRoom()).withClap(SeatId(1, 1), RtTake(List(7) { 0.8 }), curves)
        assertEquals(curves, assertNotNullSeat(session, SeatId(1, 1)).lastClap)
    }

    @Test
    fun `a clap without curves keeps the curves of the one before`() {
        val seat = SeatId(1, 1)
        val session = Session(aRoom()).withClap(seat, RtTake(List(7) { 0.8 }), curves).withClap(seat, RtTake(List(7) { 0.9 }))
        assertEquals(curves, assertNotNullSeat(session, seat).lastClap)
        assertEquals(2, assertNotNullSeat(session, seat).rtTakes.size)
    }

    @Test
    fun `clearing a seat's claps clears its curves too`() {
        val seat = SeatId(1, 1)
        val session = Session(aRoom()).withClap(seat, RtTake(List(7) { 0.8 }), curves).withoutClaps(seat)
        assertEquals(null, assertNotNullSeat(session, seat).lastClap)
    }

    @Test
    fun `wall area counts the four walls only`() {
        assertEquals(2 * (9.0 + 7.0) * 3.0, aRoom(lengthM = 9.0, widthM = 7.0, heightM = 3.0).wallAreaM2, 1e-12)
    }

    // --- Before and after (FR-13) --------------------------------------------------------------------

    private val measured = Session(aRoom(), voice = flatBands(-30.0), seats = listOf(aMeasuredSeat(0, 0), aMeasuredSeat(4, 5)))

    @Test
    fun `re-measuring keeps the readings as before and clears the seats, but not the voice`() {
        val remeasuring = measured.startRemeasuring(atMillis = 42L)
        assertEquals(Baseline(42L, flatBands(-30.0), measured.seats), remeasuring.baseline)
        assertEquals(emptyList(), remeasuring.seats)
        assertEquals(flatBands(-30.0), remeasuring.voice)
        assertEquals(measured, remeasuring.before)
    }

    @Test
    fun `starting again while comparing keeps the first before`() {
        val remeasuring = measured.startRemeasuring(42L).withNoise(SeatId(1, 1), flatBands(-50.0), FanState.OFF)
        assertEquals(remeasuring, remeasuring.startRemeasuring(99L))
    }

    @Test
    fun `there is no before unless re-measuring, and nothing to end`() {
        assertNull(measured.before)
        assertEquals(measured, measured.endComparison())
    }

    @Test
    fun `ending a comparison keeps the new readings and drops before`() {
        val ended = measured.startRemeasuring(42L).withNoise(SeatId(1, 1), flatBands(-50.0), FanState.OFF).endComparison()
        assertNull(ended.baseline)
        assertEquals(listOf(SeatId(1, 1)), ended.seats.map { it.seat })
    }

    @Test
    fun `ending a comparison before measuring anything brings the earlier readings back`() {
        assertEquals(measured, measured.startRemeasuring(42L).endComparison())
    }

    @Test
    fun `a smaller room drops before-readings of seats that no longer exist, too`() {
        val smaller = measured.startRemeasuring(42L).withRoom(aRoom(rows = 3, cols = 4))
        assertEquals(listOf(aMeasuredSeat(0, 0)), smaller.baseline?.seats)
    }

    @Test
    fun `before-readings must fit the room's grid like the current ones`() {
        assertFailsWith<IllegalArgumentException> { Session(aRoom(rows = 2, cols = 2), baseline = Baseline(1L, null, listOf(aMeasuredSeat(2, 0)))) }
        assertFailsWith<IllegalArgumentException> { Session(aRoom(), baseline = Baseline(1L, null, listOf(aMeasuredSeat(1, 1), aMeasuredSeat(1, 1)))) }
    }

    @Test
    fun `a session remembers how its latest reading was captured`() {
        val mic = MicSetup(AudioSourceKind.VOICE_RECOGNITION, 48_000)
        assertEquals(mic, Session(aRoom()).withMic(mic).mic)
        assertFailsWith<IllegalArgumentException> { MicSetup(AudioSourceKind.UNPROCESSED, 0) }
    }

    // --- Field tests (M13) ------------------------------------------------------------------------------

    @Test
    fun `a voice reading per distance, replaced when re-recorded, nearest first`() {
        val session = Session(aRoom())
            .withDistanceReading(DistanceReading(8.0, flatBands(-50.0)))
            .withDistanceReading(DistanceReading(3.0, flatBands(-40.0)))
            .withDistanceReading(DistanceReading(8.0, flatBands(-52.0)))
        assertEquals(listOf(DistanceReading(3.0, flatBands(-40.0)), DistanceReading(8.0, flatBands(-52.0))), session.distanceReadings)
        assertFailsWith<IllegalArgumentException> { DistanceReading(0.0, flatBands(-40.0)) }
    }

    @Test
    fun `a listening score per seat, front to back, cleared with null`() {
        val session = Session(aRoom()).withListeningScore(SeatId(4, 1), 12).withListeningScore(SeatId(0, 1), 19).withListeningScore(SeatId(4, 1), 14)
        assertEquals(listOf(ListeningScore(SeatId(0, 1), 19), ListeningScore(SeatId(4, 1), 14)), session.listening)
        assertEquals(listOf(ListeningScore(SeatId(0, 1), 19)), session.withListeningScore(SeatId(4, 1), null).listening)
        assertFailsWith<IllegalArgumentException> { session.withListeningScore(SeatId(9, 0), 10) }
        assertFailsWith<IllegalArgumentException> { ListeningScore(SeatId(0, 0), ListeningScore.WORDS + 1) }
    }

    @Test
    fun `a smaller room drops the listening scores of seats that are gone`() {
        val session = Session(aRoom()).withListeningScore(SeatId(0, 0), 18).withListeningScore(SeatId(4, 5), 10)
        assertEquals(listOf(SeatId(0, 0)), session.withRoom(aRoom(rows = 2, cols = 2)).listening.map { it.seat })
    }

    @Test
    fun `a distance recorded twice or a seat scored twice is refused`() {
        assertFailsWith<IllegalArgumentException> { Session(aRoom(), distanceReadings = List(2) { DistanceReading(3.0, flatBands(-40.0)) }) }
        assertFailsWith<IllegalArgumentException> { Session(aRoom(), listening = List(2) { ListeningScore(SeatId(0, 0), 10) }) }
    }
}
