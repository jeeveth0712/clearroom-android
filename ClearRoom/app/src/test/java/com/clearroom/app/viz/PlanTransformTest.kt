package com.clearroom.app.viz

import androidx.compose.ui.geometry.Size
import com.clearroom.app.acoustics.FloorPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlanTransformTest {

    @Test
    fun `a long room in a wide canvas is limited by height and centred across`() {
        val plan = PlanTransform.fit(widthM = 7.0, lengthM = 9.0, canvas = Size(800f, 240f), paddingPx = 10f)
        assertEquals(220f / 9f, plan.pxPerM, absoluteTolerance = 1e-4f)
        val left = plan.toPx(FloorPoint(0.0, 0.0)).x
        val right = plan.toPx(FloorPoint(7.0, 0.0)).x
        assertEquals(800f - right, left, absoluteTolerance = 1e-3f) // same margin both sides
        assertEquals(10f, plan.toPx(FloorPoint(0.0, 0.0)).y, absoluteTolerance = 1e-3f)
        assertEquals(230f, plan.toPx(FloorPoint(0.0, 9.0)).y, absoluteTolerance = 1e-3f)
    }

    @Test
    fun `a wide room in a tall canvas is limited by width and centred down`() {
        val plan = PlanTransform.fit(widthM = 20.0, lengthM = 5.0, canvas = Size(400f, 900f), paddingPx = 0f)
        assertEquals(20f, plan.pxPerM, absoluteTolerance = 1e-4f)
        val top = plan.toPx(FloorPoint(0.0, 0.0)).y
        val bottom = plan.toPx(FloorPoint(0.0, 5.0)).y
        assertEquals(900f - bottom, top, absoluteTolerance = 1e-3f)
    }

    @Test
    fun `one scale serves both directions, so the room keeps its true shape`() {
        val plan = PlanTransform.fit(widthM = 7.0, lengthM = 9.0, canvas = Size(500f, 500f), paddingPx = 0f)
        val corner = plan.toPx(FloorPoint(7.0, 9.0)) - plan.toPx(FloorPoint(0.0, 0.0))
        assertEquals(7f / 9f, corner.x / corner.y, absoluteTolerance = 1e-5f)
    }

    @Test
    fun `a room without size is rejected`() {
        assertFailsWith<IllegalArgumentException> { PlanTransform.fit(0.0, 9.0, Size(100f, 100f), 0f) }
    }
}
