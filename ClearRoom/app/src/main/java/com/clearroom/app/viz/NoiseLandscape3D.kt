package com.clearroom.app.viz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.clearroom.app.daylog.LogPeriod
import com.clearroom.app.ui.theme.SceneColors
import com.clearroom.app.viz.math3d.Argb
import com.clearroom.app.viz.math3d.Label3
import com.clearroom.app.viz.math3d.OrbitCamera
import com.clearroom.app.viz.math3d.Primitives
import com.clearroom.app.viz.math3d.SceneGeometry
import com.clearroom.app.viz.math3d.Solid
import com.clearroom.app.viz.math3d.Vec3
import kotlin.math.max

data class LandscapeLabels(val lowBand: String, val midBand: String, val highBand: String, val start: String, val end: String)

/**
 * V11, the noise landscape (doc "3D scenes"): one bar per period and octave band, x = time,
 * depth = band (125 Hz at the back), height and colour = the band's level in that period. Fan hum
 * shows as tall back rows, traffic in the middle, horns at the front.
 */
object NoiseLandscapeScene {
    const val METRES_PER_PERIOD = 0.5
    const val METRES_PER_BAND = 0.5
    const val HEIGHT_M = 2.0
    private const val BAR_M = 0.38
    private const val MIN_BAR_M = 0.03
    private const val MIN_RANGE_DB = 10.0
    private const val LABEL_OFFSET_M = 0.6

    /** With fewer periods the start and end times sit on top of each other: only the start is named. */
    private const val MIN_PERIODS_FOR_END_LABEL = 3

    /** Where a level sits between the quietest band level (just above 0) and the loudest (1). */
    fun fraction(levelDb: Double, quietestDb: Double, loudestDb: Double): Double {
        val low = quietestDb - 3
        val high = max(loudestDb, low + MIN_RANGE_DB)
        return ((levelDb - low) / (high - low)).coerceIn(0.0, 1.0)
    }

    fun build(periods: List<LogPeriod>, labels: LandscapeLabels, floor: Argb, colourFor: (Double) -> Argb): SceneGeometry {
        require(periods.isNotEmpty()) { "no periods to draw" }
        val levels = periods.flatMap { it.bands.valuesDb }
        val (quietest, loudest) = levels.min() to levels.max()
        val bands = periods.first().bands.valuesDb.size
        val half = BAR_M / 2
        val bars = periods.flatMapIndexed { p, period ->
            period.bands.valuesDb.mapIndexed { band, db ->
                val f = fraction(db, quietest, loudest)
                val x = p * METRES_PER_PERIOD
                val z = band * METRES_PER_BAND
                Solid(Primitives.box(Vec3(x - half, 0.0, z - half), Vec3(x + half, max(MIN_BAR_M, f * HEIGHT_M), z + half), colourFor(f)))
            }
        }
        val width = (periods.size - 1) * METRES_PER_PERIOD
        val depth = (bands - 1) * METRES_PER_BAND
        // Times float above the back (125 Hz) row: the far side is higher on screen, so they never clip.
        val timeHeight = HEIGHT_M + LABEL_OFFSET_M
        return SceneGeometry(
            ground = listOf(Primitives.floorRect(-METRES_PER_PERIOD / 2, -METRES_PER_BAND / 2, width + METRES_PER_PERIOD / 2, depth + METRES_PER_BAND / 2, floor)),
            solids = bars,
            labels = listOf(
                Label3(Vec3(-LABEL_OFFSET_M, 0.0, 0.0), labels.lowBand),
                Label3(Vec3(-LABEL_OFFSET_M, 0.0, 3 * METRES_PER_BAND), labels.midBand),
                Label3(Vec3(-LABEL_OFFSET_M, 0.0, depth), labels.highBand),
                Label3(Vec3(0.0, timeHeight, 0.0), labels.start),
            ) + if (periods.size >= MIN_PERIODS_FOR_END_LABEL) listOf(Label3(Vec3(width, timeHeight, 0.0), labels.end)) else emptyList(),
        )
    }
}

@Composable
fun NoiseLandscape3D(periods: List<LogPeriod>, labels: LandscapeLabels, description: String, modifier: Modifier = Modifier) {
    val geometry = remember(periods, labels) { NoiseLandscapeScene.build(periods, labels, SceneColors.Floor.argb(), ::levelRamp) }
    val home = remember(periods.size) {
        OrbitCamera.homeView(
            max(periods.size - 1, 1) * NoiseLandscapeScene.METRES_PER_PERIOD,
            6 * NoiseLandscapeScene.METRES_PER_BAND,
            NoiseLandscapeScene.HEIGHT_M,
        ).chartView()
    }
    FramedScene3D(geometry, home, description, modifier)
}
