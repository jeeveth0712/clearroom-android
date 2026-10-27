package com.clearroom.app.viz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clearroom.app.acoustics.ClarityModel
import com.clearroom.app.acoustics.CurtainCheck
import com.clearroom.app.acoustics.Fixes
import com.clearroom.app.acoustics.FixesOutcome
import com.clearroom.app.acoustics.RankedFix
import com.clearroom.app.acoustics.RoomClarity
import com.clearroom.app.acoustics.SeatClarity
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.acoustics.VoiceNeed
import com.clearroom.app.dsp.OctaveBank
import com.clearroom.app.ui.theme.ClearRoomTheme
import com.clearroom.app.ui.theme.color
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// The Fixes tab's charts in the owner's concept 05 style: a label column on the left, dots on a
// shared scale in the middle, the number on the right. Each row is its own small canvas, and the
// rows share one axis, so the dashed reference lines run straight down the chart.

// --- V6 improvement by change ------------------------------------------------------------------------

/**
 * The scale under V6's dots: back-row clarity from a little below the lowest likely result to a
 * little above the highest, at least 0.15 wide, never beyond 0–1, on 0.05 steps.
 */
fun improvementAxis(ranking: List<RankedFix>, nowSti: Double): Axis {
    val low = min(nowSti, ranking.minOfOrNull { nowSti + it.gainLow } ?: nowSti) - SCORE_MARGIN
    val high = max(nowSti, ranking.maxOfOrNull { nowSti + it.gainHigh } ?: nowSti) + SCORE_MARGIN
    return scoreAxis(low, high, MIN_SCORE_SPAN)
}

/** A 0–1 score axis from [low] to [high], widened to [minSpan] and snapped outwards to 0.05. */
internal fun scoreAxis(low: Double, high: Double, minSpan: Double): Axis {
    val widen = max(0.0, minSpan - (high - low)) / 2
    var from = floor((low - widen) / SCORE_STEP + 1e-9) * SCORE_STEP
    var to = ceil((high + widen) / SCORE_STEP - 1e-9) * SCORE_STEP
    if (from < 0.0) to -= from.also { from = 0.0 }
    if (to > 1.0) from = max(0.0, from - (to - 1.0)).also { to = 1.0 }
    return Axis(from, to)
}

/** One V6 row's words, passed in so the chart needs no resources. */
data class FixRowWords(val name: String, val cost: String, val gain: String)

/**
 * V6 (doc: "predicted back-row STI gain per fix, labelled with cost"), as concept 05 draws it:
 * per fix, a dot at the back-row clarity it would reach on its own, with whiskers for the likely
 * range (NFR-10), against a dashed line at today's score; its cost beside the name and its gain
 * at the right. Best first.
 */
@Composable
fun FixImprovementChart(ranking: List<RankedFix>, nowSti: Double, words: List<FixRowWords>, currentLabel: String, axisTitle: String, modifier: Modifier = Modifier) {
    val axis = improvementAxis(ranking, nowSti)
    val style = ChartStyle.fromTheme()
    val text = rememberTextMeasurer()
    val dot = ClearRoomTheme.brand.headline
    Column(modifier.fillMaxWidth()) {
        ChartRow(FIX_NAME_WIDTH, FIX_VALUE_WIDTH, start = {}, end = {}) {
            Canvas(Modifier.fillMaxWidth().height(LABEL_ROW_HEIGHT)) { drawCentredLabel(currentLabel, axis.toPx(nowSti, 0f, size.width), style.label, text) }
        }
        ranking.forEachIndexed { i, fix ->
            val word = words[i]
            ChartRow(
                FIX_NAME_WIDTH,
                FIX_VALUE_WIDTH,
                modifier = Modifier.heightIn(min = FIX_ROW_HEIGHT).semantics(mergeDescendants = true) { contentDescription = "${word.name}: ${word.gain}, ${word.cost}" },
                start = {
                    Text(word.name, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(2.dp))
                    CostTag(word.cost)
                },
                end = { Text(word.gain, style = MaterialTheme.typography.titleSmall, color = if (fix.gain > 0.0) dot else style.muted, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
            ) {
                Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                    val px = { sti: Double -> axis.toPx(sti, 0f, size.width) }
                    drawDashedVertical(px(nowSti), style.axis)
                    val y = size.height / 2
                    drawWhisker(px(nowSti + fix.gainLow), px(nowSti + fix.gainHigh), y, dot.copy(alpha = 0.7f))
                    drawCircle(dot, DOT_RADIUS.toPx(), Offset(px(nowSti + fix.gain), y))
                }
            }
        }
        ScoreAxisRow(axis, FIX_NAME_WIDTH, FIX_VALUE_WIDTH, style, text)
        Text(axisTitle, style = style.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CostTag(cost: String) {
    Text(
        cost,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

// --- Before and after, by row ------------------------------------------------------------------------

/** One row before and after the chosen changes: its worst seat now and with the changes (a row is judged by its worst seat). */
data class RowComparison(val row: Int, val now: SeatClarity, val after: SeatClarity)

fun rowComparison(now: RoomClarity, after: RoomClarity): List<RowComparison> =
    (0 until min(now.rows, after.rows)).map { RowComparison(it, now.worstSeat(it), after.worstSeat(it)) }

/**
 * Before and after by row (concept 05): per row, its worst seat's clarity now (grey) and with the
 * chosen changes (lime by night, ink by day), each with its range (NFR-10), on a 0–1 scale with
 * the 0.60 target dashed.
 */
@Composable
fun RowCompareChart(rows: List<RowComparison>, rowNames: List<String>, values: List<String>, modifier: Modifier = Modifier) {
    val axis = Axis(0.0, 1.0)
    val style = ChartStyle.fromTheme()
    val text = rememberTextMeasurer()
    val after = ClearRoomTheme.brand.headline
    val before = ChartColors.Noise
    Column(modifier.fillMaxWidth()) {
        ScoreAxisRow(axis, ROW_NAME_WIDTH, ROW_VALUE_WIDTH, style, text, ticksOnTop = true)
        rows.forEachIndexed { i, row ->
            ChartRow(
                ROW_NAME_WIDTH,
                ROW_VALUE_WIDTH,
                modifier = Modifier.heightIn(min = COMPARE_ROW_HEIGHT).semantics(mergeDescendants = true) { contentDescription = "${rowNames[i]}: ${values[i]}" },
                start = { Text(rowNames[i], style = MaterialTheme.typography.labelLarge) },
                end = { Text(values[i], style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
            ) {
                Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                    val px = { sti: Double -> axis.toPx(sti.coerceIn(0.0, 1.0), 0f, size.width) }
                    axis.ticks(5).forEach { drawLine(style.grid, Offset(px(it), 0f), Offset(px(it), size.height), 1.dp.toPx()) }
                    drawDashedVertical(px(ClarityModel.TARGET_STI), style.axis)
                    val y = size.height / 2
                    drawLine(style.muted, Offset(px(row.now.sti), y), Offset(px(row.after.sti), y), 2.dp.toPx())
                    drawWhisker(px(row.now.stiLow), px(row.now.stiHigh), y, before)
                    drawWhisker(px(row.after.stiLow), px(row.after.stiHigh), y, after.copy(alpha = 0.7f))
                    drawCircle(before, DOT_RADIUS.toPx(), Offset(px(row.now.sti), y))
                    drawCircle(after, DOT_RADIUS.toPx(), Offset(px(row.after.sti), y))
                }
            }
        }
    }
}

// --- V7 voice needed by row --------------------------------------------------------------------------

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

/**
 * One row of V7: the speaker's normal voice as it arrives at the row's worst seat, and the level
 * that seat needs for STI 0.60 (null when no voice reaches it), both about dBA.
 */
data class VoiceRow(val row: Int, val nowDba: Double, val neededDba: Double?, val advice: VoiceAdvice)

/**
 * V7's rows from the Fixes outcome. [FixesOutcome.after] includes the voice slider, so its
 * [voiceChangeDb] is taken off again: V7 asks how loud the speaker must be, starting from their normal voice.
 */
fun voiceRows(outcome: FixesOutcome, voiceChangeDb: Double): List<VoiceRow> = outcome.voice.map { row ->
    val now = approxDba(OctaveBank.aWeightedDb(outcome.after.worstSeat(row.row).speechDb)) - voiceChangeDb
    val need = row.need
    VoiceRow(row.row, now, (need as? VoiceNeed.Required)?.let { now + it.changeDb }, voiceAdvice(need))
}

/** V7's scale: every level shown, with 5 dB to spare, on 10 dB steps and at least 30 dB wide. */
fun voiceAxis(rows: List<VoiceRow>): Axis {
    val levels = rows.flatMap { listOfNotNull(it.nowDba, it.neededDba) }
    val low = (levels.minOrNull() ?: 50.0) - VOICE_MARGIN_DB
    val high = (levels.maxOrNull() ?: 60.0) + VOICE_MARGIN_DB
    val widen = max(0.0, MIN_VOICE_SPAN_DB - (high - low)) / 2
    return Axis(floor((low - widen) / VOICE_STEP_DB) * VOICE_STEP_DB, ceil((high + widen) / VOICE_STEP_DB) * VOICE_STEP_DB)
}

/** The effort word and its explanation for each row's [VoiceAdvice], passed in so the chart needs no resources. */
data class VoiceWords(val effort: String, val detail: String)

/**
 * V7 (doc: the voice needed for STI 0.60 against normal, raised and loud) as concept 05 draws it:
 * per row, the voice arriving now (grey) and the level needed (lime by night, ink by day), with
 * the effort that takes on the right. A row no voice can reach gets a red cross: the echo caps it.
 */
@Composable
fun VoiceRowsChart(rows: List<VoiceRow>, rowNames: List<String>, words: List<VoiceWords>, axisTitle: String, modifier: Modifier = Modifier) {
    val axis = voiceAxis(rows)
    val style = ChartStyle.fromTheme()
    val text = rememberTextMeasurer()
    val needed = ClearRoomTheme.brand.headline
    val poor = StiBand.POOR.color()
    Column(modifier.fillMaxWidth()) {
        rows.forEachIndexed { i, row ->
            val unreachable = row.neededDba == null
            ChartRow(
                ROW_NAME_WIDTH,
                VOICE_VALUE_WIDTH,
                modifier = Modifier.heightIn(min = VOICE_ROW_HEIGHT).semantics(mergeDescendants = true) {
                    contentDescription = "${rowNames[i]}: ${words[i].effort}, ${words[i].detail}"
                },
                start = { Text(rowNames[i], style = MaterialTheme.typography.labelLarge) },
                end = {
                    Text(words[i].effort, style = MaterialTheme.typography.labelLarge, color = if (unreachable) poor else MaterialTheme.colorScheme.onSurface)
                    Text(words[i].detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            ) {
                Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                    val px = { db: Double -> axis.toPx(db.coerceIn(axis.min, axis.max), 0f, size.width) }
                    axis.ticks(5).forEach { drawLine(style.grid, Offset(px(it), 0f), Offset(px(it), size.height), 1.dp.toPx()) }
                    val y = size.height * 0.62f
                    val from = Offset(px(row.nowDba), y)
                    val target = row.neededDba
                    if (target == null) {
                        val end = Offset(size.width - CROSS_SIZE.toPx(), y)
                        drawLine(style.muted, from, end, 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                        drawCross(end, poor)
                    } else {
                        val to = Offset(px(target), y)
                        drawLine(style.muted, from, to, 3.dp.toPx())
                        drawCircle(needed, DOT_RADIUS.toPx(), to)
                        drawValueAbove(decimals(target, 0), to, style.label.copy(color = needed, fontWeight = FontWeight.Bold), text)
                    }
                    drawCircle(ChartColors.Noise, DOT_RADIUS.toPx(), from)
                    drawValueAbove(decimals(row.nowDba, 0), from, style.label, text)
                }
            }
        }
        ScoreAxisRow(axis, ROW_NAME_WIDTH, VOICE_VALUE_WIDTH, style, text, decimalsShown = 0)
        Text(axisTitle, style = style.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

// --- What-if realism (field test) -------------------------------------------------------------------

/** The prediction chart's words, passed in so the chart needs no resources. */
data class PredictionWords(val before: String, val predicted: String, val measured: String, val passZone: String, val axisTitle: String)

/**
 * The curtain test as concept 05 draws it: on one echo-time line, "before" (grey), the Fixes tab's
 * prediction (a ring), the zone a measured "after" must land in to pass (shaded), and the measured
 * "after" (lime by night, ink by day).
 */
@Composable
fun PredictionChart(check: CurtainCheck, words: PredictionWords, modifier: Modifier = Modifier) {
    val style = ChartStyle.fromTheme()
    val text = rememberTextMeasurer()
    val measured = ClearRoomTheme.brand.headline
    val zone = check.passZoneS
    val axis = Axis.upTo(max(check.beforeS, max(check.predictedS, check.measuredS)) * 1.1, maxCount = 4)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(PREDICTION_HEIGHT)) {
            val ticks = axis.ticks(4)
            val labels = ticks.map { text.measure(decimals(it, 1), style.label) }
            val bottom = size.height - (labels.maxOfOrNull { it.size.height } ?: 0) - 4.dp.toPx()
            val inset = 12.dp.toPx()
            val px = { s: Double -> axis.toPx(s, inset, size.width - inset) }
            val y = bottom * 0.62f
            zone?.let { drawRect(measured.copy(alpha = 0.22f), Offset(px(it.start), y - 14.dp.toPx()), Size(px(it.endInclusive) - px(it.start), 28.dp.toPx())) }
            drawLine(style.axis, Offset(inset, bottom), Offset(size.width - inset, bottom), 1.dp.toPx())
            ticks.zip(labels).forEach { (tick, label) ->
                drawLine(style.axis, Offset(px(tick), bottom), Offset(px(tick), bottom + 3.dp.toPx()), 1.dp.toPx())
                drawText(label, topLeft = Offset(px(tick) - label.size.width / 2f, bottom + 4.dp.toPx()))
            }
            drawLine(style.muted, Offset(px(check.beforeS), y), Offset(px(check.measuredS), y), 2.dp.toPx())
            drawCircle(ChartColors.Noise, DOT_RADIUS.toPx(), Offset(px(check.beforeS), y))
            drawCircle(style.axis, DOT_RADIUS.toPx(), Offset(px(check.predictedS), y), style = Stroke(2.dp.toPx()))
            drawCircle(measured, DOT_RADIUS.toPx(), Offset(px(check.measuredS), y))
            drawValueAbove(decimals(check.beforeS, 2), Offset(px(check.beforeS), y), style.label, text)
            drawValueAbove(decimals(check.measuredS, 2), Offset(px(check.measuredS), y), style.label.copy(color = measured, fontWeight = FontWeight.Bold), text)
        }
        Text(words.axisTitle, style = style.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Swatch(ChartColors.Noise, words.before)
            Swatch(style.axis, words.predicted)
            Swatch(measured, words.measured)
            if (zone != null) Swatch(measured.copy(alpha = 0.22f), words.passZone)
        }
    }
}

// --- Shared pieces -----------------------------------------------------------------------------------

/** A chart row: [start] in a fixed-width column, the plot filling the middle, [end] in a fixed-width column. */
@Composable
private fun ChartRow(
    startWidth: Dp,
    endWidth: Dp,
    modifier: Modifier = Modifier,
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
    plot: @Composable () -> Unit,
) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(startWidth).padding(end = 8.dp)) { start() }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) { plot() }
        Column(Modifier.width(endWidth).padding(start = 8.dp)) { end() }
    }
}

/** The scale under (or over) a row chart: tick marks and numbers lined up with the rows' plots. */
@Composable
private fun ScoreAxisRow(axis: Axis, startWidth: Dp, endWidth: Dp, style: ChartStyle, text: TextMeasurer, ticksOnTop: Boolean = false, decimalsShown: Int = 2) {
    ChartRow(startWidth, endWidth, start = {}, end = {}) {
        Canvas(Modifier.fillMaxWidth().height(AXIS_ROW_HEIGHT)) {
            val lineY = if (ticksOnTop) size.height else 0f
            drawLine(style.axis, Offset(0f, lineY), Offset(size.width, lineY), 1.dp.toPx())
            axis.ticks(5).forEach { tick ->
                val x = axis.toPx(tick, 0f, size.width)
                val label = text.measure(decimals(tick, if (decimalsShown == 2 && tick * 10 % 1.0 == 0.0) 1 else decimalsShown), style.label)
                val tickEnd = if (ticksOnTop) lineY - 3.dp.toPx() else lineY + 3.dp.toPx()
                drawLine(style.axis, Offset(x, lineY), Offset(x, tickEnd), 1.dp.toPx())
                val top = if (ticksOnTop) tickEnd - label.size.height else tickEnd
                val left = (x - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
                drawText(label, topLeft = Offset(left, top))
            }
        }
    }
}

private fun DrawScope.drawDashedVertical(x: Float, colour: Color) =
    drawLine(colour, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))

private fun DrawScope.drawWhisker(from: Float, to: Float, y: Float, colour: Color) {
    val (left, right) = min(from, to) to max(from, to)
    val stroke = 1.5.dp.toPx()
    val cap = 5.dp.toPx()
    drawLine(colour, Offset(left, y), Offset(right, y), stroke)
    drawLine(colour, Offset(left, y - cap), Offset(left, y + cap), stroke)
    drawLine(colour, Offset(right, y - cap), Offset(right, y + cap), stroke)
}

private fun DrawScope.drawCross(centre: Offset, colour: Color) {
    val half = CROSS_SIZE.toPx() / 2
    val stroke = 3.dp.toPx()
    drawLine(colour, centre + Offset(-half, -half), centre + Offset(half, half), stroke)
    drawLine(colour, centre + Offset(-half, half), centre + Offset(half, -half), stroke)
}

private fun DrawScope.drawValueAbove(value: String, at: Offset, style: TextStyle, text: TextMeasurer) {
    val label = text.measure(value, style)
    val left = (at.x - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
    drawText(label, topLeft = Offset(left, at.y - DOT_RADIUS.toPx() - 2.dp.toPx() - label.size.height))
}

private fun DrawScope.drawCentredLabel(value: String, x: Float, style: TextStyle, text: TextMeasurer) {
    val label = text.measure(value, style)
    drawText(label, topLeft = Offset((x - label.size.width / 2f).coerceIn(0f, size.width - label.size.width), (size.height - label.size.height) / 2f))
}

private const val SCORE_STEP = 0.05
private const val SCORE_MARGIN = 0.02
private const val MIN_SCORE_SPAN = 0.15
private const val VOICE_STEP_DB = 10.0
private const val VOICE_MARGIN_DB = 5.0
private const val MIN_VOICE_SPAN_DB = 30.0
private val FIX_NAME_WIDTH = 112.dp
private val FIX_VALUE_WIDTH = 56.dp
private val ROW_NAME_WIDTH = 84.dp
private val ROW_VALUE_WIDTH = 92.dp
private val VOICE_VALUE_WIDTH = 104.dp
private val FIX_ROW_HEIGHT = 52.dp
private val COMPARE_ROW_HEIGHT = 36.dp
private val VOICE_ROW_HEIGHT = 48.dp
private val LABEL_ROW_HEIGHT = 18.dp
private val AXIS_ROW_HEIGHT = 22.dp
private val PREDICTION_HEIGHT = 96.dp
private val DOT_RADIUS = 6.dp
private val CROSS_SIZE = 12.dp
