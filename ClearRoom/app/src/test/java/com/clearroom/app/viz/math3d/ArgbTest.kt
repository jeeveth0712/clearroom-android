package com.clearroom.app.viz.math3d

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArgbTest {

    private val teal = Argb(0xFF336699.toInt())

    @Test
    fun `channels unpack like Android colour ints`() {
        assertEquals(0xFF, teal.alpha)
        assertEquals(0x33, teal.red)
        assertEquals(0x66, teal.green)
        assertEquals(0x99, teal.blue)
    }

    @Test
    fun `packing and unpacking round-trip`() {
        assertEquals(teal, Argb.of(0xFF, 0x33, 0x66, 0x99))
        assertEquals(0x80, Argb.of(0x80, 1, 2, 3).alpha)
    }

    @Test
    fun `shading darkens red, green and blue but keeps alpha`() {
        val half = Argb.of(0x80, 200, 100, 50).shaded(0.5f)
        assertEquals(Argb.of(0x80, 100, 50, 25), half)
    }

    @Test
    fun `full shade leaves the colour alone and zero shade is black`() {
        assertEquals(teal, teal.shaded(1f))
        assertEquals(Argb.of(0xFF, 0, 0, 0), teal.shaded(0f))
    }

    @Test
    fun `shade outside 0 to 1 is rejected`() {
        assertFailsWith<IllegalArgumentException> { teal.shaded(1.2f) }
        assertFailsWith<IllegalArgumentException> { teal.shaded(-0.1f) }
    }

    @Test
    fun `channels outside 0 to 255 are rejected`() {
        assertFailsWith<IllegalArgumentException> { Argb.of(256, 0, 0, 0) }
        assertFailsWith<IllegalArgumentException> { Argb.of(255, -1, 0, 0) }
    }
}
