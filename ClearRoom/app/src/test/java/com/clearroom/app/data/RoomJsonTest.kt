package com.clearroom.app.data

import com.clearroom.app.testing.aRoom
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
}
