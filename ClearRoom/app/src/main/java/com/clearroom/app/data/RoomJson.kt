package com.clearroom.app.data

import com.clearroom.app.audio.AudioSourceKind
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A file that isn't a readable ClearRoom room (corrupt, hand-edited, or from a newer app). */
class RoomFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class StoredSession(val session: Session, val savedAtMillis: Long)

/**
 * One JSON document per room: numbers, labels and settings only, never audio (NFR-6).
 * Non-finite numbers are written as null because JSON can't hold NaN or Infinity.
 */
object RoomJson {
    const val SCHEMA_VERSION = 1

    fun encode(session: Session, savedAtMillis: Long): String = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("savedAtMillis", savedAtMillis)
        .put("room", encodeRoom(session.room))
        .put("voiceDb", session.voice?.let { numbers(it.valuesDb) } ?: JSONObject.NULL)
        .put("seats", seatsJson(session.seats))
        .put("mic", session.mic?.let { JSONObject().put("source", it.source.name).put("sampleRateHz", it.sampleRateHz) } ?: JSONObject.NULL)
        .put("baseline", session.baseline?.let(::encodeBaseline) ?: JSONObject.NULL)
        .toString(2)

    /** @throws RoomFormatException when the text isn't a room this app version can read. */
    fun decode(text: String): StoredSession = try {
        val json = JSONObject(text)
        val version = json.getInt("schemaVersion")
        if (version > SCHEMA_VERSION) {
            throw RoomFormatException("saved by a newer ClearRoom (schema $version, this app reads up to $SCHEMA_VERSION)")
        }
        val current = migrate(json, fromVersion = version)
        val room = decodeRoom(current.getJSONObject("room"))
        StoredSession(
            session = Session(
                room = room,
                voice = voiceOf(current),
                seats = current.getJSONArray("seats").objects().map(::decodeSeat),
                // Both arrived after the first files were written: a file without them reads as none.
                mic = current.optJSONObject("mic")?.let { orNull { MicSetup(AudioSourceKind.valueOf(it.getString("source")), it.getInt("sampleRateHz")) } },
                // Checked against the room the way a session checks it, so a bad "before" can't sink the room.
                baseline = current.optJSONObject("baseline")?.let { orNull { decodeBaseline(it).also { before -> Session(room, baseline = before) } } },
            ),
            savedAtMillis = current.getLong("savedAtMillis"),
        )
    } catch (e: JSONException) {
        throw RoomFormatException("not a valid room file: ${e.message}", e)
    } catch (e: IllegalArgumentException) { // a model rule failed (bad size, unknown enum name, …)
        throw RoomFormatException("room file breaks a data rule: ${e.message}", e)
    }

    /** Upgrades older files step by step. Version 1 is the first format, so nothing to do yet. */
    private fun migrate(json: JSONObject, fromVersion: Int): JSONObject {
        if (fromVersion < 1) throw RoomFormatException("unknown schema version $fromVersion")
        return json
    }

    private fun voiceOf(json: JSONObject): BandLevels? = if (json.isNull("voiceDb")) null else BandLevels(doubles(json.getJSONArray("voiceDb")))

    private fun seatsJson(seats: List<SeatMeasure>) = JSONArray().apply { seats.forEach { put(encodeSeat(it)) } }

    private fun encodeBaseline(baseline: Baseline) = JSONObject()
        .put("takenAtMillis", baseline.takenAtMillis)
        .put("voiceDb", baseline.voice?.let { numbers(it.valuesDb) } ?: JSONObject.NULL)
        .put("seats", seatsJson(baseline.seats))

    private fun decodeBaseline(json: JSONObject) =
        Baseline(json.getLong("takenAtMillis"), voiceOf(json), json.getJSONArray("seats").objects().map(::decodeSeat))

    private fun encodeRoom(room: Room) = JSONObject()
        .put("id", room.id)
        .put("name", room.name)
        .put("lengthM", room.lengthM)
        .put("widthM", room.widthM)
        .put("heightM", room.heightM)
        .put("rows", room.rows)
        .put("cols", room.cols)
        .put(
            "surfaces",
            JSONObject()
                .put("floor", room.surfaces.floor.name)
                .put("walls", room.surfaces.walls.name)
                .put("ceiling", room.surfaces.ceiling.name),
        )
        .put("students", room.students)

    private fun decodeRoom(json: JSONObject): Room {
        val surfaces = json.getJSONObject("surfaces")
        return Room(
            id = json.getString("id"),
            name = json.getString("name"),
            lengthM = json.getDouble("lengthM"),
            widthM = json.getDouble("widthM"),
            heightM = json.getDouble("heightM"),
            rows = json.getInt("rows"),
            cols = json.getInt("cols"),
            surfaces = Surfaces(
                floor = Material.valueOf(surfaces.getString("floor")),
                walls = Material.valueOf(surfaces.getString("walls")),
                ceiling = Material.valueOf(surfaces.getString("ceiling")),
            ),
            students = json.getInt("students"),
        )
    }

    private fun encodeSeat(seat: SeatMeasure) = JSONObject()
        .put("row", seat.row)
        .put("col", seat.col)
        .put("noiseDb", seat.noise?.let { numbers(it.valuesDb) } ?: JSONObject.NULL)
        .put("fans", seat.fans?.name ?: JSONObject.NULL)
        .put("rtTakes", JSONArray().apply { seat.rtTakes.forEach { put(numbers(it.t60S)) } })
        .put("lastClap", seat.lastClap?.let(::encodeCurves) ?: JSONObject.NULL)

    private fun decodeSeat(json: JSONObject) = SeatMeasure(
        row = json.getInt("row"),
        col = json.getInt("col"),
        noise = if (json.isNull("noiseDb")) null else BandLevels(doubles(json.getJSONArray("noiseDb"))),
        fans = if (json.isNull("fans")) null else FanState.valueOf(json.getString("fans")),
        rtTakes = json.getJSONArray("rtTakes").arrays().map { RtTake(nullableDoubles(it)) },
        // Rooms saved before the curves were kept have no "lastClap" at all: read that as none.
        lastClap = json.optJSONObject("lastClap")?.let { orNull { decodeCurves(it) } },
    )

    /**
     * For the parts a room can do without: damaged clap curves only lose the charts they feed (V3,
     * V10), a damaged "before" only the comparison, never the room and its current readings.
     */
    private inline fun <T> orNull(decode: () -> T): T? = try {
        decode()
    } catch (e: JSONException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun encodeCurves(curves: ClapCurves) = JSONObject()
        .put(
            "bands",
            JSONArray().apply {
                curves.bands.forEach { band ->
                    put(
                        JSONObject()
                            .put("schroederDb", numbers(band.schroederDb))
                            .put("fit", band.fit?.let { JSONObject().put("slopeDbPerS", it.slopeDbPerS).put("interceptDb", it.interceptDb).put("xiPermille", it.xiPermille) } ?: JSONObject.NULL),
                    )
                }
            },
        )
        .put("waterfallDb", JSONArray().apply { curves.waterfallDb.forEach { put(numbers(it)) } })

    private fun decodeCurves(json: JSONObject) = ClapCurves(
        bands = json.getJSONArray("bands").objects().map { band ->
            BandCurve(
                schroederDb = doubles(band.getJSONArray("schroederDb")),
                fit = band.optJSONObject("fit")?.let { DecayFit(it.getDouble("slopeDbPerS"), it.getDouble("interceptDb"), it.getDouble("xiPermille")) },
            )
        },
        waterfallDb = json.getJSONArray("waterfallDb").arrays().map(::doubles),
    )

    private fun numbers(values: List<Double?>) = JSONArray().apply {
        values.forEach { put(if (it != null && it.isFinite()) it else JSONObject.NULL) }
    }

    private fun doubles(array: JSONArray): List<Double> = List(array.length()) { array.getDouble(it) }

    private fun nullableDoubles(array: JSONArray): List<Double?> =
        List(array.length()) { if (array.isNull(it)) null else array.getDouble(it) }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun JSONArray.arrays(): List<JSONArray> = List(length()) { getJSONArray(it) }
}
