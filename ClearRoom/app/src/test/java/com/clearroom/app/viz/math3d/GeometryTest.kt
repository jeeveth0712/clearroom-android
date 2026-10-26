package com.clearroom.app.viz.math3d

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeometryTest {

    private val grey = Argb(0xFF808080.toInt())

    // --- Face3 -----------------------------------------------------------------------------------

    @Test
    fun `counter-clockwise points face towards the viewer`() {
        val face = Face3(listOf(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0)), grey, closedSolid = true)
        assertEquals(Vec3(0.0, 0.0, 1.0), face.normal)
        assertTrue(face.facesTowards(Vec3(0.2, 0.2, 5.0)))
        assertFalse(face.facesTowards(Vec3(0.2, 0.2, -5.0)))
    }

    @Test
    fun `the centre is the mean of the corners`() {
        val square = Face3(listOf(Vec3(0.0, 0.0, 0.0), Vec3(2.0, 0.0, 0.0), Vec3(2.0, 0.0, 2.0), Vec3(0.0, 0.0, 2.0)), grey, closedSolid = false)
        assertEquals(Vec3(1.0, 0.0, 1.0), square.centre)
    }

    @Test
    fun `faces turned to the light are bright and faces turned away get only ambient light`() {
        val box = Primitives.box(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 1.0, 1.0), grey)
        val top = box.facing(Vec3(0.0, 1.0, 0.0))
        val bottom = box.facing(Vec3(0.0, -1.0, 0.0))
        assertTrue(top.shade > 0.9f, "top ${top.shade}")
        assertEquals(0.35f, bottom.shade, 1e-6f)
        assertTrue(box.all { it.shade in 0.35f..1f })
    }

    @Test
    fun `a face needs at least three points`() {
        assertFailsWith<IllegalArgumentException> { Face3(listOf(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0)), grey, closedSolid = true) }
    }

    // --- Primitives ------------------------------------------------------------------------------

    @Test
    fun `every face of a box points outwards`() {
        val min = Vec3(1.0, 0.0, 2.0)
        val max = Vec3(1.5, 0.8, 2.5)
        val middle = (min + max) * 0.5
        val faces = Primitives.box(min, max, grey)
        assertEquals(6, faces.size)
        faces.forEach { assertTrue(it.normal dot (it.centre - middle) > 0.0, "normal ${it.normal} at ${it.centre}") }
        assertEquals(6, faces.map { it.normal }.toSet().size)
        assertTrue(faces.all { it.closedSolid })
    }

    @Test
    fun `a box needs a positive size on every axis`() {
        assertFailsWith<IllegalArgumentException> { Primitives.box(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 0.0, 1.0), grey) }
    }

    @Test
    fun `a cone has a wall per side and a base, all pointing outwards`() {
        val base = Vec3(3.0, 0.0, 1.0)
        val faces = Primitives.cone(base, radiusM = 0.3, heightM = 1.6, sides = 8, grey)
        assertEquals(9, faces.size)
        val inside = base + Vec3(0.0, 0.4, 0.0)
        faces.forEach { assertTrue(it.normal dot (it.centre - inside) > 0.0, "normal ${it.normal} at ${it.centre}") }
        assertEquals(-1.0, faces.last().normal.y, 1e-12) // the base faces the floor
        assertEquals(1.6, faces.first().points.maxOf { it.y }, 1e-12)
    }

    @Test
    fun `a cone needs three sides and a real size`() {
        assertFailsWith<IllegalArgumentException> { Primitives.cone(Vec3(0.0, 0.0, 0.0), 0.3, 1.0, sides = 2, grey) }
        assertFailsWith<IllegalArgumentException> { Primitives.cone(Vec3(0.0, 0.0, 0.0), 0.0, 1.0, sides = 8, grey) }
    }

    @Test
    fun `the floor covers the room, faces up and shows from both sides`() {
        val floor = Primitives.floor(7.0, 9.0, grey)
        assertEquals(Vec3(0.0, 1.0, 0.0), floor.normal)
        assertFalse(floor.closedSolid)
        assertEquals(Vec3(3.5, 0.0, 4.5), floor.centre)
    }

    @Test
    fun `a wire box has its 12 edges, each along one axis`() {
        val edges = Primitives.wireBox(Vec3(0.0, 0.0, 0.0), Vec3(7.0, 3.0, 9.0), grey)
        assertEquals(12, edges.size)
        val lengths = edges.map { (it.to - it.from).length }.sorted()
        assertEquals(List(4) { 3.0 } + List(4) { 7.0 } + List(4) { 9.0 }, lengths)
    }
}

/** The face whose normal is [direction], allowing for rounding in the normal's maths. */
internal fun List<Face3>.facing(direction: Vec3): Face3 = single { (it.normal - direction).length < 1e-9 }
