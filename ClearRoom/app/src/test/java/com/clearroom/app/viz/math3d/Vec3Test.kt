package com.clearroom.app.viz.math3d

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Vec3Test {

    private fun assertNear(expected: Vec3, actual: Vec3, tolerance: Double = 1e-9) {
        assertEquals(expected.x, actual.x, tolerance, "x of $actual")
        assertEquals(expected.y, actual.y, tolerance, "y of $actual")
        assertEquals(expected.z, actual.z, tolerance, "z of $actual")
    }

    // --- Vec3 ------------------------------------------------------------------------------------

    @Test
    fun `adding, subtracting and scaling work per axis`() {
        val a = Vec3(1.0, 2.0, 3.0)
        val b = Vec3(0.5, -1.0, 4.0)
        assertEquals(Vec3(1.5, 1.0, 7.0), a + b)
        assertEquals(Vec3(0.5, 3.0, -1.0), a - b)
        assertEquals(Vec3(2.0, 4.0, 6.0), a * 2.0)
    }

    @Test
    fun `the dot product of perpendicular directions is zero`() {
        assertEquals(0.0, Vec3(1.0, 0.0, 0.0) dot Vec3(0.0, 5.0, 0.0))
        assertEquals(32.0, Vec3(1.0, 2.0, 3.0) dot Vec3(4.0, 5.0, 6.0))
    }

    @Test
    fun `the cross product follows the right-hand rule`() {
        assertEquals(Vec3(0.0, 0.0, 1.0), Vec3(1.0, 0.0, 0.0) cross Vec3(0.0, 1.0, 0.0))
        assertEquals(Vec3(0.0, 0.0, -1.0), Vec3(0.0, 1.0, 0.0) cross Vec3(1.0, 0.0, 0.0))
    }

    @Test
    fun `length and normalising`() {
        assertEquals(5.0, Vec3(3.0, 4.0, 0.0).length)
        val unit = Vec3(2.0, 2.0, 1.0).normalized()
        assertEquals(1.0, unit.length, 1e-12)
        assertNear(Vec3(2.0 / 3, 2.0 / 3, 1.0 / 3), unit)
    }

    @Test
    fun `a zero vector has no direction`() {
        assertFailsWith<IllegalArgumentException> { Vec3(0.0, 0.0, 0.0).normalized() }
    }

    // --- Mat4.lookAt -----------------------------------------------------------------------------

    private val eye = Vec3(3.0, 4.0, 12.0)
    private val target = Vec3(3.0, 1.0, 4.0)
    private val view = Mat4.lookAt(eye, target, Vec3.UP)

    @Test
    fun `the camera ends up at the origin`() {
        assertNear(Vec3(0.0, 0.0, 0.0), view.transform(eye))
    }

    @Test
    fun `what the camera looks at ends up straight ahead, down minus z`() {
        val distance = (target - eye).length
        assertNear(Vec3(0.0, 0.0, -distance), view.transform(target))
    }

    @Test
    fun `screen right and up match the world`() {
        // Looking from the back of the room (+z) towards the front, world +x is to the right.
        assertTrue(view.transform(target + Vec3(1.0, 0.0, 0.0)).x > 0.0)
        assertTrue(view.transform(target + Vec3(0.0, 1.0, 0.0)).y > 0.0)
    }

    @Test
    fun `distances are kept, so the scene isn't stretched`() {
        val a = Vec3(1.0, 0.0, 2.0)
        val b = Vec3(4.0, 2.5, 7.0)
        assertEquals((b - a).length, (view.transform(b) - view.transform(a)).length, 1e-9)
    }

    @Test
    fun `looking almost straight down gives finite numbers`() {
        val steep = OrbitCamera(target, yawRad = 0.0, pitchRad = 85.0 * PI / 180, distanceM = 10.0, minDistanceM = 1.0, maxDistanceM = 20.0)
        val moved = steep.view().transform(Vec3(1.0, 2.0, 3.0))
        assertTrue(moved.x.isFinite() && moved.y.isFinite() && moved.z.isFinite(), "$moved")
    }
}
