package com.clearroom.app.viz.math3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * One flat polygon of the scene. [closedSolid] faces belong to boxes, cones or the room's walls and
 * are skipped when facing away from the camera; open surfaces (the floor, meshes) show from both
 * sides. Points go counter-clockwise when seen from the side the face points to.
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

    internal companion object {
        /** From above, behind the back row and a little to the right: the sides people see get distinct, soft shades. */
        val LIGHT = Vec3(0.3, 1.0, 0.6).normalized()
        const val AMBIENT = 0.55
        const val DIFFUSE = 0.45
    }
}

/** A convex shape (a bar, the speaker cone), sorted as a whole: its own visible faces never overlap. */
class Solid(val faces: List<Face3>) {
    init {
        require(faces.isNotEmpty()) { "a solid needs at least one face" }
    }

    val centre: Vec3 = faces.map { it.centre }.reduce(Vec3::plus) * (1.0 / faces.size)
}

class Line3(val from: Vec3, val to: Vec3, val color: Argb)

/** Text pinned to a point in the scene; it turns with the scene and is drawn on top of everything. */
class Label3(val at: Vec3, val text: String)

/**
 * Everything one 3D view draws. [ground] is painted first, in list order: the room's walls and
 * floor, then anything lying flat on the floor. The camera stays outside the room and above the
 * floor, so the walls it can see and the floor are always behind whatever stands in the room.
 * [solids] and loose [faces] are then sorted far to near; [lines] and [labels] go on top.
 */
class SceneGeometry(
    val ground: List<Face3> = emptyList(),
    val solids: List<Solid> = emptyList(),
    val faces: List<Face3> = emptyList(),
    val lines: List<Line3> = emptyList(),
    val labels: List<Label3> = emptyList(),
)

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

    /** A flat rectangle lying on the floor, facing up and visible from both sides. */
    fun floorRect(x0: Double, z0: Double, x1: Double, z1: Double, color: Argb): Face3 {
        require(x1 > x0 && z1 > z0) { "rectangle needs positive size: ($x0, $z0) .. ($x1, $z1)" }
        return Face3(listOf(Vec3(x0, 0.0, z0), Vec3(x0, 0.0, z1), Vec3(x1, 0.0, z1), Vec3(x1, 0.0, z0)), color, closedSolid = false)
    }

    /** The whole floor of a [widthM] × [lengthM] room. */
    fun floor(widthM: Double, lengthM: Double, color: Argb) = floorRect(0.0, 0.0, widthM, lengthM, color)

    /**
     * The room's four walls, facing inwards. As one-sided faces they make a cut-away model: seen
     * from outside, the walls nearest the camera face away and are skipped, so you always see in.
     */
    fun wallsFacingIn(widthM: Double, lengthM: Double, heightM: Double, color: Argb): List<Face3> {
        require(widthM > 0.0 && lengthM > 0.0 && heightM > 0.0) { "room needs positive size: $widthM × $lengthM × $heightM" }
        val (w, l, h) = Triple(widthM, lengthM, heightM)
        fun wall(vararg p: Vec3) = Face3(p.toList(), color, closedSolid = true)
        return listOf(
            wall(Vec3(0.0, 0.0, 0.0), Vec3(w, 0.0, 0.0), Vec3(w, h, 0.0), Vec3(0.0, h, 0.0)), // front wall, faces +z
            wall(Vec3(0.0, 0.0, l), Vec3(0.0, h, l), Vec3(w, h, l), Vec3(w, 0.0, l)), // back wall, faces −z
            wall(Vec3(0.0, 0.0, 0.0), Vec3(0.0, h, 0.0), Vec3(0.0, h, l), Vec3(0.0, 0.0, l)), // left wall, faces +x
            wall(Vec3(w, 0.0, 0.0), Vec3(w, 0.0, l), Vec3(w, h, l), Vec3(w, h, 0.0)), // right wall, faces −x
        )
    }
}
