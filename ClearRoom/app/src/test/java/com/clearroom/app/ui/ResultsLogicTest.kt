package com.clearroom.app.ui

import com.clearroom.app.acoustics.ClarityModel
import com.clearroom.app.acoustics.ClarityResult
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.Session
import com.clearroom.app.testing.aRoom
import com.clearroom.app.ui.screens.RowVerdict
import com.clearroom.app.ui.screens.rowVerdict
import com.clearroom.app.ui.screens.worstSeat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The Results tab's plain logic: which seat a row is judged by, and what an evaluation holds. */
class ResultsLogicTest {

    private val clarity = assertIs<ClarityResult.Ready>(ClarityModel.evaluate(SampleData.session)).room

    @Test
    fun `a row is judged by its least clear seat`() {
        val worst = worstSeat(clarity, row = 4)
        assertEquals(4, worst.seat.row)
        assertEquals(clarity.seats.filter { it.seat.row == 4 }.minOf { it.sti }, worst.sti)
    }

    @Test
    fun `a row's verdict is its worst seat's band, range and cause`() {
        val worst = worstSeat(clarity, row = 0)
        assertEquals(RowVerdict(worst.band, worst.stiLow, worst.stiHigh, worst.cause), rowVerdict(clarity, row = 0))
    }

    @Test
    fun `an evaluation of a measured room includes the floor grid`() {
        val evaluation = Evaluation.of(SampleData.session)
        assertIs<ClarityResult.Ready>(evaluation.result)
        assertNotNull(evaluation.grid)
    }

    @Test
    fun `an evaluation of an unmeasured room says what's missing and has no grid`() {
        val evaluation = Evaluation.of(Session(aRoom()))
        assertIs<ClarityResult.NeedsMore>(evaluation.result)
        assertNull(evaluation.grid)
    }

    @Test
    fun `while re-measuring, an evaluation carries the room as it was before the change`() {
        val mine = SampleData.session.copy(room = SampleData.room.copy(id = "mine"))
        val evaluation = Evaluation.of(mine.startRemeasuring(5L))
        assertIs<ClarityResult.NeedsMore>(evaluation.result)
        assertEquals(Evaluation.of(mine), evaluation.before)
        assertNull(Evaluation.of(mine).before)
    }
}
