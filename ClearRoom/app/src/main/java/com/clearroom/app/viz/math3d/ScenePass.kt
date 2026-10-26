package com.clearroom.app.viz.math3d

import kotlin.math.max

/** A face ready to paint: screen points as x, y pairs in pixels, with its shaded colour. */
class ScreenFace(val points: FloatArray, val color: Argb)

class ScreenLine(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Argb)

/** Where a label's centre lands on screen. */
class ScreenLabel(val x: Float, val y: Float, val text: String)

/** One frame, in painting order: [ground], then [faces] far to near, then [lines], then [labels]. */
class ScreenScene(val ground: List<ScreenFace>, val faces: List<ScreenFace>, val lines: List<ScreenLine>, val labels: List<ScreenLabel>)

/**
 * One frame's 3D → 2D pass (painter's algorithm, compose-3d-scene skill): to view space, drop
 * faces behind the camera or (for solids) facing away, project, then sort far to near so nearer
 * shapes are painted over farther ones. Pure Kotlin, so it's unit-tested without a screen.
 */
object ScenePass {

    fun project(scene: SceneGeometry, camera: OrbitCamera, width: Float, height: Float): ScreenScene {
        val view = camera.view()
        val eye = camera.eye
        val projection = Projection(Projection.DEFAULT_FOV_Y_RAD, width, height)

        fun screenFace(face: Face3): ScreenFace? {
            if (face.closedSolid && !face.facesTowards(eye)) return null
            val points = FloatArray(face.points.size * 2)
            face.points.forEachIndexed { i, p ->
                val (x, y) = projection.toScreen(view.transform(p)) ?: return null
                points[2 * i] = x
                points[2 * i + 1] = y
            }
            return ScreenFace(points, face.color.shaded(face.shade))
        }

        // A solid is sorted as a whole by its centre; a loose face by its own centre. Anything
        // behind the camera gets depth 0 here and is dropped when projected.
        val groups = scene.solids.map { it.faces } + scene.faces.map { listOf(it) }
        val centres = scene.solids.map { it.centre } + scene.faces.map { it.centre }
        val order = LongArray(groups.size) { i -> packDepth(max(0.0, -view.transform(centres[i]).z), i) }.also { it.sort() }
        val farToNear = ArrayList<ScreenFace>()
        for (i in order.indices.reversed()) groups[indexOf(order[i])].mapNotNullTo(farToNear, ::screenFace)

        val lines = scene.lines.mapNotNull { line ->
            val a = projection.toScreen(view.transform(line.from)) ?: return@mapNotNull null
            val b = projection.toScreen(view.transform(line.to)) ?: return@mapNotNull null
            ScreenLine(a.first, a.second, b.first, b.second, line.color)
        }
        val labels = scene.labels.mapNotNull { label ->
            projection.toScreen(view.transform(label.at))?.let { (x, y) -> ScreenLabel(x, y, label.text) }
        }
        return ScreenScene(scene.ground.mapNotNull(::screenFace), farToNear, lines, labels)
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
