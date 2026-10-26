package com.clearroom.app.dsp

import kotlin.test.Test
import kotlin.test.assertEquals

class OctaveBandsTest {

    @Test
    fun `the seven doc bands, lowest first`() {
        assertEquals(listOf(125, 250, 500, 1_000, 2_000, 4_000, 8_000), OctaveBands.CENTRES_HZ)
        assertEquals(7, OctaveBands.COUNT)
    }
}
