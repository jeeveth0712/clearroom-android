package com.clearroom.app.testing

import com.clearroom.app.data.RoomRepository
import com.clearroom.app.data.RoomSummary
import com.clearroom.app.data.SavedRoom
import com.clearroom.app.data.Session
import java.io.IOException

/** [RoomRepository] in a map, with a counter for "saved at" so newest-first order is predictable. */
class InMemoryRoomRepository : RoomRepository {
    private val sessions = mutableMapOf<String, Pair<Session, Long>>()
    private var saveCounter = 0L

    var failSaves = false
    val damagedIds = mutableListOf<String>()

    override suspend fun save(session: Session) {
        if (failSaves) throw IOException("fake: disk full")
        sessions[session.room.id] = session to ++saveCounter
    }

    override suspend fun load(roomId: String): Session? = sessions[roomId]?.first

    override suspend fun list(): List<SavedRoom> =
        sessions.values.sortedByDescending { it.second }.map { (session, savedAt) ->
            SavedRoom.Readable(
                RoomSummary(
                    id = session.room.id,
                    name = session.room.name,
                    lengthM = session.room.lengthM,
                    widthM = session.room.widthM,
                    seatCount = session.room.seatCount,
                    measuredSeats = session.seats.size,
                    savedAtMillis = savedAt,
                ),
            )
        } + damagedIds.map { SavedRoom.Damaged(it) }

    override suspend fun delete(roomId: String) {
        sessions.remove(roomId)
        damagedIds.remove(roomId)
    }
}
