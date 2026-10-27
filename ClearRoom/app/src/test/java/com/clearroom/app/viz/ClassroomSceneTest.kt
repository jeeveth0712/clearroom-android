package com.clearroom.app.viz

import com.clearroom.app.acoustics.SeatLayout
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SeatId
import com.clearroom.app.viz.math3d.Argb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The classroom picture used by Room and the coloured desk view used by Results. */
class ClassroomSceneTest {
    private val room = SampleData.room
    private val palette = ClassroomPalette(
        Argb(1), Argb(2), Argb(3), Argb(4), Argb(5), Argb(6), Argb(7), Argb(8), Argb(9),
    )

    @Test
    fun `every sample seat gets furniture and its own desk-top colour`() {
        val desks = SeatLayout.seats(room).mapIndexed { index, seat -> SeatDesk(SeatId(seat.row, seat.col), Argb(index + 20)) }
        val scene = ClassroomScene.build(room, desks, null, palette, null)
        assertTrue(scene.solids.size >= desks.size)
        assertEquals(desks.first().top, scene.solids.first().faces.first().color)
        assertEquals(desks.last().top, scene.solids[(desks.size - 1) * 6].faces.first().color)
        assertTrue(scene.ground.isNotEmpty())
    }

    @Test
    fun `a selected seat's callout and the speaker appear in the result scene`() {
        val seat = SeatId(room.rows - 1, 0)
        val scene = ClassroomScene.build(
            room, listOf(SeatDesk(seat, Argb(20))), SeatLayout.speaker(room), palette,
            RoomSceneLabels("Speaker", "Back"), DeskCallout(seat, "Back seat"),
        )
        assertTrue(scene.labels.any { it.text == "Back seat" })
        assertTrue(scene.labels.any { it.text == "Speaker" })
        assertTrue(scene.solids.size > 6)
    }

    @Test
    fun `narrow seat spacing shrinks furniture and windows stay bounded`() {
        assertTrue(ClassroomScene.furnitureScale(room) in 0.0..1.0)
        assertTrue(ClassroomScene.windowCount(room) in 1..4)
    }
}
