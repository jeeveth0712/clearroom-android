package com.clearroom.app.viz

import kotlin.test.Test
import kotlin.test.assertEquals

class SceneLabelLayoutTest {
    @Test
    fun `a callout near either edge stays wholly inside the scene`() {
        assertEquals(10f, clampedLabelStart(2f, 120f, 300f, 10f))
        assertEquals(170f, clampedLabelStart(290f, 120f, 300f, 10f))
        assertEquals(90f, clampedLabelStart(150f, 120f, 300f, 10f))
    }

    @Test
    fun `a label wider than the scene starts at the near edge without crashing`() {
        assertEquals(10f, clampedLabelStart(150f, 400f, 300f, 10f))
    }
}
