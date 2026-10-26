package com.clearroom.app.data

import com.clearroom.app.audio.AudioSourceKind
import com.clearroom.app.testing.aMeasuredSeat
import com.clearroom.app.testing.aRoom
import com.clearroom.app.testing.flatBands
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RoomJsonTest {

    private fun roundTrip(session: Session, savedAt: Long = 1_700_000_000_000) =
        RoomJson.decode(RoomJson.encode(session, savedAt))

    @Test
    fun `the sample session survives a save and load unchanged`() {
        assertEquals(StoredSession(SampleData.session, 42L), roundTrip(SampleData.session, savedAt = 42L))
    }

    @Test
    fun `a bare room with no measurements survives a save and load`() {
        val session = Session(aRoom())
        assertEquals(session, roundTrip(session).session)
    }

    @Test
    fun `missing values stay missing - no voice, no noise, no fan state, rejected bands`() {
        val session = Session(
            room = aRoom(),
            voice = null,
            seats = listOf(SeatMeasure(row = 1, col = 2, noise = null, fans = null, rtTakes = listOf(RtTake(listOf(null, 1.1, 1.0, 0.9, 0.8, 0.7, null))))),
        )
        assertEquals(session, roundTrip(session).session)
    }

    @Test
    fun `names with quotes and non-English text survive`() {
        val session = Session(aRoom(name = "Room \"A\" — कक्षा 5"))
        assertEquals(session, roundTrip(session).session)
    }

    @Test
    fun `files record their schema version and save time`() {
        val json = JSONObject(RoomJson.encode(Session(aRoom()), savedAtMillis = 123L))
        assertEquals(RoomJson.SCHEMA_VERSION, json.getInt("schemaVersion"))
        assertEquals(123L, json.getLong("savedAtMillis"))
    }

    @Test
    fun `files from a newer app version are refused, not half-read`() {
        val newer = JSONObject(RoomJson.encode(Session(aRoom()), 1L)).put("schemaVersion", RoomJson.SCHEMA_VERSION + 1)
        val error = assertFailsWith<RoomFormatException> { RoomJson.decode(newer.toString()) }
        assertTrue(error.message.orEmpty().contains("newer"), error.message)
    }

    @Test
    fun `broken files are reported as format errors`() {
        val valid = JSONObject(RoomJson.encode(Session(aRoom()), 1L))
        val broken = listOf(
            "not json at all",
            "{}",
            JSONObject(valid.toString()).apply { getJSONObject("room").remove("lengthM") }.toString(),
            JSONObject(valid.toString()).apply { getJSONObject("room").getJSONObject("surfaces").put("floor", "MARBLE") }.toString(),
            JSONObject(valid.toString()).apply { getJSONObject("room").put("rows", 0) }.toString(),
            JSONObject(valid.toString()).put("schemaVersion", 0).toString(),
        )
        broken.forEach { text -> assertFailsWith<RoomFormatException>(text.take(80)) { RoomJson.decode(text) } }
    }

    @Test
    fun `a seat's latest clap curves survive a save and load, rejected bands included`() {
        val curves = ClapCurves(
            List(7) { band -> if (band == 0) BandCurve(emptyList(), null) else BandCurve(listOf(0.0, -1.25, -2.5), DecayFit(-55.5, 0.25, 3.5)) },
            List(7) { listOf(0.0, -10.5, -60.0) },
        )
        val session = Session(aRoom()).withClap(SeatId(0, 1), RtTake(List(7) { 1.0 }), curves)
        assertEquals(session, roundTrip(session).session)
    }

    @Test
    fun `a room saved before curves were kept still loads, with no curves`() {
        val session = Session(aRoom()).withClap(SeatId(0, 1), RtTake(List(7) { 1.0 }))
        val old = JSONObject(RoomJson.encode(session, 1L)).apply { getJSONArray("seats").getJSONObject(0).remove("lastClap") }.toString()
        assertEquals(session, RoomJson.decode(old).session)
    }

    @Test
    fun `damaged clap curves are dropped, but the room and its readings still load`() {
        val curves = ClapCurves(List(7) { BandCurve(listOf(0.0, -1.0), DecayFit(-60.0, 0.0, 2.0)) }, List(7) { listOf(0.0, -3.0) })
        val session = Session(aRoom()).withClap(SeatId(0, 1), RtTake(List(7) { 1.0 }), curves)
        val damaged = JSONObject(RoomJson.encode(session, 1L)).apply {
            getJSONArray("seats").getJSONObject(0).getJSONObject("lastClap").getJSONArray("bands").remove(0) // only 6 bands left
        }.toString()
        val loaded = RoomJson.decode(damaged).session
        assertEquals(null, loaded.measureAt(SeatId(0, 1))?.lastClap)
        assertEquals(session.measureAt(SeatId(0, 1))?.rtTakes, loaded.measureAt(SeatId(0, 1))?.rtTakes)
    }

    @Test
    fun `before-readings and the microphone setup survive a save and load`() {
        val session = Session(aRoom(), voice = flatBands(-30.0), seats = listOf(aMeasuredSeat(0, 1)))
            .startRemeasuring(atMillis = 1_800_000_000_000L)
            .withNoise(SeatId(2, 2), flatBands(-48.5), FanState.FAST)
            .withMic(MicSetup(AudioSourceKind.VOICE_RECOGNITION, 44_100))
        assertEquals(session, roundTrip(session).session)
    }

    @Test
    fun `a room saved before comparisons and microphone notes still loads, with neither`() {
        val session = Session(aRoom(), seats = listOf(aMeasuredSeat(0, 1)))
        val old = JSONObject(RoomJson.encode(session, 1L)).apply {
            remove("mic")
            remove("baseline")
        }.toString()
        assertEquals(session, RoomJson.decode(old).session)
    }

    @Test
    fun `a damaged before or microphone note is dropped, but the room and its readings still load`() {
        val session = Session(aRoom(), seats = listOf(aMeasuredSeat(0, 1))).startRemeasuring(5L)
            .withNoise(SeatId(1, 1), flatBands(-50.0), FanState.OFF)
            .withMic(MicSetup(AudioSourceKind.UNPROCESSED, 48_000))
        val damaged = JSONObject(RoomJson.encode(session, 1L)).apply {
            getJSONObject("baseline").getJSONArray("seats").getJSONObject(0).put("row", 99) // outside the grid
            getJSONObject("mic").put("source", "TELEPATHY")
        }.toString()
        assertEquals(session.copy(baseline = null, mic = null), RoomJson.decode(damaged).session)
    }
}
