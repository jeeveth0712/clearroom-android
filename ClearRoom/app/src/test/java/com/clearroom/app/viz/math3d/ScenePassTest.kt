package com.clearroom.app.viz.math3d

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScenePassTest {

    private val red = Argb(0xFFFF0000.toInt())
    private val blue = Argb(0xFF0000FF.toInt())
    private val grey = Argb(0xFF808080.toInt())

    /** Level camera 10 m behind the origin (towards +z), looking at it. */
    private val camera = OrbitCamera(Vec3(0.0, 0.0, 0.0), yawRad = 0.0, pitchRad = 0.0, distanceM = 10.0, minDistanceM = 1.0, maxDistanceM = 50.0)

    private fun project(geometry: SceneGeometry, from: OrbitCamera = camera) = ScenePass.project(geometry, from, width = 800f, height = 600f)

    // --- Depth packing ---------------------------------------------------------------------------

    @Test
    fun `the index comes back out of the packed key`() {
        assertEquals(7, ScenePass.indexOf(ScenePass.packDepth(3.25, 7)))
        assertEquals(Int.MAX_VALUE, ScenePass.indexOf(ScenePass.packDepth(0.0, Int.MAX_VALUE)))
    }

    @Test
    fun `sorting packed keys orders by depth, the same as a reference sort`() {
        val random = Random(42)
        val depths = List(500) { random.nextDouble(0.0, 60.0) }
        val packed = LongArray(depths.size) { ScenePass.packDepth(depths[it], it) }.also { it.sort() }
        val reference = depths.indices.sortedBy { depths[it].toFloat() }
        assertEquals(reference.map { depths[it].toFloat() }, packed.map { depths[ScenePass.indexOf(it)].toFloat() })
    }

    @Test
    fun `negative depth or index is rejected`() {
        assertFailsWith<IllegalArgumentException> { ScenePass.packDepth(-1.0, 0) }
        assertFailsWith<IllegalArgumentException> { ScenePass.packDepth(1.0, -1) }
    }

    // --- Faces and solids ------------------------------------------------------------------------

    @Test
    fun `only the sides of a box facing the camera are drawn`() {
        val box = Primitives.box(Vec3(-1.0, -1.0, -1.0), Vec3(1.0, 1.0, 1.0), grey)
        val frame = project(SceneGeometry(faces = box))
        assertEquals(1, frame.faces.size) // looking level and straight on: just the back face (+z)
    }

    @Test
    fun `an open surface is drawn even from underneath`() {
        val below = camera.copy(pitchRad = -0.3) // built directly: orbitBy would clamp the pitch
        val frame = project(SceneGeometry(faces = listOf(Primitives.floor(2.0, 2.0, grey))), below)
        assertEquals(1, frame.faces.size)
    }

    @Test
    fun `nearer faces are painted after farther ones`() {
        val far = Primitives.box(Vec3(-0.5, -0.5, -4.0), Vec3(0.5, 0.5, -3.0), red)
        val near = Primitives.box(Vec3(-0.5, -0.5, 3.0), Vec3(0.5, 0.5, 4.0), blue)
        val frame = project(SceneGeometry(faces = near + far)) // listed near first: the sort must swap them
        val shade = near.facing(Vec3(0.0, 0.0, 1.0)).shade // level view: only each box's back face shows
        assertEquals(listOf(red.shaded(shade), blue.shaded(shade)), frame.faces.map { it.color })
    }

    @Test
    fun `a solid is painted as a whole, nearer solids after farther ones`() {
        val above = camera.copy(pitchRad = 0.6) // sees tops and back faces
        val far = Solid(Primitives.box(Vec3(-0.5, 0.0, -2.0), Vec3(0.5, 3.0, -1.0), red)) // tall, so its top is near the camera's height
        val near = Solid(Primitives.box(Vec3(-0.5, 0.0, 1.0), Vec3(0.5, 0.2, 2.0), blue))
        val colours = project(SceneGeometry(solids = listOf(near, far)), above).faces.map { if (it.color.red > it.color.blue) "red" else "blue" }
        val firstBlue = colours.indexOf("blue")
        assertTrue(firstBlue > 0 && colours.drop(firstBlue).all { it == "blue" }, "all of the far solid, then all of the near one: $colours")
    }

    @Test
    fun `faces behind the camera are dropped`() {
        val behind = Face3(listOf(Vec3(0.0, 0.0, 12.0), Vec3(1.0, 0.0, 12.0), Vec3(0.0, 1.0, 12.0)), grey, closedSolid = false)
        val frame = project(SceneGeometry(faces = listOf(behind), solids = listOf(Solid(listOf(behind)))))
        assertTrue(frame.faces.isEmpty())
    }

    @Test
    fun `painted colours carry each face's shading`() {
        val box = Primitives.box(Vec3(-1.0, -1.0, -1.0), Vec3(1.0, 1.0, 1.0), grey)
        val back = box.facing(Vec3(0.0, 0.0, 1.0))
        val frame = project(SceneGeometry(faces = box))
        assertEquals(grey.shaded(back.shade), frame.faces.single().color)
    }

    @Test
    fun `screen points are x, y pairs for every corner`() {
        val square = Face3(listOf(Vec3(-1.0, -1.0, 0.0), Vec3(1.0, -1.0, 0.0), Vec3(1.0, 1.0, 0.0), Vec3(-1.0, 1.0, 0.0)), grey, closedSolid = false)
        val points = project(SceneGeometry(faces = listOf(square))).faces.single().points
        assertEquals(8, points.size)
        assertEquals(400f, (points[0] + points[2]) / 2, 1e-3f)
        assertTrue(points[1] > points[5], "the bottom edge is lower on screen than the top edge")
    }

    // --- Ground ----------------------------------------------------------------------------------

    @Test
    fun `ground faces keep their own order and are never sorted`() {
        val nearTile = Primitives.floorRect(-1.0, 2.0, 1.0, 3.0, blue)
        val farTile = Primitives.floorRect(-1.0, -3.0, 1.0, -2.0, red)
        val frame = project(SceneGeometry(ground = listOf(nearTile, farTile)), camera.copy(pitchRad = 0.5))
        assertEquals(listOf(blue.shaded(nearTile.shade), red.shaded(farTile.shade)), frame.ground.map { it.color })
    }

    @Test
    fun `walls facing away from the camera are left out of the ground`() {
        val walls = Primitives.wallsFacingIn(widthM = 2.0, lengthM = 2.0, heightM = 1.0, color = grey)
        val behindTheBack = OrbitCamera(Vec3(1.0, 0.5, 1.0), yawRad = 0.0, pitchRad = 0.5, distanceM = 6.0, minDistanceM = 1.0, maxDistanceM = 20.0)
        // Straight behind, the camera is between the side walls, so it sees them from inside too.
        assertEquals(3, project(SceneGeometry(ground = walls), behindTheBack).ground.size)
        // Turned past the right wall's line, that wall drops out as well: just the front and left are left.
        assertEquals(2, project(SceneGeometry(ground = walls), behindTheBack.copy(yawRad = 0.6)).ground.size)
    }

    // --- Lines and labels ------------------------------------------------------------------------

    @Test
    fun `lines with an end behind the camera are dropped, others are kept`() {
        val lines = listOf(
            Line3(Vec3(-1.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0), grey),
            Line3(Vec3(0.0, 0.0, 0.0), Vec3(0.0, 0.0, 20.0), grey),
        )
        val frame = project(SceneGeometry(lines = lines))
        assertEquals(1, frame.lines.size)
        assertEquals(400f, (frame.lines[0].x1 + frame.lines[0].x2) / 2, 1e-3f) // centred across
    }

    @Test
    fun `labels land where their point does, and labels behind the camera are dropped`() {
        val frame = project(SceneGeometry(labels = listOf(Label3(Vec3(0.0, 0.0, 0.0), "Speaker"), Label3(Vec3(0.0, 0.0, 20.0), "Hidden"))))
        val label = frame.labels.single()
        assertEquals("Speaker", label.text)
        assertEquals(400f, label.x, 1e-3f)
        assertEquals(300f, label.y, 1e-3f)
    }
}
