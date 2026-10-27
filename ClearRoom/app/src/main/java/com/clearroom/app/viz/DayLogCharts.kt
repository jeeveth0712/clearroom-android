package com.clearroom.app.viz

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.acoustics.ClarityModel
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.daylog.LogStats
import com.clearroom.app.daylog.LogSummary
import com.clearroom.app.daylog.SoundLabel
import com.clearroom.app.ui.theme.color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** "09:05" in the phone's time zone. */
fun clockTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(millis).atZone(zone))

/** "1:05:30" (h:mm:ss): no words, so no plurals to translate. */
fun formatDuration(millis: Long): String {
    val totalS = (millis / 1_000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d:%02d", totalS / 3_600, totalS % 3_600 / 60, totalS % 60)
}

/** Phone dBFS as rough dB SPL (uncalibrated): the numbers the Log tab shows. */
fun approxDba(dbfs: Double): Double = dbfs + LogStats.PHONE_TO_SPL_DB

/** Round clock-time ticks (1, 2, 5, 10, 15, 30 min, 1, 2, 3 h…), at most [maxCount] of them inside [startMillis]..[endMillis]. */
fun timeTicks(startMillis: Long, endMillis: Long, maxCount: Int = 5): List<Long> {
    require(endMillis > startMillis) { "empty time range" }
    val minute = 60_000L
    val steps = listOf(1L, 2, 5, 10, 15, 30, 60, 120, 180, 240, 360).map { it * minute }
    val step = steps.firstOrNull { (endMillis - startMillis) / it <= maxCount } ?: steps.last()
    val first = (startMillis + step - 1) / step * step
    return generateSequence(first) { it + step }.takeWhile { it <= endMillis }.toList()
}

/** Sound labels get their own colours: never clarity-band hues, so a red mark can't be misread as "poor". */
fun SoundLabel.chartColor(): Color = when (this) {
    SoundLabel.FAN -> Color(0xFF1B9E77)
    SoundLabel.TRAFFIC -> Color(0xFF8C510A)
    SoundLabel.HORN -> Color(0xFFC51B7D)
    SoundLabel.TALKING -> ChartColors.Voice
    SoundLabel.MUSIC -> Color(0xFF7F8C1F)
    SoundLabel.QUIET, SoundLabel.OTHER -> ChartColors.Noise
}

/** The label in plain words, for legends and the event list. */
@StringRes
fun SoundLabel.nameRes(): Int = when (this) {
    SoundLabel.FAN -> R.string.sound_fan
    SoundLabel.TRAFFIC -> R.string.sound_traffic
    SoundLabel.HORN -> R.string.sound_horn
    SoundLabel.TALKING -> R.string.sound_talking
    SoundLabel.MUSIC -> R.string.sound_music
    SoundLabel.QUIET -> R.string.sound_quiet
    SoundLabel.OTHER -> R.string.sound_other
}

/** The names V5 needs, passed in so the drawing needs no resources. */
data class TimelineWords(val noiseTitle: String, val clarityTitle: String, val background: String, val notMeasured: String)

/**
 * V5, the day clarity timeline: two panels sharing the clock axis, as the chart skill advises
 * (a dual y axis confuses). Top: noise (≈ dBA) with the background level dashed and the labelled
 * events as coloured marks. Bottom: the back row's STI per period, with easy listening (≥ 0.60)
 * shaded; when the room isn't measured yet, a note instead.
 */
@Composable
fun DayTimelineChart(summary: LogSummary, startMillis: Long, words: TimelineWords, description: String, modifier: Modifier = Modifier) {
    val text = rememberTextMeasurer()
    val style = ChartStyle.fromTheme()
    val labels = remember(summary) { summary.events.map { it.label }.distinct() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(300.dp).semantics { contentDescription = description }) {
            drawDayTimeline(summary, startMillis, words, style, text)
        }
        if (labels.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                labels.forEach { Swatch(it.chartColor(), stringResource(it.nameRes())) }
            }
        }
    }
}

fun DrawScope.drawDayTimeline(summary: LogSummary, startMillis: Long, words: TimelineWords, style: ChartStyle, text: TextMeasurer) {
    val endMillis = startMillis + max(summary.spanMillis, 60_000L)
    val x = Axis(startMillis.toDouble(), endMillis.toDouble())
    val xTicks = timeTicks(startMillis, endMillis).map { Tick(it.toDouble(), clockTime(it)) }

    val levels = summary.noiseLine.map { approxDba(it.dba) } + approxDba(summary.l90Dba)
    val step = Axis.niceStep((levels.max() - levels.min()).coerceAtLeast(10.0), 3)
    val noiseAxis = Axis(floor((levels.min() - 2) / step) * step, ceil((levels.max() + 2) / step) * step)
    val noiseTicks = noiseAxis.ticks(3).map { Tick(it, decimals(it, 0)) }
    val stiAxis = Axis(0.0, 1.0)
    val stiTicks = listOf(0.0, ClarityModel.TARGET_STI, 1.0).map { Tick(it, decimals(it, 1)) }
    val gap = 4.dp.toPx()
    val left = (noiseTicks + stiTicks).maxOf { text.measure(it.label, style.label).size.width } + 2 * gap
    val split = size.height * 0.55f

    inset(left = 0f, top = 0f, right = 0f, bottom = size.height - split) {
        val area = drawFrame(x, noiseAxis, emptyList(), noiseTicks, "", words.noiseTitle, style, text, minLeft = left)
        summary.events.forEach { event ->
            val from = area.px(event.startMillis.toDouble()).coerceIn(area.left, area.right)
            val to = area.px(event.endMillis.toDouble()).coerceIn(area.left, area.right)
            if (to > from) drawRect(event.label.chartColor().copy(alpha = 0.12f), Offset(from, area.top), Size(to - from, area.bottom - area.top))
        }
        // The background level: what's left with the events taken out.
        val background = area.py(approxDba(summary.l90Dba))
        drawLine(style.axis, Offset(area.left, background), Offset(area.right, background), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        val backgroundLabel = text.measure(words.background, style.label)
        drawText(backgroundLabel, topLeft = Offset(area.right - backgroundLabel.size.width, background + 2.dp.toPx()))
        val line = Path()
        summary.noiseLine.forEachIndexed { k, point ->
            val p = Offset(area.px(point.timeMillis.toDouble()), area.py(approxDba(point.dba)))
            if (k == 0) line.moveTo(p.x, p.y) else line.lineTo(p.x, p.y)
        }
        drawPath(line, ChartColors.Noise, style = Stroke(2.dp.toPx()))
        // Events as marks along the top of the panel, at least 3 dp wide so short ones show.
        val markHeight = 6.dp.toPx()
        summary.events.forEach { event ->
            val from = area.px(event.startMillis.toDouble())
            val width = max(area.px(event.endMillis.toDouble()) - from, 3.dp.toPx())
            drawRect(event.label.chartColor(), Offset(from, area.top), Size(width, markHeight))
        }
    }

    inset(left = 0f, top = split, right = 0f, bottom = 0f) {
        val area = drawFrame(x, stiAxis, xTicks, stiTicks, "", words.clarityTitle, style, text, minLeft = left)
        val good = area.py(ClarityModel.TARGET_STI)
        drawRect(StiBand.GOOD.color().copy(alpha = 0.18f), Offset(area.left, area.top), Size(area.right - area.left, good - area.top))
        summary.events.forEach { event ->
            val from = area.px(event.startMillis.toDouble()).coerceIn(area.left, area.right)
            val to = area.px(event.endMillis.toDouble()).coerceIn(area.left, area.right)
            if (to > from) drawRect(event.label.chartColor().copy(alpha = 0.10f), Offset(from, area.top), Size(to - from, area.bottom - area.top))
        }
        if (summary.backRowSti.all { it == null }) {
            val note = text.measure(words.notMeasured, style.label, constraints = Constraints(maxWidth = (area.right - area.left).toInt().coerceAtLeast(1)))
            drawText(note, topLeft = Offset(area.left + gap, (area.top + area.bottom - note.size.height) / 2f))
        } else {
            summary.periods.zip(summary.backRowSti).forEach { (period, sti) ->
                if (sti == null) return@forEach
                val y = area.py(sti)
                val from = area.px(period.startMillis.toDouble())
                val to = area.px(minOf(period.endMillis, endMillis).toDouble())
                drawLine(StiBand.from(sti.coerceIn(0.0, 1.0)).color(), Offset(from, y), Offset(to, y), 4.dp.toPx())
            }
        }
    }
}
