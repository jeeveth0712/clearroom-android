package com.clearroom.app.testing

import com.clearroom.app.audio.AudioClip
import com.clearroom.app.daylog.LogSecond
import com.clearroom.app.daylog.LogStore
import com.clearroom.app.daylog.LogWriter
import com.clearroom.app.daylog.SoundLabel
import com.clearroom.app.daylog.SoundLabeller
import com.clearroom.app.daylog.StoredLog

/** [LogStore] in memory: each log is a list per room, keyed by start time. Records what happened to it. */
class InMemoryLogStore : LogStore {
    val logs = mutableMapOf<String, MutableMap<Long, MutableList<LogSecond>>>()
    var openWriters = 0
        private set

    override fun open(roomId: String, startMillis: Long): LogWriter {
        val rows = logs.getOrPut(roomId) { mutableMapOf() }.getOrPut(startMillis) { mutableListOf() }
        openWriters++
        return object : LogWriter {
            override fun append(second: LogSecond) {
                rows += second
            }

            override fun flush() = Unit

            override fun close() {
                openWriters--
            }
        }
    }

    override fun latest(roomId: String): StoredLog? =
        logs[roomId]?.maxByOrNull { it.key }?.let { (start, rows) -> StoredLog(start, rows.toList()) }

    override fun deleteAll(roomId: String) {
        logs.remove(roomId)
    }
}

/** A [SoundLabeller] that hands out [labels] in turn (then null), and remembers being closed. */
class ScriptedLabeller(private val labels: List<SoundLabel?> = emptyList()) : SoundLabeller {
    var calls = 0
        private set
    var closed = false
        private set

    override fun label(clip: AudioClip): SoundLabel? = labels.getOrNull(calls++)

    override fun close() {
        closed = true
    }
}
