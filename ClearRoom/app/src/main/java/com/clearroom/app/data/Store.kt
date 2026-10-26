package com.clearroom.app.data

import androidx.core.util.AtomicFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Rooms as one JSON file each in app-private storage ([directory], normally filesDir/rooms).
 * Writes are atomic: a crash mid-save leaves the previous version intact.
 */
class JsonRoomStore(
    private val directory: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : RoomRepository {

    override suspend fun save(session: Session): Unit = withContext(io) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("can't create $directory")
        val file = AtomicFile(fileFor(session.room.id))
        val stream = file.startWrite()
        try {
            stream.write(RoomJson.encode(session, nowMillis()).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: IOException) {
            file.failWrite(stream)
            throw e
        }
    }

    override suspend fun load(roomId: String): Session? = withContext(io) {
        readStored(fileFor(roomId))?.session
    }

    override suspend fun list(): List<SavedRoom> = withContext(io) {
        val files = directory.listFiles { file -> file.name.endsWith(EXTENSION) }.orEmpty()
        val entries = files.map { file ->
            val id = file.name.removeSuffix(EXTENSION)
            val stored = if (isSafeId(id)) readStored(file) else null
            if (stored == null) SavedRoom.Damaged(id) else SavedRoom.Readable(stored.toSummary())
        }
        entries.filterIsInstance<SavedRoom.Readable>().sortedByDescending { it.summary.savedAtMillis } +
            entries.filterIsInstance<SavedRoom.Damaged>().sortedBy { it.id }
    }

    override suspend fun delete(roomId: String): Unit = withContext(io) {
        AtomicFile(fileFor(roomId)).delete()
    }

    /** Null for a missing or unreadable file; one bad file never hides the others. */
    private fun readStored(file: File): StoredSession? {
        if (!file.exists()) return null
        return try {
            RoomJson.decode(AtomicFile(file).readFully().toString(Charsets.UTF_8))
        } catch (e: RoomFormatException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    /** Ids become file names, so only the characters a UUID uses are allowed: no "../" tricks. */
    private fun fileFor(roomId: String): File {
        require(isSafeId(roomId)) { "unsafe room id: $roomId" }
        return File(directory, roomId + EXTENSION)
    }

    private fun StoredSession.toSummary() = RoomSummary(
        id = session.room.id,
        name = session.room.name,
        lengthM = session.room.lengthM,
        widthM = session.room.widthM,
        seatCount = session.room.seatCount,
        measuredSeats = session.seats.size,
        savedAtMillis = savedAtMillis,
    )

    private companion object {
        const val EXTENSION = ".json"
        val SAFE_ID = Regex("[A-Za-z0-9-]{1,64}")

        fun isSafeId(id: String) = SAFE_ID.matches(id)
    }
}
