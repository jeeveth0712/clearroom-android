package com.clearroom.app.viz

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/** Octave band names as the charts print them, in band order: 125 Hz on the left. */
val BAND_LABELS = listOf("125", "250", "500", "1k", "2k", "4k", "8k")

/** Octave band names with their unit, for sentences and 3D labels: "1 kHz", not "1k Hz". */
val BAND_NAMES = listOf("125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz")

/** [value] with [places] decimals, in the phone's number style; never "-0.0" for a value that rounds to zero. */
fun decimals(value: Double, places: Int): String {
    val shown = if (abs(value) < 0.5 * 10.0.pow(-places)) 0.0 else value
    return String.format(Locale.getDefault(), "%.${places}f", shown)
}

/** One axis: value → pixel, linear or log10, plus "nice" round tick values (compose-charts-2d skill). */
class Axis(val min: Double, val max: Double, val log: Boolean = false) {
    init {
        require(min.isFinite() && max.isFinite() && max > min) { "an axis needs min < max: $min, $max" }
        require(!log || min > 0.0) { "a log axis needs positive values, min was $min" }
    }

    /** Where [value] sits between min (0) and max (1); values outside the axis give < 0 or > 1. */
    fun fraction(value: Double): Double =
        if (log) (log10(value) - log10(min)) / (log10(max) - log10(min)) else (value - min) / (max - min)

    fun toPx(value: Double, startPx: Float, endPx: Float): Float = (startPx + (endPx - startPx) * fraction(value)).toFloat()

    /** Round values inside the axis in 1-2-5 steps, at most [maxCount] steps apart. Linear axes only. */
    fun ticks(maxCount: Int = 5): List<Double> {
        check(!log) { "log axes name their ticks explicitly" }
        val step = niceStep(max - min, maxCount)
        val first = ceil(min / step - EPSILON) * step
        return generateSequence(first) { it + step }
            .takeWhile { it <= max + step * EPSILON }
            .map { round(it / step) * step + 0.0 } // + 0.0 turns a -0.0 tick into 0.0
            .toList()
    }

    companion object {
        private const val EPSILON = 1e-9

        /** The smallest 1, 2 or 5 × 10ⁿ step that splits [range] into at most [maxCount] parts. */
        fun niceStep(range: Double, maxCount: Int): Double {
            require(range > 0.0 && maxCount >= 1) { "bad range $range or count $maxCount" }
            val raw = range / maxCount
            val magnitude = 10.0.pow(floor(log10(raw)))
            return listOf(1.0, 2.0, 5.0, 10.0).first { it * magnitude >= raw * (1 - EPSILON) } * magnitude
        }

        /** From [min] up to [maxValue] rounded up to the next nice step, so the top tick closes the axis. */
        fun upTo(maxValue: Double, min: Double = 0.0, maxCount: Int = 5): Axis {
            val step = niceStep(maxValue - min, maxCount)
            return Axis(min, ceil(maxValue / step - EPSILON) * step)
        }
    }
}

/** A tick mark: where it goes, and the text under or beside it. */
class Tick(val value: Double, val label: String)

/** Axis, grid and label colours follow the theme; data colours never do (StiColors, ChartColors). */
class ChartStyle(val axis: Color, val grid: Color, val label: TextStyle, val emphasis: Color, val muted: Color) {
    companion object {
        @Composable
        fun fromTheme(): ChartStyle {
            val scheme = MaterialTheme.colorScheme
            return ChartStyle(
                axis = scheme.onSurfaceVariant,
                grid = scheme.outlineVariant,
                label = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant),
                emphasis = scheme.primary,
                muted = scheme.outline,
            )
        }
    }
}

/** The data area of a chart, with its axes: [px] and [py] turn values into pixels. */
class PlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float, val x: Axis, val y: Axis) {
    fun px(value: Double): Float = x.toPx(value, left, right)
    fun py(value: Double): Float = y.toPx(value, bottom, top)
}

/**
 * Draws the grid, tick labels and both axis titles, and returns the area left for the data.
 * Insets come from the measured labels, so long labels never clip on small phones. The y title
 * sits above the plot instead of rotated up the side, which is easier to read on a phone.
 */
fun DrawScope.drawFrame(
    x: Axis,
    y: Axis,
    xTicks: List<Tick>,
    yTicks: List<Tick>,
    xTitle: String,
    yTitle: String,
    style: ChartStyle,
    text: TextMeasurer,
): PlotArea {
    val gap = 4.dp.toPx()
    val yLabels = yTicks.map { text.measure(it.label, style.label) }
    val xLabels = xTicks.map { text.measure(it.label, style.label) }
    val yTitleText = text.measure(yTitle, style.label)
    val xTitleText = text.measure(xTitle, style.label)
    val left = (yLabels.maxOfOrNull { it.size.width } ?: 0) + 2 * gap
    val top = yTitleText.size.height + 2 * gap
    val right = size.width - (xLabels.lastOrNull()?.size?.width ?: 0) / 2f - gap
    val bottom = size.height - (xLabels.maxOfOrNull { it.size.height } ?: 0) - xTitleText.size.height - 2 * gap
    val area = PlotArea(left, top, right, bottom, x, y)
    val hairline = 1.dp.toPx()

    drawText(yTitleText, topLeft = Offset(0f, 0f))
    yTicks.zip(yLabels).forEach { (tick, label) ->
        val py = area.py(tick.value)
        drawLine(style.grid, Offset(left, py), Offset(right, py), hairline)
        drawText(label, topLeft = Offset(left - gap - label.size.width, py - label.size.height / 2f))
    }
    xTicks.zip(xLabels).forEach { (tick, label) ->
        val px = area.px(tick.value)
        drawLine(style.axis, Offset(px, bottom), Offset(px, bottom + gap), hairline)
        drawText(label, topLeft = Offset(px - label.size.width / 2f, bottom + gap))
    }
    drawLine(style.axis, Offset(left, bottom), Offset(right, bottom), hairline)
    drawText(xTitleText, topLeft = Offset((left + right - xTitleText.size.width) / 2f, size.height - xTitleText.size.height))
    return area
}

/** A dashed reference line across the plot (a target, a threshold); its name goes in the legend. */
fun DrawScope.drawReferenceLine(area: PlotArea, value: Double, colour: Color) {
    val py = area.py(value)
    drawLine(colour, Offset(area.left, py), Offset(area.right, py), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx())))
}

/** Ticks at whole band positions 0–6, named by [BAND_LABELS]. */
val BAND_TICKS: List<Tick> = BAND_LABELS.mapIndexed { band, label -> Tick(band.toDouble(), label) }

/** A band axis with half a band of room either side, so bars centred on 0 and 6 fit. */
val BAND_AXIS = Axis(-0.5, BAND_LABELS.size - 0.5)

/** Fixed data colours for charts that aren't about clarity bands, so they never look like one. */
object ChartColors {
    val Voice = Color(0xFF6A51A3)
    val Noise = Color(0xFF8C8C8C)

    /** The part of an echo-time bar above the target: amber, a hue no clarity band uses. */
    val TooLong = Color(0xFFE3A21A)

    /** One per octave band, dark to light (viridis): ordered pitches get an ordered ramp. */
    val Bands = listOf(
        Color(0xFF440154), Color(0xFF443983), Color(0xFF31688E), Color(0xFF21918C),
        Color(0xFF35B779), Color(0xFF90D743), Color(0xFFFDE725),
    )
}
