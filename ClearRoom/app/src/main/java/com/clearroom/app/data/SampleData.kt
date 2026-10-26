package com.clearroom.app.data

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Built-in sample room (build-plan rule: every screen works on sample data before the mic is
 * used again). Illustrative numbers for a bare, tiled classroom with fans on: echo around a
 * second, a reverberant low end, and a few rejected 125 Hz / 8 kHz fits like real claps give.
 * Levels are dB relative to the phone's full scale, like real measurements. Never saved to disk
 * unless the user saves a copy, which gets its own id.
 */
object SampleData {
    const val ROOM_ID = "sample"

    val room = Room(
        id = ROOM_ID,
        name = "Sample classroom",
        lengthM = 9.0,
        widthM = 7.0,
        heightM = 3.2,
        rows = 5,
        cols = 6,
        surfaces = Surfaces(floor = Material.TILE, walls = Material.CONCRETE, ceiling = Material.CONCRETE),
        students = 0,
    )

    /** Illustrative curves for the sample's latest clap (V3, V10); every sample seat shows the same one. */
    private val sampleClap: ClapCurves by lazy { illustrativeCurves(listOf(1.44, 1.35, 1.20, 1.12, 1.01, 0.91, 0.80)) }

    /** Speaker reading the reference paragraph at 1 m (FR-5). */
    private val voice = BandLevels(listOf(-34.0, -30.0, -28.0, -31.0, -36.0, -42.0, -49.0))

    val session = Session(
        room = room,
        voice = voice,
        seats = listOf(
            SeatMeasure(
                row = 0, col = 2,
                noise = BandLevels(listOf(-58.0, -61.0, -64.0, -67.0, -70.0, -74.0, -79.0)),
                fans = FanState.FAST,
                rtTakes = listOf(
                    RtTake(listOf(null, 1.32, 1.21, 1.12, 1.01, 0.90, 0.78)),
                    RtTake(listOf(1.45, 1.36, 1.18, 1.09, 0.99, 0.88, null)),
                    RtTake(listOf(1.41, 1.30, 1.24, 1.14, 1.03, 0.91, 0.80)),
                ),
                lastClap = sampleClap,
            ),
            SeatMeasure(
                row = 2, col = 3,
                noise = BandLevels(listOf(-56.0, -60.0, -63.0, -66.0, -69.0, -73.0, -78.0)),
                fans = FanState.FAST,
                rtTakes = listOf(
                    RtTake(listOf(1.48, 1.34, 1.22, 1.11, 1.02, 0.89, 0.79)),
                    RtTake(listOf(null, 1.29, 1.19, 1.13, 1.00, 0.92, 0.81)),
                    RtTake(listOf(1.39, 1.33, 1.23, 1.10, 1.04, 0.90, null)),
                ),
                lastClap = sampleClap,
            ),
            SeatMeasure(
                row = 4, col = 2,
                noise = BandLevels(listOf(-55.0, -59.0, -62.0, -65.0, -68.0, -72.0, -77.0)),
                fans = FanState.FAST,
                rtTakes = listOf(
                    RtTake(listOf(1.44, 1.35, 1.20, 1.12, 1.01, 0.91, 0.80)),
                    RtTake(listOf(1.50, 1.31, 1.24, 1.09, 0.98, 0.88, 0.77)),
                    RtTake(listOf(null, 1.37, 1.21, 1.15, 1.03, 0.90, null)),
                ),
                lastClap = sampleClap,
            ),
        ),
    )

    /**
     * Curves shaped like a real clap's, made from an echo time per band: a Schroeder decay that
     * runs straight and bends slightly at its tail, and a waterfall that decays with a small ripple
     * into a noise floor that's higher at low pitches (fans). Illustrative, not a measurement.
     */
    internal fun illustrativeCurves(t60S: List<Double>): ClapCurves {
        val step = ClapCurves.STEP_S
        val bands = t60S.mapIndexed { band, t60 ->
            val slope = -60.0 / t60
            val points = (SCHROEDER_DEPTH_DB / -slope / step).roundToInt()
            val curve = List(points) { k ->
                val t = k * step
                val tailBend = TAIL_BEND_DB * (k.toDouble() / points).pow(3)
                centiDb(slope * t - tailBend)
            }
            BandCurve(curve, DecayFit(slope, interceptDb = 0.0, xiPermille = 3.0 + band))
        }
        val waterfall = t60S.mapIndexed { band, t60 ->
            val floorDb = -42.0 - 2.5 * band
            List(WATERFALL_POINTS) { k ->
                val t = k * step
                val decayDb = -60.0 / t60 * t
                val withNoise = 10 * log10(10.0.pow(decayDb / 10) + 10.0.pow(floorDb / 10))
                val ripple = 1.2 * sin(2 * PI * (2.0 + band) * t)
                centiDb(max(-60.0, withNoise + ripple))
            }
        }
        return ClapCurves(bands, waterfall)
    }

    private fun centiDb(db: Double): Double = (db * 100).roundToInt() / 100.0

    private const val SCHROEDER_DEPTH_DB = 38.0
    private const val TAIL_BEND_DB = 2.5
    private const val WATERFALL_POINTS = 200
}
