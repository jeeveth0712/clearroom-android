package com.clearroom.app.ui.theme

import com.clearroom.app.acoustics.StiBand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StiColorsTest {

    @Test
    fun `every band has its own colour`() {
        val colours = StiBand.entries.map { it.color() }
        assertEquals(colours.size, colours.distinct().size, "bands share a colour: $colours")
    }

    @Test
    fun `poor is red and excellent is blue, so a red seat means poor everywhere`() {
        val poor = StiBand.POOR.color()
        val excellent = StiBand.EXCELLENT.color()
        assertTrue(poor.red > poor.green && poor.red > poor.blue, "POOR is not red: $poor")
        assertTrue(excellent.blue > excellent.red && excellent.blue > excellent.green, "EXCELLENT is not blue: $excellent")
    }

    @Test
    fun `band colours are fully opaque so they look the same on any background, in 3D and in the PDF`() {
        StiBand.entries.forEach { band -> assertEquals(1f, band.color().alpha, "$band") }
    }
}
