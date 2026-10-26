package com.clearroom.app.daylog

import com.clearroom.app.data.BandLevels
import com.clearroom.app.dsp.OctaveBands
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.util.Locale

/** A saved day log: when it started and its seconds. */
data class StoredLog(val startMillis: Long, val seconds: List<LogSecond>)

/** Appends one log's seconds as they come; [flush] makes them survive a crash, [close] ends the log. */
interface LogWriter {
    fun append(second: LogSecond)

    fun flush()

    fun close()
}

/** Day logs, one per session, kept per room. */
interface LogStore {
    /** Starts a new log for [roomId] that began at [startMillis]. */
    fun open(roomId: String, startMillis: Long): LogWriter

    /** The room's most recent log, or null if it has none. */
    fun latest(roomId: String): StoredLog?

    /** Removes every log of [roomId] (when the room is deleted). */
    fun deleteAll(roomId: String)
}

/**
 * Day logs as plain text, one file per log at `<dir>/<roomId>/<startMillis>.csv`, one second per
 * line: `time,125 Hz,…,8 kHz,label`. Appending a line never rewrites earlier ones, so 8 hours
 * (28,800 lines) cost nothing extra, and a crash loses at most the lines since the last flush.
 * A cut-off last line is skipped when reading. Only numbers and a label: never audio (NFR-6).
 * The doc keeps "one JSON file per room"; the logs live beside it because rewriting a growing
 * JSON file every minute is slow and loses everything if the phone dies mid-write.
 */
class CsvLogStore(private val dir: File) : LogStore {

    override fun open(roomId: String, startMillis: Long): LogWriter {
        val roomDir = roomDir(roomId).apply { mkdirs() }
        return CsvLogWriter(File(roomDir, "$startMillis$SUFFIX").bufferedWriter())
    }

    override fun latest(roomId: String): StoredLog? {
        val newest = roomDir(roomId).listFiles { file -> file.name.endsWith(SUFFIX) }
            ?.mapNotNull { file -> file.name.removeSuffix(SUFFIX).toLongOrNull()?.let { it to file } }
            ?.maxByOrNull { it.first } ?: return null
        val seconds = try {
            newest.second.useLines { lines -> lines.mapNotNull(::parse).toList() }
        } catch (e: IOException) {
            return null
        }
        return StoredLog(newest.first, seconds)
    }

    override fun deleteAll(roomId: String) {
        roomDir(roomId).deleteRecursively()
    }

    /** Room ids are app-made UUIDs; anything else is refused, so an id can never reach outside [dir]. */
    private fun roomDir(roomId: String): File {
        require(roomId.matches(SAFE_ID)) { "unexpected room id: $roomId" }
        return File(dir, roomId)
    }

    private class CsvLogWriter(private val out: BufferedWriter) : LogWriter {
        private var sinceFlush = 0

        override fun append(second: LogSecond) {
            out.write(format(second))
            out.newLine()
            if (++sinceFlush >= FLUSH_EVERY_SECONDS) flush()
        }

        override fun flush() {
            out.flush()
            sinceFlush = 0
        }

        override fun close() = out.close()
    }

    internal companion object {
        const val SUFFIX = ".csv"

        /** Skill: flush every 60 s, so a crash loses at most a minute. */
        const val FLUSH_EVERY_SECONDS = 60
        private val SAFE_ID = Regex("[A-Za-z0-9-]{1,64}")

        fun format(second: LogSecond): String = buildString {
            append(second.timeMillis)
            second.bands.valuesDb.forEach { append(',').append(String.format(Locale.ROOT, "%.2f", it)) }
            append(',').append(second.label?.name?.lowercase(Locale.ROOT).orEmpty())
        }

        /** Null for a line that isn't a whole second (the last line if the phone died mid-write). */
        fun parse(line: String): LogSecond? {
            val parts = line.split(',')
            if (parts.size != OctaveBands.COUNT + 2) return null
            val time = parts[0].toLongOrNull() ?: return null
            val bands = parts.subList(1, 1 + OctaveBands.COUNT).map { it.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null }
            val labelText = parts.last()
            val label = if (labelText.isEmpty()) null else SoundLabel.entries.firstOrNull { it.name.equals(labelText, ignoreCase = true) } ?: return null
            return LogSecond(time, BandLevels(bands), label)
        }
    }
}
