package com.clearroom.app.ui.screens

import androidx.compose.ui.unit.dp
import com.clearroom.app.acoustics.BandRt60
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MeasureHelpersTest {

    @Test
    fun `band headings read 125 to 8k`() {
        assertEquals(listOf("125", "250", "500", "1k", "2k", "4k", "8k"), listOf(125, 250, 500, 1_000, 2_000, 4_000, 8_000).map(::bandLabel))
    }

    @Test
    fun `readings show as whole dB`() {
        assertEquals("-58", wholeDb(-58.4))
        assertEquals("-58", wholeDb(-57.6))
        assertEquals("0", wholeDb(-0.3))
    }

    @Test
    fun `seat cells fill the width between gaps, capped at 44 dp`() {
        assertEquals(44.dp, seatCellSize(availableWidth = 400.dp, cols = 6, gap = 4.dp))
        assertEquals(10.dp, seatCellSize(availableWidth = 136.dp, cols = 10, gap = 4.dp)) // (136 - 9*4) / 10
    }

    @Test
    fun `echo times show to a tenth of a second, borrowed ones in brackets`() {
        assertEquals("1.2", rt60Cell(BandRt60(1.234, 0.1, 3, estimated = false)).replace(',', '.'))
        assertEquals("(0.9)", rt60Cell(BandRt60(0.88, 0.0, 0, estimated = true)).replace(',', '.'))
    }

    @Test
    fun `a grid needs at least one column`() {
        assertFailsWith<IllegalArgumentException> { seatCellSize(300.dp, cols = 0, gap = 4.dp) }
    }
}
