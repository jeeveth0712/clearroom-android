package com.clearroom.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real files on the phone, in a throwaway folder; never the app's own rooms folder. */
@RunWith(AndroidJUnit4::class)
class JsonRoomStoreTest {

    private val directory = File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "rooms-test-${UUID.randomUUID()}",
    )
    private var clock = 1_000L
    private fun store() = JsonRoomStore(directory, nowMillis = { clock++ })

    private fun room(id: String, name: String = "Room $id") =
        Room(id, name, 9.0, 7.0, 3.0, 5, 6, Surfaces(Material.TILE, Material.CONCRETE, Material.CONCRETE), 0)

    @After
    fun deleteTestFolder() {
        directory.deleteRecursively()
    }

    @Test
    fun save_thenLoad_returnsTheSameSession() = runBlocking {
        store().save(SampleData.session)
        assertEquals(SampleData.session, store().load(SampleData.ROOM_ID))
    }

    @Test
    fun aFreshStore_readsWhatAnEarlierOneSaved_likeAfterAnAppRestart() = runBlocking {
        val beforeRestart = store()
        beforeRestart.save(Session(room("r1", "Physics lab")))
        val afterRestart = store()
        assertEquals("Physics lab", afterRestart.load("r1")?.room?.name)
        assertEquals(listOf("r1"), afterRestart.list().map { it.id })
    }

    @Test
    fun list_isNewestFirst_withSummaries() = runBlocking {
        val store = store()
        store.save(Session(room("older")))
        store.save(SampleData.session.withRoom(SampleData.room.copy(id = "newer", name = "Newer")))
        val listed = store.list().map { (it as SavedRoom.Readable).summary }
        assertEquals(listOf("newer", "older"), listed.map { it.id })
        assertEquals(3, listed.first().measuredSeats)
        assertEquals(30, listed.first().seatCount)
    }

    @Test
    fun savingAgain_replacesTheRoom_insteadOfAddingOne() = runBlocking {
        val store = store()
        store.save(Session(room("r1", "First name")))
        store.save(Session(room("r1", "Second name")))
        assertEquals("Second name", store.load("r1")?.room?.name)
        assertEquals(1, store.list().size)
    }

    @Test
    fun aCorruptFile_isListedAsDamaged_andDoesNotHideTheOthers() = runBlocking {
        val store = store()
        store.save(Session(room("good")))
        File(directory, "broken.json").writeText("{ this is not json")
        val listed = store.list()
        assertEquals(listOf<SavedRoom>(SavedRoom.Damaged("broken")), listed.filterIsInstance<SavedRoom.Damaged>())
        assertEquals(listOf("good", "broken"), listed.map { it.id }) // readable first
        assertNull(store.load("broken"))
    }

    @Test
    fun load_ofAMissingRoom_isNull() = runBlocking {
        assertNull(store().load("never-saved"))
    }

    @Test
    fun delete_removesTheRoom() = runBlocking {
        val store = store()
        store.save(Session(room("r1")))
        store.delete("r1")
        assertNull(store.load("r1"))
        assertEquals(emptyList<SavedRoom>(), store.list())
    }

    @Test
    fun fileNames_comeFromTheId_neverFromTheUserTypedName() = runBlocking {
        store().save(Session(room("r1", name = "../../evil/name")))
        assertEquals(listOf("r1.json"), directory.list()?.toList())
    }

    @Test
    fun unsafeIds_areRefused() = runBlocking {
        listOf("../escape", "a/b", "", "x".repeat(65)).forEach { id ->
            val refused = try {
                store().load(id)
                false
            } catch (e: IllegalArgumentException) {
                true
            }
            assertTrue("id '$id' was accepted", refused)
        }
    }

    @Test
    fun aSave_leavesNoTemporaryFilesBehind() = runBlocking {
        val store = store()
        repeat(3) { store.save(Session(room("r1", "Version $it"))) }
        assertEquals(listOf("r1.json"), directory.list()?.toList())
    }
}
