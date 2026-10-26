package com.clearroom.app.viz.math3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * One flat polygon of the scene. [closedSolid] faces belong to boxes or cones and are skipped when
 * facing away from the camera; open surfaces (the floor, meshes) are drawn from both sides.
 * Points go counter-clockwise when seen from the side the face points to.
 */
class Face3(val points: List<Vec3>, val color: Argb, val closedSolid: Boolean) {
    init {
        require(points.size >= 3) { "a face needs at least 3 points" }
    }

    val normal: Vec3 = ((points[1] - points[0]) cross (points[2] - points[0])).normalized()

    val centre: Vec3 = points.reduce(Vec3::plus) * (1.0 / points.size)

    /** Flat Lambert shading from a light fixed in the world, so it's worked out once, not per frame. */
    val shade: Float = (AMBIENT + DIFFUSE * max(0.0, normal dot LIGHT)).toFloat()

    /** Seen from [eye], is this the face's front side? */
    fun facesTowards(eye: Vec3): Boolean = normal dot (eye - points[0]) > 0.0

    private companion object {
        val LIGHT = Vec3(0.4, 1.0, 0.3).normalized()
        const val AMBIENT = 0.35
        const val DIFFUSE = 0.65
    }
}

class Line3(val from: Vec3, val to: Vec3, val color: Argb)

class SceneGeometry(val faces: List<Face3>, val lines: List<Line3> = emptyList())

/** The handful of shapes V8–V12 need. */
object Primitives {

    /** An axis-aligned box from [min] to [max] corner: 6 outward-facing quads. */
    fun box(min: Vec3, max: Vec3, color: Argb): List<Face3> {
        require(max.x > min.x && max.y > min.y && max.z > min.z) { "box needs positive size: $min .. $max" }
        val (x0, y0, z0) = min
        val (x1, y1, z1) = max
        fun quad(vararg p: Vec3) = Face3(p.toList(), color, closedSolid = true)
        return listOf(
            quad(Vec3(x0, y1, z0), Vec3(x0, y1, z1), Vec3(x1, y1, z1), Vec3(x1, y1, z0)), // top (+y)
            quad(Vec3(x0, y0, z0), Vec3(x1, y0, z0), Vec3(x1, y0, z1), Vec3(x0, y0, z1)), // bottom (−y)
            quad(Vec3(x0, y0, z1), Vec3(x1, y0, z1), Vec3(x1, y1, z1), Vec3(x0, y1, z1)), // back (+z)
            quad(Vec3(x0, y0, z0), Vec3(x0, y1, z0), Vec3(x1, y1, z0), Vec3(x1, y0, z0)), // front (−z)
            quad(Vec3(x1, y0, z0), Vec3(x1, y1, z0), Vec3(x1, y1, z1), Vec3(x1, y0, z1)), // right (+x)
            quad(Vec3(x0, y0, z0), Vec3(x0, y0, z1), Vec3(x0, y1, z1), Vec3(x0, y1, z0)), // left (−x)
        )
    }

    /** An upright cone made of [sides] triangles plus a base, standing on [baseCentre]. */
    fun cone(baseCentre: Vec3, radiusM: Double, heightM: Double, sides: Int, color: Argb): List<Face3> {
        require(sides >= 3 && radiusM > 0.0 && heightM > 0.0) { "bad cone: $sides sides, r=$radiusM, h=$heightM" }
        val apex = baseCentre + Vec3(0.0, heightM, 0.0)
        val rim = List(sides) { i ->
            val angle = 2 * PI * i / sides
            baseCentre + Vec3(radiusM * cos(angle), 0.0, radiusM * sin(angle))
        }
        val walls = rim.indices.map { i -> Face3(listOf(rim[(i + 1) % sides], rim[i], apex), color, closedSolid = true) }
        val base = Face3(rim, color, closedSolid = true)
        return walls + base
    }

    /** A flat floor rectangle at height 0, visible from above and below. */
    fun floor(widthM: Double, lengthM: Double, color: Argb) = Face3(
        listOf(Vec3(0.0, 0.0, 0.0), Vec3(0.0, 0.0, lengthM), Vec3(widthM, 0.0, lengthM), Vec3(widthM, 0.0, 0.0)),
        color,
        closedSolid = false,
    )

    /** The 12 edges of an axis-aligned box, for drawing the room as a wireframe. */
    fun wireBox(min: Vec3, max: Vec3, color: Argb): List<Line3> {
        val c = listOf(
            Vec3(min.x, min.y, min.z), Vec3(max.x, min.y, min.z), Vec3(max.x, min.y, max.z), Vec3(min.x, min.y, max.z),
            Vec3(min.x, max.y, min.z), Vec3(max.x, max.y, min.z), Vec3(max.x, max.y, max.z), Vec3(min.x, max.y, max.z),
        )
        val edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
        return edges.map { (a, b) -> Line3(c[a], c[b], color) }
    }
}
