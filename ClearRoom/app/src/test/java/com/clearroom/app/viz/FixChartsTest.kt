package com.clearroom.app.viz

import com.clearroom.app.acoustics.ClarityModel
import com.clearroom.app.acoustics.ClarityResult
import com.clearroom.app.acoustics.FixPreset
import com.clearroom.app.acoustics.FloorPoint
import com.clearroom.app.acoustics.RankedFix
import com.clearroom.app.acoustics.SeatLayout
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.acoustics.VoiceNeed
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SeatId
import com.clearroom.app.ui.screens.VoiceEffort
import com.clearroom.app.ui.screens.speechEchoS
import com.clearroom.app.ui.screens.voiceEffort
import com.clearroom.app.acoustics.FixSettings
import com.clearroom.app.acoustics.Fixes
import com.clearroom.app.viz.math3d.Argb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** V6 fix ranking, V7 voice needed, V12 morph bars and the Fixes tab's small helpers. */
class FixChartsTest {

    private fun fix(gain: Double, low: Double = gain, high: Double = gain) = RankedFix(FixPreset.CURTAINS, gain, low, high)

    // --- V6 ---------------------------------------------------------------------------------------

    @Test
    fun `the improvement scale contains today's score and every result range`() {
        val axis = improvementAxis(listOf(fix(0.07, 0.06, 0.08), fix(-0.01, -0.03, 0.0)), 0.57)
        assertTrue(axis.min < 0.54 && axis.max > 0.65)
        assertTrue(axis.fraction(0.57) in 0.0..1.0)
    }

    @Test
    fun `tiny gains still get a readable scale`() {
        val axis = improvementAxis(listOf(fix(0.0)), 0.57)
        assertTrue(axis.max - axis.min >= 0.15)
    }

    @Test
    fun `score scale stays within physical 0 to 1 bounds`() {
        assertEquals(0.0, scoreAxis(-0.2, 0.02, 0.15).min)
        assertEquals(1.0, scoreAxis(0.98, 1.2, 0.15).max)
    }

    // --- V7 ---------------------------------------------------------------------------------------

    @Test
    fun `voice advice follows the vocal effort steps`() {
        assertEquals(VoiceAdvice.NORMAL_IS_ENOUGH, voiceAdvice(VoiceNeed.Required(-3.0)))
        assertEquals(VoiceAdvice.NORMAL_IS_ENOUGH, voiceAdvice(VoiceNeed.Required(0.0)))
        assertEquals(VoiceAdvice.RAISED, voiceAdvice(VoiceNeed.Required(4.0)))
        assertEquals(VoiceAdvice.LOUD, voiceAdvice(VoiceNeed.Required(12.0)))
        assertEquals(VoiceAdvice.BEYOND_LOUD, voiceAdvice(VoiceNeed.Required(15.0)))
        assertEquals(VoiceAdvice.UNREACHABLE, voiceAdvice(VoiceNeed.Unreachable))
    }

    @Test
    fun `the voice slider reads as quieter, normal, raised or loud`() {
        assertEquals(VoiceEffort.QUIETER, voiceEffort(-6.0))
        assertEquals(VoiceEffort.NORMAL, voiceEffort(0.0))
        assertEquals(VoiceEffort.NORMAL, voiceEffort(2.0))
        assertEquals(VoiceEffort.RAISED, voiceEffort(6.0))
        assertEquals(VoiceEffort.LOUD, voiceEffort(12.0))
    }

    // --- Summary and V12 ----------------------------------------------------------------------------

    private val clarity = (ClarityModel.evaluate(SampleData.session) as ClarityResult.Ready).room

    @Test
    fun `row comparison uses each row's worst seat in both states`() {
        val after = (ClarityModel.evaluate(SampleData.session, FixSettings(curtainM2 = 20.0).toWhatIf(SampleData.room)) as ClarityResult.Ready).room
        val rows = rowComparison(clarity, after)
        assertEquals(clarity.rows, rows.size)
        assertEquals(clarity.worstSeat(0), rows.first().now)
        assertEquals(after.worstSeat(after.rows - 1), rows.last().after)
    }

    @Test
    fun `voice rows retain one point per row and an axis around their levels`() {
        val outcome = requireNotNull(Fixes.outcome(SampleData.session, FixSettings()))
        val rows = voiceRows(outcome, 0.0)
        assertEquals(clarity.rows, rows.size)
        val axis = voiceAxis(rows)
        assertTrue(rows.all { it.nowDba in axis.min..axis.max })
        assertTrue(rows.mapNotNull { it.neededDba }.all { it in axis.min..axis.max })
    }

    @Test
    fun `the echo time people quote is the speech bands' mean`() {
        assertEquals((clarity.rt60S[2] + clarity.rt60S[3] + clarity.rt60S[4]) / 3, speechEchoS(clarity), 1e-12)
    }

    @Test
    fun `mid-morph bars use their animated score for height and colour`() {
        val colours = RoomSceneColours(Argb(1), Argb(2), Argb(3), Argb(4))
        val bandColours = StiBand.entries.associateWith { Argb(0xFF000000.toInt() or (10 + it.ordinal)) }
        val room = SampleData.room
        val bars = listOf(SeatBar(SeatId(0, 0), 0.62), SeatBar(SeatId(4, 5), 1.3)) // out-of-range values are clamped
        val scene = ClarityRoomScene.build(room, bars, FloorPoint(3.5, 3.0), colours, RoomSceneLabels("Speaker", "Back"), bandColours::getValue)
        val (first, second) = scene.solids
        assertEquals(ClarityRoomScene.barHeightM(0.62, room.heightM), first.faces.flatMap { it.points }.maxOf { it.y }, 1e-12)
        assertEquals(bandColours.getValue(StiBand.GOOD), first.faces.first().color)
        assertEquals(ClarityRoomScene.barHeightM(1.0, room.heightM), second.faces.flatMap { it.points }.maxOf { it.y }, 1e-12)
        val cone = scene.solids.last().faces.flatMap { it.points }
        assertEquals(3.0, (cone.minOf { it.z } + cone.maxOf { it.z }) / 2, 1e-9) // the cone stands where the speaker was moved
        assertTrue(cone.maxOf { it.z } > SeatLayout.SPEAKER_FROM_FRONT_M + 1)
    }
}
