package com.clearroom.app.viz.math3d

import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CameraTest {

    private val camera = OrbitCamera(
        target = Vec3(3.5, 0.75, 4.5),
        yawRad = 0.0,
        pitchRad = 0.0,
        distanceM = 10.0,
        minDistanceM = 5.0,
        maxDistanceM = 40.0,
    )

    // --- OrbitCamera -----------------------------------------------------------------------------

    @Test
    fun `at yaw 0 and pitch 0 the camera is level, behind the target towards the back`() {
        assertEquals(Vec3(3.5, 0.75, 14.5), camera.eye)
    }

    @Test
    fun `a quarter turn moves the camera to the right-hand side`() {
        val eye = camera.copy(yawRad = PI / 2).eye // set directly: orbitBy would lift a level camera to 5°
        assertEquals(13.5, eye.x, 1e-9)
        assertEquals(4.5, eye.z, 1e-9)
    }

    @Test
    fun `turning around the room has no limit`() {
        assertEquals(7.0, camera.orbitBy(7.0, 0.0).yawRad)
        assertEquals(-7.0, camera.orbitBy(-7.0, 0.0).yawRad)
    }

    @Test
    fun `the camera stays at its distance from the target however it turns`() {
        val turned = camera.orbitBy(1.1, 0.6)
        assertEquals(10.0, (turned.eye - turned.target).length, 1e-9)
    }

    @Test
    fun `tilting stops just above the floor and just short of straight down`() {
        assertEquals(OrbitCamera.MIN_PITCH_RAD, camera.orbitBy(0.0, -3.0).pitchRad)
        assertEquals(OrbitCamera.MAX_PITCH_RAD, camera.orbitBy(0.0, 3.0).pitchRad)
    }

    @Test
    fun `pinching out moves closer, down to the minimum distance`() {
        assertEquals(5.0, camera.zoomBy(2.0).distanceM)
        assertEquals(5.0, camera.zoomBy(100.0).distanceM)
    }

    @Test
    fun `pinching in moves away, up to the maximum distance`() {
        assertEquals(20.0, camera.zoomBy(0.5).distanceM)
        assertEquals(40.0, camera.zoomBy(0.01).distanceM)
    }

    @Test
    fun `a zoom factor must be positive`() {
        assertFailsWith<IllegalArgumentException> { camera.zoomBy(0.0) }
        assertFailsWith<IllegalArgumentException> { camera.zoomBy(-1.0) }
    }

    @Test
    fun `the home view looks at the middle of the room from behind and above the back row`() {
        val home = OrbitCamera.homeView(widthM = 7.0, lengthM = 9.0, heightM = 3.0)
        assertEquals(Vec3(3.5, 0.75, 4.5), home.target)
        assertTrue(home.eye.z > 9.0, "eye ${home.eye} should be behind the back wall")
        assertTrue(home.eye.y > 3.0, "eye ${home.eye} should be above the ceiling line")
        val diagonal = sqrt(7.0 * 7.0 + 9.0 * 9.0 + 3.0 * 3.0)
        assertEquals(1.3 * diagonal, home.distanceM, 1e-9)
        assertTrue(home.distanceM in home.minDistanceM..home.maxDistanceM)
    }

    // --- Projection ------------------------------------------------------------------------------

    private val screen = Projection(Projection.DEFAULT_FOV_Y_RAD, width = 1000f, height = 500f)

    @Test
    fun `a point straight ahead lands in the centre of the screen`() {
        val (x, y) = assertNotNull(screen.toScreen(Vec3(0.0, 0.0, -5.0)))
        assertEquals(500f, x, 1e-3f)
        assertEquals(250f, y, 1e-3f)
    }

    @Test
    fun `up is up on screen and right is right`() {
        val (_, aboveY) = assertNotNull(screen.toScreen(Vec3(0.0, 1.0, -5.0)))
        val (rightX, _) = assertNotNull(screen.toScreen(Vec3(1.0, 0.0, -5.0)))
        assertTrue(aboveY < 250f, "screen y grows downwards, so a higher point has a smaller y: $aboveY")
        assertTrue(rightX > 500f)
    }

    @Test
    fun `a square stays square on a wide screen`() {
        val (cx, cy) = assertNotNull(screen.toScreen(Vec3(0.0, 0.0, -5.0)))
        val (x, y) = assertNotNull(screen.toScreen(Vec3(1.0, 1.0, -5.0)))
        assertEquals(x - cx, cy - y, 1e-3f)
    }

    @Test
    fun `farther things look smaller`() {
        val (near, _) = assertNotNull(screen.toScreen(Vec3(1.0, 0.0, -2.0)))
        val (far, _) = assertNotNull(screen.toScreen(Vec3(1.0, 0.0, -8.0)))
        assertTrue(near - 500f > far - 500f)
    }

    @Test
    fun `points behind or touching the camera aren't drawn`() {
        assertNull(screen.toScreen(Vec3(0.0, 0.0, 1.0)))
        assertNull(screen.toScreen(Vec3(0.0, 0.0, 0.0)))
        assertNull(screen.toScreen(Vec3(0.0, 0.0, -0.01)))
        assertNotNull(screen.toScreen(Vec3(0.0, 0.0, -Projection.NEAR_M - 1e-6)))
    }
}
