package com.clearroom.app.viz.math3d

import kotlin.math.roundToInt

/**
 * A colour packed as 0xAARRGGBB, the same layout as android.graphics.Color ints, so the 3D maths
 * needs no UI types. The renderer turns it into a Compose Color only when painting.
 */
@JvmInline
value class Argb(val packed: Int) {
    val alpha: Int get() = (packed ushr 24) and 0xFF
    val red: Int get() = (packed shr 16) and 0xFF
    val green: Int get() = (packed shr 8) and 0xFF
    val blue: Int get() = packed and 0xFF

    /** Red, green and blue scaled by [shade] (0 = black, 1 = unchanged); alpha is kept. */
    fun shaded(shade: Float): Argb {
        require(shade in 0f..1f) { "shade must be in [0, 1], was $shade" }
        fun scale(channel: Int) = (channel * shade).roundToInt()
        return of(alpha, scale(red), scale(green), scale(blue))
    }

    companion object {
        fun of(alpha: Int, red: Int, green: Int, blue: Int): Argb {
            require(listOf(alpha, red, green, blue).all { it in 0..255 }) { "channels must be 0..255: $alpha, $red, $green, $blue" }
            return Argb((alpha shl 24) or (red shl 16) or (green shl 8) or blue)
        }
    }
}
