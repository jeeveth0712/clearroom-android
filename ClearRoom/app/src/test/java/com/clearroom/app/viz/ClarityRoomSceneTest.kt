package com.clearroom.app.viz

import com.clearroom.app.acoustics.ClarityModel
import com.clearroom.app.acoustics.ClarityResult
import com.clearroom.app.acoustics.SeatLayout
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.data.SampleData
import com.clearroom.app.viz.math3d.Argb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClarityRoomSceneTest {

    private val room = SampleData.room
    private val clarity = assertIs<ClarityResult.Ready>(ClarityModel.evaluate(SampleData.session)).room
    private val colours = RoomSceneColours(floor = Argb(0xFF101010.toInt()), walls = Argb(0xFF202020.toInt()), speaker = Argb(0xFF303030.toInt()))
    private val bandColours = StiBand.entries.zip(listOf(0xFFAA0000, 0xFFAA5500, 0xFF5599CC, 0xFF0044AA)).associate { (band, argb) -> band to Argb(argb.toInt()) }
    private val scene = ClarityRoomScene.build(room, clarity, colours) { bandColours.getValue(it) }

    /** The builder's order: the floor, then 6 faces per seat in the clarity's seat order, then the speaker. */
    private val bars = scene.faces.drop(1).take(6 * clarity.seats.size).chunked(6)

    @Test
    fun `a perfect seat reaches half the room height`() {
        assertEquals(1.5, ClarityRoomScene.barHeightM(sti = 1.0, roomHeightM = 3.0), 1e-12)
        assertEquals(0.9, ClarityRoomScene.barHeightM(sti = 0.6, roomHeightM = 3.0), 1e-12)
    }

    @Test
    fun `a seat scoring zero still shows as a short stub`() {
        assertEquals(0.05, ClarityRoomScene.barHeightM(sti = 0.0, roomHeightM = 3.0), 1e-12)
    }

    @Test
    fun `the room is drawn as its 12 edges at full size`() {
        assertEquals(12, scene.lines.size)
        val corners = scene.lines.flatMap { listOf(it.from, it.to) }
        assertEquals(room.widthM, corners.maxOf { it.x })
        assertEquals(room.heightM, corners.maxOf { it.y })
        assertEquals(room.lengthM, corners.maxOf { it.z })
        assertTrue(scene.lines.all { it.color == colours.walls })
    }

    @Test
    fun `there is a floor, a bar for every seat and the speaker cone`() {
        assertEquals(1 + 6 * room.rows * room.cols + 9, scene.faces.size)
        assertEquals(colours.floor, scene.faces.first().color)
        assertEquals(List(9) { colours.speaker }, scene.faces.takeLast(9).map { it.color })
    }

    @Test
    fun `each bar stands on its seat, as tall as its score, in its band's colour`() {
        bars.zip(clarity.seats).forEach { (faces, seat) ->
            val points = faces.flatMap { it.points }
            val spot = SeatLayout.position(room, seat.seat)
            assertEquals(spot.xM, (points.minOf { it.x } + points.maxOf { it.x }) / 2, 1e-9, "x of ${seat.seat}")
            assertEquals(spot.yM, (points.minOf { it.z } + points.maxOf { it.z }) / 2, 1e-9, "z of ${seat.seat}")
            assertEquals(0.0, points.minOf { it.y })
            assertEquals(ClarityRoomScene.barHeightM(seat.sti, room.heightM), points.maxOf { it.y }, 1e-12)
            assertTrue(faces.all { it.color == bandColours.getValue(seat.band) }, "colour of ${seat.seat}")
        }
    }

    @Test
    fun `bars are as wide as set and stay inside the room`() {
        bars.forEach { faces ->
            val points = faces.flatMap { it.points }
            assertEquals(ClarityRoomScene.BAR_WIDTH_M, points.maxOf { it.x } - points.minOf { it.x }, 1e-9)
            assertTrue(points.all { it.x in 0.0..room.widthM && it.z in 0.0..room.lengthM && it.y <= room.heightM })
        }
    }

    @Test
    fun `the speaker stands at the front, at its floor-plan spot`() {
        val cone = scene.faces.takeLast(9).flatMap { it.points }
        val spot = SeatLayout.speaker(room)
        assertEquals(spot.xM, (cone.minOf { it.x } + cone.maxOf { it.x }) / 2, 1e-9)
        assertEquals(spot.yM, (cone.minOf { it.z } + cone.maxOf { it.z }) / 2, 1e-9)
        assertTrue(cone.maxOf { it.z } < SeatLayout.FIRST_ROW_M, "the speaker is in front of the first row")
    }
}
