package com.clearroom.app.acoustics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StiBandTest {

    @Test
    fun `typical values map to their band`() {
        assertEquals(StiBand.POOR, StiBand.from(0.30))
        assertEquals(StiBand.FAIR, StiBand.from(0.52))
        assertEquals(StiBand.GOOD, StiBand.from(0.68))
        assertEquals(StiBand.EXCELLENT, StiBand.from(0.90))
    }

    @Test
    fun `each band edge belongs to the higher band, just below it to the lower one`() {
        val expected = listOf(
            0.0 to StiBand.POOR,
            0.4499 to StiBand.POOR,
            0.45 to StiBand.FAIR,
            0.5999 to StiBand.FAIR,
            0.60 to StiBand.GOOD,
            0.7499 to StiBand.GOOD,
            0.75 to StiBand.EXCELLENT,
            1.0 to StiBand.EXCELLENT,
        )
        expected.forEach { (sti, band) -> assertEquals(band, StiBand.from(sti), "STI=$sti") }
    }

    @Test
    fun `values outside 0 to 1 or not finite are rejected rather than banded`() {
        val invalid = listOf(-0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
        invalid.forEach { sti ->
            assertFailsWith<IllegalArgumentException>("STI=$sti should be rejected") { StiBand.from(sti) }
        }
    }

    @Test
    fun `band never gets worse as STI rises`() {
        val bands = (0..1000).map { StiBand.from(it / 1000.0) }
        assertEquals(bands.sorted(), bands)
    }

    @Test
    fun `lower bounds start at zero and rise in declaration order`() {
        val bounds = StiBand.entries.map { it.lowerBound }
        assertEquals(0.0, bounds.first())
        assertEquals(bounds.sorted(), bounds)
        assertEquals(bounds.size, bounds.distinct().size, "duplicate lower bound in $bounds")
    }
}
