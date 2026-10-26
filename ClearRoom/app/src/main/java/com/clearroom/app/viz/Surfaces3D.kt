package com.clearroom.app.viz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.clearroom.app.acoustics.ClarityGrid
import com.clearroom.app.acoustics.ClarityModel
import com.clearroom.app.acoustics.FloorPoint
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.data.ClapCurves
import com.clearroom.app.ui.theme.SceneColors
import com.clearroom.app.ui.theme.color
import com.clearroom.app.viz.math3d.Argb
import com.clearroom.app.viz.math3d.Face3
import com.clearroom.app.viz.math3d.Label3
import com.clearroom.app.viz.math3d.Line3
import com.clearroom.app.viz.math3d.OrbitCamera
import com.clearroom.app.viz.math3d.Primitives
import com.clearroom.app.viz.math3d.SceneGeometry
import com.clearroom.app.viz.math3d.Vec3
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

// --- V9 Clarity terrain ------------------------------------------------------------------------------

/** One stretch of a contour line on the floor plan. */
data class ContourSegment(val from: FloorPoint, val to: FloorPoint)

/**
 * Marching squares: where the grid crosses [level], as straight pieces, one or two per cell. A
 * corner exactly on the level counts as above it, so a line never runs through the same point twice.
 */
fun contourSegments(grid: ClarityGrid, level: Double): List<ContourSegment> {
    val segments = ArrayList<ContourSegment>()
    for (j in 0 until grid.rows) {
        for (i in 0 until grid.columns) {
            // Corners anticlockwise from the front-left; each edge joins corner k and corner k+1.
            val corners = listOf(i to j, i + 1 to j, i + 1 to j + 1, i to j + 1)
            val crossings = corners.indices.mapNotNull { k ->
                val (ia, ja) = corners[k]
                val (ib, jb) = corners[(k + 1) % 4]
                val a = grid.corner(ia, ja)
                val b = grid.corner(ib, jb)
                if ((a >= level) == (b >= level)) return@mapNotNull null
                val t = (level - a) / (b - a)
                val pa = grid.cornerPoint(ia, ja)
                val pb = grid.cornerPoint(ib, jb)
                FloorPoint(pa.xM + t * (pb.xM - pa.xM), pa.yM + t * (pb.yM - pa.yM))
            }
            if (crossings.size >= 2) segments += ContourSegment(crossings[0], crossings[1])
            if (crossings.size == 4) segments += ContourSegment(crossings[2], crossings[3])
        }
    }
    return segments
}

data class TerrainLabels(val speaker: String, val back: String)

/** V9: STI across the floor as a surface, height and colour by clarity, with the 0.60 line on it. */
object ClarityTerrainScene {
    /** Relief per unit of STI: half the room's longer side, so a 0.1 change is clearly visible. */
    fun heightPerStiM(grid: ClarityGrid): Double = 0.5 * max(grid.widthM, grid.lengthM)

    /**
     * The STI the surface rises from: the lowest score in the room rounded down to a tenth, so the
     * terrain sits on its floor instead of floating a pedestal's height above it.
     */
    fun baseSti(grid: ClarityGrid): Double = floor(grid.sti.min() * 10) / 10

    fun heightM(sti: Double, grid: ClarityGrid): Double = max(MIN_HEIGHT_M, (sti - baseSti(grid)) * heightPerStiM(grid))

    fun build(grid: ClarityGrid, speaker: FloorPoint, labels: TerrainLabels, floor: Argb, line: Argb, bandColour: (StiBand) -> Argb): SceneGeometry {
        fun vertex(i: Int, j: Int) = grid.cornerPoint(i, j).let { Vec3(it.xM, heightM(grid.corner(i, j), grid), it.yM) }
        val faces = ArrayList<Face3>(grid.columns * grid.rows * 2)
        for (j in 0 until grid.rows) {
            for (i in 0 until grid.columns) {
                val colour = bandColour(StiBand.from(grid.cell(i, j).coerceIn(0.0, 1.0)))
                val (v00, v10, v11, v01) = listOf(vertex(i, j), vertex(i + 1, j), vertex(i + 1, j + 1), vertex(i, j + 1))
                // Two triangles per cell (the four corners needn't lie in one plane), both facing up.
                faces += Face3(listOf(v00, v01, v11), colour, closedSolid = false, outlined = false)
                faces += Face3(listOf(v00, v11, v10), colour, closedSolid = false, outlined = false)
            }
        }
        val lift = CONTOUR_LIFT_M + heightM(ClarityModel.TARGET_STI, grid)
        val contour = contourSegments(grid, ClarityModel.TARGET_STI).map {
            Line3(Vec3(it.from.xM, lift, it.from.yM), Vec3(it.to.xM, lift, it.to.yM), line)
        }
        val nearSpeaker = grid.corner((speaker.xM / grid.cellWidthM).roundToInt().coerceIn(0, grid.columns), (speaker.yM / grid.cellLengthM).roundToInt().coerceIn(0, grid.rows))
        return SceneGeometry(
            ground = listOf(Primitives.floor(grid.widthM, grid.lengthM, floor)),
            faces = faces,
            lines = contour,
            labels = listOf(
                Label3(Vec3(speaker.xM, heightM(nearSpeaker, grid) + LABEL_LIFT_M, speaker.yM), labels.speaker),
                Label3(Vec3(grid.widthM / 2, 0.0, grid.lengthM), labels.back),
            ),
        )
    }

    private const val CONTOUR_LIFT_M = 0.02 // 2 cm above the surface, so the line is always drawn on top
    private const val LABEL_LIFT_M = 0.5
    private const val MIN_HEIGHT_M = 0.02
}

@Composable
fun ClarityTerrain3D(grid: ClarityGrid, speaker: FloorPoint, labels: TerrainLabels, description: String, modifier: Modifier = Modifier) {
    val geometry = remember(grid, speaker, labels) {
        ClarityTerrainScene.build(grid, speaker, labels, SceneColors.Floor.argb(), Argb(WHITE)) { it.color().argb() }
    }
    val home = remember(grid) {
        OrbitCamera.homeView(grid.widthM, grid.lengthM, ClarityTerrainScene.heightM(grid.sti.max(), grid))
    }
    FramedScene3D(geometry, home, description, modifier)
}

// --- V10 Echo waterfall ------------------------------------------------------------------------------

data class WaterfallLabels(val lowBand: String, val midBand: String, val highBand: String, val start: String, val oneSecond: String, val twoSeconds: String)

/**
 * V10: each band's level after the clap as its own ridge (x = time, depth = band, height = level),
 * coloured by level: the classic waterfall, where a longer ridge means that pitch rings longer.
 */
object EchoWaterfallScene {
    /** Draw every 50 ms: smooth enough to read, and about 500 faces instead of 2,800. */
    const val TIME_STEP_S = 0.05
    const val METRES_PER_S = 2.0
    const val METRES_PER_BAND = 0.6
    const val HEIGHT_M = 1.6
    private const val FLOOR_DB = -60.0

    /** Height of a level; never quite 0, so every ridge piece is a real shape. */
    fun heightM(levelDb: Double): Double = max(MIN_HEIGHT_M, (levelDb.coerceIn(FLOOR_DB, 0.0) - FLOOR_DB) / -FLOOR_DB * HEIGHT_M)

    /** A waterfall needs two time steps in every band; a short or edited one shows the "clap again" text instead. */
    fun canDraw(curves: ClapCurves): Boolean = sampled(curves).minOf { it.size } >= 2

    /** Every [TIME_STEP_S] of each band's waterfall row. */
    fun sampled(curves: ClapCurves): List<List<Double>> {
        val every = (TIME_STEP_S / ClapCurves.STEP_S).roundToInt()
        return curves.waterfallDb.map { band -> band.filterIndexed { k, _ -> k % every == 0 } }
    }

    fun build(curves: ClapCurves, labels: WaterfallLabels, floor: Argb, colourFor: (Double) -> Argb): SceneGeometry {
        val rows = sampled(curves)
        val steps = rows.minOf { it.size }
        require(steps >= 2) { "waterfall needs at least two time steps" }
        val dx = TIME_STEP_S * METRES_PER_S
        val faces = ArrayList<Face3>()
        rows.forEachIndexed { band, levels ->
            val z = band * METRES_PER_BAND
            for (k in 0 until steps - 1) {
                if (levels[k] <= FLOOR_DB && levels[k + 1] <= FLOOR_DB) continue // faded out: nothing to show
                val fraction = ((levels[k] + levels[k + 1]) / 2 - FLOOR_DB) / -FLOOR_DB
                val (x0, x1) = k * dx to (k + 1) * dx
                faces += Face3(
                    listOf(Vec3(x0, 0.0, z), Vec3(x1, 0.0, z), Vec3(x1, heightM(levels[k + 1]), z), Vec3(x0, heightM(levels[k]), z)),
                    colourFor(fraction.coerceIn(0.0, 1.0)),
                    closedSolid = false,
                    outlined = false,
                )
            }
        }
        val widthM = (steps - 1) * TIME_STEP_S * METRES_PER_S
        val depthM = (rows.size - 1) * METRES_PER_BAND
        val front = depthM + TIME_LABEL_OFFSET_M
        return SceneGeometry(
            ground = listOf(Primitives.floorRect(0.0, 0.0, widthM, depthM, floor)),
            faces = faces,
            labels = listOf(
                Label3(Vec3(-BAND_LABEL_OFFSET_M, 0.0, 0.0), labels.lowBand),
                Label3(Vec3(-BAND_LABEL_OFFSET_M, 0.0, 3 * METRES_PER_BAND), labels.midBand),
                Label3(Vec3(-BAND_LABEL_OFFSET_M, 0.0, depthM), labels.highBand),
                Label3(Vec3(0.0, 0.0, front), labels.start),
                Label3(Vec3(1.0 * METRES_PER_S, 0.0, front), labels.oneSecond),
                Label3(Vec3(widthM, 0.0, front), labels.twoSeconds),
            ),
        )
    }

    private const val BAND_LABEL_OFFSET_M = 0.5
    private const val TIME_LABEL_OFFSET_M = 0.9 // further out than the band names, so "0 s" and "8 kHz" don't meet
    private const val MIN_HEIGHT_M = 0.01
}

/** The band whose fitted echo time is longest ("rings longest"); null if no band has a fit. */
fun longestRingingBand(curves: ClapCurves): Int? =
    curves.bands.indices.filter { curves.bands[it].fit != null }.maxByOrNull { requireNotNull(curves.bands[it].fit).t60S }

/** Sequential teal ramp for levels, quiet (0) to loud (1): never the clarity band colours. */
fun levelRamp(fraction: Double): Argb {
    val stops = listOf(0xFF1F3B4D, 0xFF2A9D8F, 0xFFE9F5DB).map { Argb(it.toInt()) }
    val scaled = fraction.coerceIn(0.0, 1.0) * (stops.size - 1)
    val k = scaled.toInt().coerceAtMost(stops.size - 2)
    val t = scaled - k
    fun mix(a: Int, b: Int) = (a + (b - a) * t).roundToInt()
    val (a, b) = stops[k] to stops[k + 1]
    return Argb.of(255, mix(a.red, b.red), mix(a.green, b.green), mix(a.blue, b.blue))
}

@Composable
fun EchoWaterfall3D(curves: ClapCurves, labels: WaterfallLabels, description: String, modifier: Modifier = Modifier) {
    val geometry = remember(curves, labels) { EchoWaterfallScene.build(curves, labels, SceneColors.Floor.argb(), ::levelRamp) }
    val home = remember(geometry) {
        val steps = EchoWaterfallScene.sampled(curves).minOf { it.size }
        val room = OrbitCamera.homeView(
            (steps - 1) * EchoWaterfallScene.TIME_STEP_S * EchoWaterfallScene.METRES_PER_S,
            (curves.waterfallDb.size - 1) * EchoWaterfallScene.METRES_PER_BAND,
            EchoWaterfallScene.HEIGHT_M,
        )
        room.chartView()
    }
    FramedScene3D(geometry, home, description, modifier)
}

private const val WHITE = 0xFFFFFFFF.toInt()
private const val CHART_YAW_RAD = -20.0 * PI / 180
private const val CHART_PITCH_RAD = 32.0 * PI / 180
private const val CHART_DISTANCE_FACTOR = 1.35

/**
 * The view for 3D charts with time running left to right (V10, V11): from in front, slightly to
 * the left and lower than a room view. Their near ends come close, so it steps back further.
 */
internal fun OrbitCamera.chartView(): OrbitCamera =
    copy(yawRad = CHART_YAW_RAD, pitchRad = CHART_PITCH_RAD, distanceM = distanceM * CHART_DISTANCE_FACTOR)
