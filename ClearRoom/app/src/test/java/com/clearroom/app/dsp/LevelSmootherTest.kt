package com.clearroom.app.dsp

import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LevelSmootherTest {

    @Test
    fun `the first reading passes straight through`() {
        assertEquals(-42.0, LevelSmoother().next(-42.0, stepS = 0.05))
    }

    @Test
    fun `a steady level stays steady`() {
        val smoother = LevelSmoother()
        repeat(50) { assertEquals(-30.0, smoother.next(-30.0, stepS = 0.05), absoluteTolerance = 1e-12) }
    }

    @Test
    fun `after one time constant a step change has covered about 63 percent`() {
        val smoother = LevelSmoother(timeConstantS = 0.1)
        smoother.next(-60.0, stepS = 0.01)
        var level = 0.0
        repeat(10) { level = smoother.next(0.0, stepS = 0.01) } // 10 x 0.01 s = one time constant
        val expected = -60.0 * exp(-1.0) // the remaining gap shrinks to e^-1
        assertEquals(expected, level, absoluteTolerance = 1e-9)
    }

    @Test
    fun `smoothing depends on elapsed time, not on how the time is chunked`() {
        val oneBigStep = LevelSmoother().apply { next(-80.0, 0.05) }.next(-20.0, stepS = 0.1)
        val tenSmallSteps = LevelSmoother().apply { next(-80.0, 0.05) }.let { s ->
            var level = 0.0
            repeat(10) { level = s.next(-20.0, stepS = 0.01) }
            level
        }
        assertEquals(oneBigStep, tenSmallSteps, absoluteTolerance = 1e-9)
    }

    @Test
    fun `non-positive time steps and time constants are rejected`() {
        assertFailsWith<IllegalArgumentException> { LevelSmoother().next(-40.0, stepS = 0.0) }
        assertFailsWith<IllegalArgumentException> { LevelSmoother().next(-40.0, stepS = -0.05) }
        assertFailsWith<IllegalArgumentException> { LevelSmoother(timeConstantS = 0.0) }
    }
}
