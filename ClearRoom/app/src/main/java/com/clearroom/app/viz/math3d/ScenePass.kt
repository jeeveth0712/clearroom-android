package com.clearroom.app.viz.math3d

/** A face ready to paint: screen points as x, y pairs in pixels, with its shaded colour. */
class ScreenFace(val points: FloatArray, val color: Argb)

class ScreenLine(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Argb)

class ScreenScene(val lines: List<ScreenLine>, val faces: List<ScreenFace>)

/**
 * One frame's 3D → 2D pass (painter's algorithm, compose-3d-scene skill): to view space, drop
 * faces behind the camera or (for solids) facing away, project, then sort far to near so nearer
 * faces are painted over farther ones. Pure Kotlin, so it's unit-tested without a screen.
 */
object ScenePass {

    fun project(scene: SceneGeometry, camera: OrbitCamera, width: Float, height: Float): ScreenScene {
        val view = camera.view()
        val eye = camera.eye
        val projection = Projection(Projection.DEFAULT_FOV_Y_RAD, width, height)

        val kept = ArrayList<ScreenFace>(scene.faces.size)
        val depthKeys = LongArray(scene.faces.size)
        for (face in scene.faces) {
            if (face.closedSolid && !face.facesTowards(eye)) continue
            val points = FloatArray(face.points.size * 2)
            var depthSum = 0.0
            var visible = true
            face.points.forEachIndexed { i, p ->
                val inView = view.transform(p)
                val screen = projection.toScreen(inView)
                if (screen == null) {
                    visible = false
                    return@forEachIndexed
                }
                points[2 * i] = screen.first
                points[2 * i + 1] = screen.second
                depthSum += -inView.z
            }
            if (!visible) continue
            depthKeys[kept.size] = packDepth(depthSum / face.points.size, kept.size)
            kept += ScreenFace(points, face.color.shaded(face.shade))
        }

        val order = depthKeys.copyOf(kept.size).also { it.sort() } // nearest first
        val farToNear = List(kept.size) { i -> kept[indexOf(order[kept.size - 1 - i])] }

        val lines = scene.lines.mapNotNull { line ->
            val a = projection.toScreen(view.transform(line.from)) ?: return@mapNotNull null
            val b = projection.toScreen(view.transform(line.to)) ?: return@mapNotNull null
            ScreenLine(a.first, a.second, b.first, b.second, line.color)
        }
        return ScreenScene(lines, farToNear)
    }

    /**
     * Depth and index in one Long: sorting a LongArray is a primitive sort with no boxing. For a
     * positive float the raw bits sort like the value, so the high half orders by depth.
     */
    internal fun packDepth(depth: Double, index: Int): Long {
        require(depth >= 0.0 && index >= 0) { "depth $depth, index $index" }
        return (java.lang.Float.floatToRawIntBits(depth.toFloat()).toLong() shl 32) or index.toLong()
    }

    internal fun indexOf(packed: Long): Int = (packed and 0xFFFF_FFFFL).toInt()
}
