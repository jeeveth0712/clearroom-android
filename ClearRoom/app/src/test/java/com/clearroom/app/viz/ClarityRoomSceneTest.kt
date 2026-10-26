package com.clearroom.app.viz

import com.clearroom.app.acoustics.ClarityModel
import com.clearroom.app.acoustics.ClarityResult
import com.clearroom.app.acoustics.SeatLayout
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.data.SampleData
import com.clearroom.app.viz.math3d.Argb
import com.clearroom.app.viz.math3d.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClarityRoomSceneTest {

    private val room = SampleData.room
    private val clarity = assertIs<ClarityResult.Ready>(ClarityModel.evaluate(SampleData.session)).room
    private val colours = RoomSceneColours(
        floor = Argb(0xFF101010.toInt()),
        seatTile = Argb(0xFF181818.toInt()),
        walls = Argb(0xFF202020.toInt()),
        speaker = Argb(0xFF303030.toInt()),
    )
    private val labels = RoomSceneLabels(speaker = "Speaker", back = "Back")
    private val bandColours = StiBand.entries.zip(listOf(0xFFAA0000, 0xFFAA5500, 0xFF5599CC, 0xFF0044AA)).associate { (band, argb) -> band to Argb(argb.toInt()) }
    private val scene = ClarityRoomScene.build(room, clarity, colours, labels) { bandColours.getValue(it) }
    private val seatCount = room.rows * room.cols

    /** The builder's order: one bar per seat in the clarity's seat order, then the speaker. */
    private val bars = scene.solids.dropLast(1)

    @Test
    fun `a perfect seat reaches 80 percent of the room height`() {
        assertEquals(2.4, ClarityRoomScene.barHeightM(sti = 1.0, roomHeightM = 3.0), 1e-12)
        assertEquals(1.2, ClarityRoomScene.barHeightM(sti = 0.5, roomHeightM = 3.0), 1e-12)
    }

    @Test
    fun `a seat scoring zero still shows as a short stub`() {
        assertEquals(0.05, ClarityRoomScene.barHeightM(sti = 0.0, roomHeightM = 3.0), 1e-12)
    }

    @Test
    fun `the ground is the four walls, then the floor, then a tile per seat`() {
        assertEquals(4 + 1 + seatCount, scene.ground.size)
        assertEquals(List(4) { colours.walls }, scene.ground.take(4).map { it.color })
        assertEquals(colours.floor, scene.ground[4].color)
        assertEquals(List(seatCount) { colours.seatTile }, scene.ground.drop(5).map { it.color })
    }

    @Test
    fun `the walls are the room's full size`() {
        val corners = scene.ground.take(4).flatMap { it.points }
        assertEquals(room.widthM, corners.maxOf { it.x })
        assertEquals(room.heightM, corners.maxOf { it.y })
        assertEquals(room.lengthM, corners.maxOf { it.z })
    }

    @Test
    fun `there is a bar for every seat and the speaker cone`() {
        assertEquals(seatCount + 1, scene.solids.size)
        assertEquals(List(9) { colours.speaker }, scene.solids.last().faces.map { it.color })
    }

    @Test
    fun `each bar stands on its seat, as tall as its score, in its band's colour`() {
        bars.zip(clarity.seats).forEach { (bar, seat) ->
            val points = bar.faces.flatMap { it.points }
            val spot = SeatLayout.position(room, seat.seat)
            assertEquals(spot.xM, bar.centre.x, 1e-9, "x of ${seat.seat}")
            assertEquals(spot.yM, bar.centre.z, 1e-9, "z of ${seat.seat}")
            assertEquals(0.0, points.minOf { it.y })
            assertEquals(ClarityRoomScene.barHeightM(seat.sti, room.heightM), points.maxOf { it.y }, 1e-12)
            assertTrue(bar.faces.all { it.color == bandColours.getValue(seat.band) }, "colour of ${seat.seat}")
        }
    }

    @Test
    fun `each seat's tile sits under its bar and is wider than it`() {
        val tiles = scene.ground.drop(5)
        tiles.zip(bars).forEach { (tile, bar) ->
            assertEquals(bar.centre.x, tile.centre.x, 1e-9)
            assertEquals(bar.centre.z, tile.centre.z, 1e-9)
            assertEquals(ClarityRoomScene.MAX_TILE_WIDTH_M, tile.points.maxOf { it.x } - tile.points.minOf { it.x }, 1e-9)
        }
        assertTrue(ClarityRoomScene.tileWidthM(room) > ClarityRoomScene.barWidthM(room))
    }

    @Test
    fun `bars are as wide as set and stay inside the room`() {
        bars.forEach { bar ->
            val points = bar.faces.flatMap { it.points }
            assertEquals(ClarityRoomScene.MAX_BAR_WIDTH_M, points.maxOf { it.x } - points.minOf { it.x }, 1e-9)
            assertTrue(points.all { it.x in 0.0..room.widthM && it.z in 0.0..room.lengthM && it.y <= room.heightM })
        }
    }

    @Test
    fun `in a roomy layout bars and tiles are full width`() {
        assertEquals(0.45, ClarityRoomScene.barWidthM(room), 1e-12)
        assertEquals(0.70, ClarityRoomScene.tileWidthM(room.copy(rows = 1, cols = 1)), 1e-12)
    }

    @Test
    fun `where seats are close together bars and tiles narrow so they never merge`() {
        val cramped = room.copy(lengthM = 3.5, widthM = 2.5) // 5 x 6 seats only 5 cm apart front-to-back
        assertEquals(0.04, ClarityRoomScene.barWidthM(cramped), 1e-12)
        assertEquals(0.045, ClarityRoomScene.tileWidthM(cramped), 1e-12)
        assertTrue(ClarityRoomScene.tileWidthM(cramped) < SeatLayout.spacingM(cramped))
    }

    @Test
    fun `the speaker stands at the front, at its floor-plan spot`() {
        val cone = scene.solids.last()
        val spot = SeatLayout.speaker(room)
        val points = cone.faces.flatMap { it.points }
        assertEquals(spot.xM, (points.minOf { it.x } + points.maxOf { it.x }) / 2, 1e-9)
        assertEquals(spot.yM, (points.minOf { it.z } + points.maxOf { it.z }) / 2, 1e-9)
        assertTrue(points.maxOf { it.z } < SeatLayout.FIRST_ROW_M, "the speaker is in front of the first row")
    }

    @Test
    fun `labels name the speaker above its cone and the back edge`() {
        val byText = scene.labels.associateBy { it.text }
        val speaker = SeatLayout.speaker(room)
        assertTrue(byText.getValue("Speaker").at.y > ClarityRoomScene.SPEAKER_HEIGHT_M)
        assertEquals(speaker.xM, byText.getValue("Speaker").at.x)
        assertEquals(Vec3(room.widthM / 2, 0.0, room.lengthM), byText.getValue("Back").at)
    }
}
