package com.clearroom.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SampleDataTest {

    private val session = SampleData.session

    @Test
    fun `the sample is the documented 5 x 6 classroom with a voice reference`() {
        assertEquals(SampleData.ROOM_ID, session.room.id)
        assertEquals(30, session.room.seatCount)
        assertNotNull(session.voice)
    }

    @Test
    fun `three seats are measured, from the front row to the back row`() {
        assertEquals(3, session.seats.size)
        assertEquals(0, session.seats.minOf { it.row })
        assertEquals(session.room.rows - 1, session.seats.maxOf { it.row })
    }

    @Test
    fun `each measured seat has noise, a fan state and three claps`() {
        session.seats.forEach { seat ->
            assertNotNull(seat.noise, "seat ${seat.row},${seat.col}")
            assertNotNull(seat.fans, "seat ${seat.row},${seat.col}")
            assertEquals(3, seat.rtTakes.size, "seat ${seat.row},${seat.col}")
        }
    }

    @Test
    fun `speech is well above noise at 1 m, as in a real classroom`() {
        val voice = assertNotNull(session.voice)
        val noise = assertNotNull(session.seats.first().noise)
        (0 until 7).forEach { band -> assertTrue(voice[band] - noise[band] > 15.0, "band $band") }
    }

    @Test
    fun `sample RT60 values look like a bare classroom - around a second, longer in the bass`() {
        val midBand = session.seats.flatMap { seat -> seat.rtTakes.mapNotNull { it.t60S[3] } }
        assertTrue(midBand.all { it in 0.9..1.3 }, "1 kHz RT60s: $midBand")
        session.seats.flatMap { it.rtTakes }.forEach { take ->
            val low = take.t60S[1]
            val high = take.t60S[5]
            if (low != null && high != null) assertTrue(low > high, "take $take")
        }
    }

    @Test
    fun `every sample seat has clap curves for the decay chart and waterfall`() {
        session.seats.forEach { assertNotNull(it.lastClap, "seat ${it.seat}") }
    }

    @Test
    fun `illustrative curves fall from 0 dB along their fit, and the waterfall stays within its floor`() {
        val curves = SampleData.illustrativeCurves(listOf(1.4, 1.3, 1.2, 1.1, 1.0, 0.9, 0.8))
        curves.bands.forEachIndexed { band, curve ->
            assertEquals(0.0, curve.schroederDb.first())
            assertTrue(curve.schroederDb.zipWithNext().all { (a, b) -> b < a }, "band $band falls")
            assertEquals(listOf(1.4, 1.3, 1.2, 1.1, 1.0, 0.9, 0.8)[band], assertNotNull(curve.fit).t60S, 1e-9)
        }
        assertTrue(curves.waterfallDb.all { band -> band.size == 200 && band.all { it in -60.0..2.0 } })
    }
}
