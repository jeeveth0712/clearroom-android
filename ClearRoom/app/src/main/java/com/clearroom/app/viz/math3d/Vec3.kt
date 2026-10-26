package com.clearroom.app.viz.math3d

import kotlin.math.sqrt

/**
 * A point or direction in the 3D scene, in metres. World axes (fixed for every view): x across the
 * room, y up, z from the front wall towards the back. The floor plan's (x, y) is the world's (x, z).
 */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Double) = Vec3(x * scale, y * scale, z * scale)

    infix fun dot(other: Vec3): Double = x * other.x + y * other.y + z * other.z

    infix fun cross(other: Vec3) = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    val length: Double get() = sqrt(this dot this)

    fun normalized(): Vec3 {
        val len = length
        require(len > 0.0) { "can't normalise a zero vector" }
        return this * (1 / len)
    }

    companion object {
        val UP = Vec3(0.0, 1.0, 0.0)
    }
}

/**
 * 4 × 4 transform stored column-major (like android.opengl.Matrix): element (row, col) is at
 * index col * 4 + row. Only what the renderer needs: the camera's view transform.
 */
class Mat4 private constructor(private val m: DoubleArray) {

    /** Transforms a point (w = 1). */
    fun transform(p: Vec3) = Vec3(
        m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12],
        m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13],
        m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14],
    )

    companion object {
        /**
         * View matrix for a camera at [eye] looking at [target]: afterwards the camera sits at the
         * origin looking down −z, with +y up and +x to the right on screen.
         */
        fun lookAt(eye: Vec3, target: Vec3, up: Vec3): Mat4 {
            val forward = (target - eye).normalized()
            val right = (forward cross up).normalized()
            val trueUp = right cross forward
            return Mat4(
                doubleArrayOf(
                    right.x, trueUp.x, -forward.x, 0.0,
                    right.y, trueUp.y, -forward.y, 0.0,
                    right.z, trueUp.z, -forward.z, 0.0,
                    -(right dot eye), -(trueUp dot eye), forward dot eye, 1.0,
                ),
            )
        }
    }
}
