package com.clearroom.app.viz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.clearroom.app.acoustics.Fixes
import com.clearroom.app.acoustics.RankedFix
import com.clearroom.app.acoustics.RowVoice
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.acoustics.VoiceNeed
import com.clearroom.app.ui.theme.color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// --- V6 fix ranking ----------------------------------------------------------------------------------

/** One shared scale for every fix's bar, so bars compare; it always includes 0 and a little room either side. */
fun gainAxis(ranking: List<RankedFix>): Axis {
    val low = min(0.0, ranking.minOfOrNull { it.gainLow } ?: 0.0)
    val high = max(MIN_GAIN_SPAN, ranking.maxOfOrNull { it.gainHigh } ?: 0.0)
    return Axis(low - GAIN_MARGIN, high + GAIN_MARGIN)
}

/** A fix's bar takes the band colour of the score it would reach, so a bar that lifts the back row out of the red isn't red. */
fun fixBarColour(backNowSti: Double, fix: RankedFix): Color =
    if (fix.gain <= 0.0) ChartColors.Noise else StiBand.from((backNowSti + fix.gain).coerceIn(0.0, 1.0)).color()

/**
 * V6: one row per fix, best first (doc "horizontal bars"). Each row has its name with
 * "+0.08 · free" beside it, and its bar on a shared scale from 0 with a whisker for the
 * uncertainty. Gains of zero or less are shown too, not hidden.
 */
@Composable
fun FixRankingChart(ranking: List<RankedFix>, backNowSti: Double, names: List<String>, values: List<String>, modifier: Modifier = Modifier) {
    val axis = gainAxis(ranking)
    val style = ChartStyle.fromTheme()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ranking.forEachIndexed { i, fix ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(names[i], style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(values[i], style = MaterialTheme.typography.labelLarge)
            }
            Canvas(Modifier.fillMaxWidth().height(BAR_ROW_HEIGHT).semantics { contentDescription = "${names[i]}: ${values[i]}" }) {
                drawFixBar(fix, axis, fixBarColour(backNowSti, fix), style)
            }
        }
    }
}

fun DrawScope.drawFixBar(fix: RankedFix, axis: Axis, colour: Color, style: ChartStyle) {
    val px = { gain: Double -> axis.toPx(gain, 0f, size.width) }
    val barHeight = size.height * 0.6f
    val top = (size.height - barHeight) / 2
    val zero = px(0.0)
    val end = px(fix.gain)
    drawRect(colour, Offset(min(zero, end), top), Size(max(abs(end - zero), 2.dp.toPx()), barHeight))
    drawLine(style.axis, Offset(zero, 0f), Offset(zero, size.height), 1.dp.toPx())
    // Whisker: the gain if the echo time is at either end of its measured spread.
    val centre = size.height / 2
    val whisker = 1.5.dp.toPx()
    drawLine(style.axis, Offset(px(fix.gainLow), centre), Offset(px(fix.gainHigh), centre), whisker)
    listOf(fix.gainLow, fix.gainHigh).forEach { drawLine(style.axis, Offset(px(it), centre - barHeight / 3), Offset(px(it), centre + barHeight / 3), whisker) }
}


private val BAR_ROW_HEIGHT = 22.dp
private const val MIN_GAIN_SPAN = 0.1
private const val GAIN_MARGIN = 0.02

// --- V7 required voice -------------------------------------------------------------------------------

/** What the back row's voice need means for the speaker (ISO 9921 steps: raised +6 dB, loud +12 dB). */
enum class VoiceAdvice { NORMAL_IS_ENOUGH, RAISED, LOUD, BEYOND_LOUD, UNREACHABLE }

fun voiceAdvice(need: VoiceNeed): VoiceAdvice = when (need) {
    VoiceNeed.Unreachable -> VoiceAdvice.UNREACHABLE
    is VoiceNeed.Required -> when {
        need.changeDb <= 0.0 -> VoiceAdvice.NORMAL_IS_ENOUGH
        need.changeDb <= Fixes.RAISED_VOICE_DB -> VoiceAdvice.RAISED
        need.changeDb <= Fixes.LOUD_VOICE_DB -> VoiceAdvice.LOUD
        else -> VoiceAdvice.BEYOND_LOUD
    }
}

/** The V7 y range: a quiet voice to well past shouting, in the 6 dB steps of vocal effort. */
val VOICE_AXIS = Axis(-12.0, 30.0)
private val VOICE_TICKS = listOf(-12.0, -6.0, 0.0, 6.0, 12.0, 18.0, 24.0, 30.0)

/** The labels for normal, raised and loud voice, drawn on their reference lines. */
data class VoiceLevelNames(val normal: String, val raised: String, val loud: String)

/**
 * V7: per row, how many dB above the normal voice the speaker needs for the row's worst seat to
 * reach STI 0.60, against normal, raised and loud voice. Rows no voice can reach get a red
 * marker at the top.
 */
@Composable
fun VoiceNeedChart(rows: List<RowVoice>, levels: VoiceLevelNames, xTitle: String, yTitle: String, description: String, modifier: Modifier = Modifier) {
    val text = rememberTextMeasurer()
    val style = ChartStyle.fromTheme()
    Canvas(modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = description }) {
        drawVoiceNeedChart(rows, levels, xTitle, yTitle, style, text)
    }
}

fun DrawScope.drawVoiceNeedChart(rows: List<RowVoice>, levels: VoiceLevelNames, xTitle: String, yTitle: String, style: ChartStyle, text: TextMeasurer) {
    val x = Axis(-0.5, rows.size - 0.5)
    val every = if (rows.size > 10) 2 else 1
    val xTicks = rows.indices.filter { it % every == 0 }.map { Tick(it.toDouble(), (it + 1).toString()) }
    val yTicks = VOICE_TICKS.map { Tick(it, if (it > 0) "+" + decimals(it, 0) else decimals(it, 0)) }
    val area = drawFrame(x, VOICE_AXIS, xTicks, yTicks, xTitle, yTitle, style, text)

    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    listOf(0.0 to levels.normal, Fixes.RAISED_VOICE_DB to levels.raised, Fixes.LOUD_VOICE_DB to levels.loud).forEach { (db, name) ->
        val py = area.py(db)
        drawLine(style.axis, Offset(area.left, py), Offset(area.right, py), 1.dp.toPx(), pathEffect = if (db == 0.0) null else dash)
        val label = text.measure(name, style.label)
        drawText(label, topLeft = Offset(area.left + 4.dp.toPx(), py - label.size.height))
    }

    val points = rows.mapIndexed { i, row ->
        val need = row.need
        if (need is VoiceNeed.Required) Offset(area.px(i.toDouble()), area.py(need.changeDb.coerceIn(VOICE_AXIS.min, VOICE_AXIS.max))) else null
    }
    val line = Path()
    var drawing = false
    points.forEach { point ->
        if (point == null) {
            drawing = false
        } else {
            if (drawing) line.lineTo(point.x, point.y) else line.moveTo(point.x, point.y)
            drawing = true
        }
    }
    drawPath(line, ChartColors.Voice, style = Stroke(2.dp.toPx()))
    val radius = 4.5.dp.toPx()
    points.forEachIndexed { i, point ->
        if (point != null) {
            drawCircle(ChartColors.Voice, radius, point)
        } else {
            // Out of reach: a red triangle pinned to the top edge.
            val tip = Offset(area.px(i.toDouble()), area.top)
            val marker = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(tip.x - radius * 1.3f, tip.y + radius * 2.2f)
                lineTo(tip.x + radius * 1.3f, tip.y + radius * 2.2f)
                close()
            }
            drawPath(marker, StiBand.POOR.color())
        }
    }
}
