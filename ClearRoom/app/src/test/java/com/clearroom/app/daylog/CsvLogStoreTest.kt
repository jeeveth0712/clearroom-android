package com.clearroom.app.daylog

import com.clearroom.app.data.BandLevels
import com.clearroom.app.testing.flatBands
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CsvLogStoreTest {

    private val dir: File = Files.createTempDirectory("logs").toFile()
    private val store = CsvLogStore(dir)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private val seconds = listOf(
        LogSecond(1_000L, BandLevels(listOf(-50.0, -51.5, -52.25, -53.0, -54.0, -55.0, -56.0)), SoundLabel.FAN),
        LogSecond(2_000L, flatBands(-60.0), null),
    )

    private fun write(roomId: String, start: Long, rows: List<LogSecond>) = store.open(roomId, start).apply { rows.forEach(::append) }.close()

    @Test
    fun `a log survives a write and a read, labels and missing labels included`() {
        write("room-1", 1_000L, seconds)
        assertEquals(StoredLog(1_000L, seconds), store.latest("room-1"))
    }

    @Test
    fun `the latest log is the one that started last`() {
        write("room-1", 1_000L, seconds)
        write("room-1", 5_000L, seconds.take(1))
        assertEquals(5_000L, store.latest("room-1")?.startMillis)
        assertEquals(1, store.latest("room-1")?.seconds?.size)
    }

    @Test
    fun `a room without logs has none`() {
        assertNull(store.latest("room-2"))
    }

    @Test
    fun `a line cut off by a crash is skipped, the rest still loads`() {
        write("room-1", 1_000L, seconds)
        File(dir, "room-1/1000.csv").appendText("3000,-50.00,-51")
        assertEquals(seconds, store.latest("room-1")?.seconds)
    }

    @Test
    fun `rows reach the file every minute, before the log is closed`() {
        val writer = store.open("room-1", 1_000L)
        repeat(CsvLogStore.FLUSH_EVERY_SECONDS) { writer.append(seconds[1]) }
        assertEquals(CsvLogStore.FLUSH_EVERY_SECONDS, File(dir, "room-1/1000.csv").readLines().size)
        writer.close()
    }

    @Test
    fun `deleting a room's logs removes them all`() {
        write("room-1", 1_000L, seconds)
        store.deleteAll("room-1")
        assertNull(store.latest("room-1"))
        assertFalse(File(dir, "room-1").exists())
    }

    @Test
    fun `a room id that could reach outside the logs folder is refused`() {
        assertFailsWith<IllegalArgumentException> { store.open("../rooms", 1L) }
        assertFailsWith<IllegalArgumentException> { store.latest("a/b") }
    }

    @Test
    fun `lines hold the time, seven levels to 0,01 dB and the label`() {
        assertEquals("1000,-50.00,-51.50,-52.25,-53.00,-54.00,-55.00,-56.00,fan", CsvLogStore.format(seconds[0]))
        assertEquals(seconds[0], CsvLogStore.parse(CsvLogStore.format(seconds[0])))
        assertNull(CsvLogStore.parse("1000,-50,-50,-50,-50,-50,-50,-50,unicorn"))
        assertNull(CsvLogStore.parse("1000,-50,-50,NaN,-50,-50,-50,-50,"))
        assertNull(CsvLogStore.parse("not a line"))
    }
}
