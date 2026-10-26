package com.clearroom.app.viz.math3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A camera circling [target]: [yawRad] turns around the vertical axis (0 = behind the back row,
 * looking at the front), [pitchRad] lifts it above the floor. Immutable: gestures make new ones.
 */
data class OrbitCamera(
    val target: Vec3,
    val yawRad: Double,
    val pitchRad: Double,
    val distanceM: Double,
    val minDistanceM: Double,
    val maxDistanceM: Double,
) {
    val eye: Vec3
        get() = target + Vec3(
            distanceM * cos(pitchRad) * sin(yawRad),
            distanceM * sin(pitchRad),
            distanceM * cos(pitchRad) * cos(yawRad),
        )

    fun view(): Mat4 = Mat4.lookAt(eye, target, Vec3.UP)

    /** Pitch stays between 5° and 85°: never under the floor, never at the pole where "up" is lost. */
    fun orbitBy(yawRad: Double, pitchRad: Double) =
        copy(yawRad = this.yawRad + yawRad, pitchRad = (this.pitchRad + pitchRad).coerceIn(MIN_PITCH_RAD, MAX_PITCH_RAD))

    /** Pinch [factor] > 1 moves closer. */
    fun zoomBy(factor: Double): OrbitCamera {
        require(factor > 0.0) { "zoom factor must be > 0, was $factor" }
        return copy(distanceM = (distanceM / factor).coerceIn(minDistanceM, maxDistanceM))
    }

    companion object {
        val MIN_PITCH_RAD = 5.0 * PI / 180
        val MAX_PITCH_RAD = 85.0 * PI / 180

        /**
         * The home view (double-tap returns here): behind and above the back row, looking at the
         * middle of the room, so the front with the speaker is at the top, as on the floor plan.
         */
        fun homeView(widthM: Double, lengthM: Double, heightM: Double): OrbitCamera {
            val diagonal = sqrt(widthM * widthM + lengthM * lengthM + heightM * heightM)
            return OrbitCamera(
                target = Vec3(widthM / 2, heightM / 4, lengthM / 2),
                yawRad = 0.0,
                pitchRad = 35.0 * PI / 180,
                distanceM = 1.3 * diagonal,
                minDistanceM = 0.5 * diagonal,
                maxDistanceM = 4.0 * diagonal,
            )
        }
    }
}

/** Perspective projection from the camera's view space onto a [width] × [height] pixel screen. */
class Projection(fovYRad: Double, private val width: Float, private val height: Float) {
    private val focal = 1 / tan(fovYRad / 2)
    private val aspect = width / height

    /** Screen x, y in pixels, or null for points behind or too close to the camera. */
    fun toScreen(view: Vec3): Pair<Float, Float>? {
        if (view.z > -NEAR_M) return null
        val ndcX = (focal / aspect) * view.x / -view.z
        val ndcY = focal * view.y / -view.z
        return ((ndcX + 1) / 2 * width).toFloat() to ((1 - ndcY) / 2 * height).toFloat()
    }

    companion object {
        /** Anything nearer than 5 cm to the camera isn't drawn (no clipping, so it's dropped). */
        const val NEAR_M = 0.05
        val DEFAULT_FOV_Y_RAD = 45.0 * PI / 180
    }
}
