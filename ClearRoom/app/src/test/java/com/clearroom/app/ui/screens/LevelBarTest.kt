package com.clearroom.app.ui.screens

import com.clearroom.app.dsp.SILENCE_DB
import kotlin.test.Test
import kotlin.test.assertEquals

class LevelBarTest {

    @Test
    fun `the floor is an empty bar and full scale a full one`() {
        assertEquals(0f, levelBarFraction(LEVEL_BAR_FLOOR_DB))
        assertEquals(1f, levelBarFraction(0.0))
    }

    @Test
    fun `the bar fills linearly in dB between floor and full scale`() {
        assertEquals(0.5f, levelBarFraction(LEVEL_BAR_FLOOR_DB / 2), absoluteTolerance = 1e-6f)
    }

    @Test
    fun `levels outside the range are clamped`() {
        assertEquals(0f, levelBarFraction(SILENCE_DB))
        assertEquals(1f, levelBarFraction(6.0))
    }

    @Test
    fun `a missing reading shows an empty bar`() {
        assertEquals(0f, levelBarFraction(Double.NaN))
    }
}
