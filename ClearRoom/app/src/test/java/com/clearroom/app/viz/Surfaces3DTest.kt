package com.clearroom.app.viz

import com.clearroom.app.acoustics.ClarityGrid
import com.clearroom.app.acoustics.ClarityModel
import com.clearroom.app.acoustics.FloorPoint
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.data.BandCurve
import com.clearroom.app.data.ClapCurves
import com.clearroom.app.data.DecayFit
import com.clearroom.app.data.SampleData
import com.clearroom.app.viz.math3d.Argb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** V9 clarity terrain and V10 echo waterfall: the geometry builders and their helpers. */
class Surfaces3DTest {

    private val grey = Argb(0xFF808080.toInt())
    private val white = Argb(0xFFFFFFFF.toInt())
    private val bandColours = StiBand.entries.associateWith { Argb(0xFF000000.toInt() or (it.ordinal + 1)) }

    /** STI rising from 0.4 at the left wall to 0.8 at the right, the same all the way down the room. */
    private val ramp = ClarityGrid(widthM = 4.0, lengthM = 2.0, columns = 4, rows = 2, sti = List(3) { listOf(0.4, 0.5, 0.6, 0.7, 0.8) }.flatten())

    // --- Contours -----------------------------------------------------------------------------------

    @Test
    fun `the 0,60 line runs straight down the room where the ramp crosses it`() {
        val segments = contourSegments(ramp, 0.6)
        assertTrue(segments.isNotEmpty())
        segments.forEach {
            assertEquals(2.0, it.from.xM, 1e-9)
            assertEquals(2.0, it.to.xM, 1e-9)
        }
        assertEquals(2.0, segments.sumOf { kotlin.math.abs(it.to.yM - it.from.yM) }, 1e-9) // the full 2 m length
    }

    @Test
    fun `a level the grid never crosses has no contour`() {
        assertTrue(contourSegments(ramp, 0.95).isEmpty())
        assertTrue(contourSegments(ramp, 0.1).isEmpty())
    }

    @Test
    fun `a saddle cell gives two pieces of line`() {
        val saddle = ClarityGrid(1.0, 1.0, 1, 1, listOf(0.8, 0.4, 0.4, 0.8)) // high corners opposite each other
        assertEquals(2, contourSegments(saddle, 0.6).size)
    }

    // --- V9 terrain ---------------------------------------------------------------------------------

    private val labels = TerrainLabels(speaker = "Speaker", back = "Back")

    @Test
    fun `the terrain rises from just below its lowest score`() {
        assertEquals(0.4, ClarityTerrainScene.baseSti(ramp), 1e-12)
        assertEquals(2.0, ClarityTerrainScene.heightPerStiM(ramp)) // half the 4 m side
        assertEquals(0.4, ClarityTerrainScene.heightM(0.6, ramp), 1e-12)
        assertEquals(0.02, ClarityTerrainScene.heightM(0.4, ramp), 1e-12) // the lowest spot still has a sliver of height
    }

    @Test
    fun `the terrain has two smooth triangles per cell coloured by band, and the 0,60 line on top`() {
        val scene = ClarityTerrainScene.build(ramp, FloorPoint(2.0, 0.5), labels, grey, white) { bandColours.getValue(it) }
        assertEquals(2 * ramp.columns * ramp.rows, scene.faces.size)
        assertTrue(scene.faces.none { it.outlined }, "a surface, not a wireframe")
        assertTrue(scene.faces.all { it.normal.y > 0.0 }, "every piece faces up")
        assertEquals(contourSegments(ramp, ClarityModel.TARGET_STI).size, scene.lines.size)
        assertTrue(scene.lines.all { it.color == white && it.from.y > ClarityTerrainScene.heightM(0.6, ramp) })
        assertEquals(listOf("Speaker", "Back"), scene.labels.map { it.text })
        assertEquals(1, scene.ground.size)
    }

    @Test
    fun `the sample room's terrain covers the floor and stays in sensible bounds`() {
        val grid = assertNotNull(ClarityModel.floorGrid(SampleData.session))
        val scene = ClarityTerrainScene.build(grid, FloorPoint(3.5, 1.0), labels, grey, white) { bandColours.getValue(it) }
        val points = scene.faces.flatMap { it.points }
        assertEquals(0.0, points.minOf { it.x }, 1e-9)
        assertEquals(SampleData.room.widthM, points.maxOf { it.x }, 1e-9)
        assertEquals(SampleData.room.lengthM, points.maxOf { it.z }, 1e-9)
        assertTrue(points.all { it.y > 0.0 && it.y <= ClarityTerrainScene.heightM(1.0, grid) })
    }

    // --- V10 waterfall -------------------------------------------------------------------------------

    private val waterfallLabels = WaterfallLabels("125 Hz", "1 kHz", "8 kHz", "0 s", "1 s", "2 s")

    /** Every band falls 30 dB per second from 0 dB for 2 s (200 points), down to the -60 dB floor. */
    private val curves = ClapCurves(
        bands = List(7) { band -> BandCurve(listOf(0.0, -1.0), DecayFit(-30.0 - 5 * band, 0.0, 2.0)) },
        waterfallDb = List(7) { List(200) { k -> maxOf(-60.0, -30.0 * k * 0.01) } },
    )

    @Test
    fun `the waterfall is drawn every 50 ms`() {
        val sampled = EchoWaterfallScene.sampled(curves)
        assertEquals(7, sampled.size)
        assertEquals(40, sampled[0].size)
        assertEquals(-1.5, sampled[0][1], 1e-9) // 50 ms in: 30 dB/s × 0.05 s
    }

    @Test
    fun `levels map to height from the -60 dB floor to 0 dB at the top`() {
        assertEquals(EchoWaterfallScene.HEIGHT_M, EchoWaterfallScene.heightM(0.0), 1e-12)
        assertEquals(EchoWaterfallScene.HEIGHT_M / 2, EchoWaterfallScene.heightM(-30.0), 1e-12)
        assertEquals(0.01, EchoWaterfallScene.heightM(-80.0), 1e-12) // below the floor: a sliver, never a flat shape
    }

    @Test
    fun `each band is its own ridge, and faded-out stretches are left out`() {
        val scene = EchoWaterfallScene.build(curves, waterfallLabels, grey, ::levelRamp)
        // 30 dB/s reaches -60 dB at 2 s, so all 39 pieces per band still show.
        assertEquals(7 * 39, scene.faces.size)
        val bandDepths = scene.faces.map { it.points.first().z }.distinct().sorted()
        assertEquals(List(7) { it * EchoWaterfallScene.METRES_PER_BAND }, bandDepths)
        assertTrue(scene.faces.none { it.outlined })

        val fast = curves.copy(waterfallDb = List(7) { List(200) { k -> maxOf(-60.0, -120.0 * k * 0.01) } }) // gone after 0.5 s
        assertEquals(7 * 10, EchoWaterfallScene.build(fast, waterfallLabels, grey, ::levelRamp).faces.size)
    }

    @Test
    fun `the waterfall names three bands and three times`() {
        val scene = EchoWaterfallScene.build(curves, waterfallLabels, grey, ::levelRamp)
        assertEquals(listOf("125 Hz", "1 kHz", "8 kHz", "0 s", "1 s", "2 s"), scene.labels.map { it.text })
    }

    @Test
    fun `the longest-ringing band is the one whose fit falls slowest`() {
        assertEquals(0, longestRingingBand(curves)) // -30 dB/s is the slowest fall
        assertNull(longestRingingBand(curves.copy(bands = List(7) { BandCurve(emptyList(), null) })))
    }

    @Test
    fun `the level ramp runs from dark teal to pale mint`() {
        assertEquals(Argb(0xFF1F3B4D.toInt()), levelRamp(0.0))
        assertEquals(Argb(0xFFE9F5DB.toInt()), levelRamp(1.0))
        assertEquals(Argb(0xFF2A9D8F.toInt()), levelRamp(0.5))
        assertEquals(levelRamp(1.0), levelRamp(3.0)) // out of range is clamped
    }

    @Test
    fun `a waterfall too short to draw is recognised`() {
        assertTrue(EchoWaterfallScene.canDraw(curves))
        assertTrue(!EchoWaterfallScene.canDraw(curves.copy(waterfallDb = List(7) { listOf(0.0) })))
    }
}
