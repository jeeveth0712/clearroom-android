package com.clearroom.app.viz

import android.content.res.Resources
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.acoustics.BandRt60
import com.clearroom.app.acoustics.Rt60Summary
import com.clearroom.app.data.Session

/** Which pitches ring longest, which tells which absorber helps most (doc V4 "decision"). */
enum class EchoHint { LOW_PITCHES, MID_PITCHES, HIGH_PITCHES }

/** V4: the room's echo time per octave band (all claps at all seats) against the classroom target. */
data class Rt60ChartModel(val bands: List<BandRt60>, val targetS: Double, val speechRt60S: Double) {
    val overTarget: List<Boolean> get() = bands.map { it.meanS > targetS }

    val speechOverTarget: Boolean get() = speechRt60S > targetS

    /** Where the longest of the too-long bands is; null when nothing rings too long. */
    val hint: EchoHint?
        get() {
            val longest = bands.indices.filter { overTarget[it] }.maxByOrNull { bands[it].meanS } ?: return null
            return when (longest) {
                0, 1 -> EchoHint.LOW_PITCHES
                2, 3, 4 -> EchoHint.MID_PITCHES
                else -> EchoHint.HIGH_PITCHES
            }
        }

    val anyEstimated: Boolean get() = bands.any { it.estimated }

    /** The top of the echo-time axis: room above the target and the tallest whisker. */
    val axisTopS: Double get() = maxOf(targetS * 1.25, bands.maxOf { it.meanS + it.spreadS } * 1.05)

    companion object {
        /** Null until a clap has been accepted somewhere in the room. */
        fun of(session: Session): Rt60ChartModel? =
            Rt60Summary.of(session.seats.flatMap { it.rtTakes })?.let { Rt60ChartModel(it.bands, Rt60Summary.CLASSROOM_TARGET_S, it.speechRt60S) }
    }
}

@Composable
fun Rt60Chart(model: Rt60ChartModel, modifier: Modifier = Modifier) {
    val style = ChartStyle.fromTheme()
    Column(modifier) {
        Rt60Bars(model, model.axisTopS, Modifier.fillMaxWidth().height(220.dp))
        Rt60Legend(model, style = style)
        Text(rt60Caption(LocalResources.current, model), style = MaterialTheme.typography.bodySmall)
    }
}

/** The V4 chart alone, on [axisTopS]: two of them on one scale compare before and after (FR-13). */
@Composable
fun Rt60Bars(model: Rt60ChartModel, axisTopS: Double, modifier: Modifier = Modifier) {
    val text = rememberTextMeasurer()
    val style = ChartStyle.fromTheme()
    val xTitle = stringResource(R.string.chart_pitch_hz)
    val yTitle = stringResource(R.string.chart_echo_time_s)
    val caption = rt60Caption(LocalResources.current, model)
    Canvas(modifier.semantics { contentDescription = caption }) {
        drawRt60Chart(model, xTitle, yTitle, style, text, axisTopS)
    }
}

/** The target line and, if any bar in [models] is over it, the amber "too long" part. One legend can serve a before/after pair. */
@Composable
fun Rt60Legend(vararg models: Rt60ChartModel, style: ChartStyle = ChartStyle.fromTheme()) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Swatch(style.axis, stringResource(R.string.results_rt_target, decimals(models.first().targetS, 1)))
        if (models.any { model -> model.overTarget.any { it } }) Swatch(ChartColors.TooLong, stringResource(R.string.results_rt_too_long))
    }
}

/** What V4 shows, in words: the echo at speech pitches against the target, and which absorber helps. */
fun rt60Caption(resources: Resources, model: Rt60ChartModel): String = buildString {
    val speech = decimals(model.speechRt60S, 1)
    val targetText = decimals(model.targetS, 1)
    append(resources.getString(if (model.speechOverTarget) R.string.results_rt_caption_over else R.string.results_rt_caption_ok, speech, targetText))
    model.hint?.let { append(' ').append(resources.getString(it.textRes())) }
    if (model.anyEstimated) append(' ').append(resources.getString(R.string.results_rt_estimated))
}

private fun EchoHint.textRes(): Int = when (this) {
    EchoHint.LOW_PITCHES -> R.string.results_rt_hint_low
    EchoHint.MID_PITCHES -> R.string.results_rt_hint_mid
    EchoHint.HIGH_PITCHES -> R.string.results_rt_hint_high
}

/** [axisTopS] lets two charts share one scale (before and after, FR-13), so equal bars look equal. */
fun DrawScope.drawRt60Chart(
    model: Rt60ChartModel,
    xTitle: String,
    yTitle: String,
    style: ChartStyle,
    text: TextMeasurer,
    axisTopS: Double = model.axisTopS,
) {
    val y = Axis.upTo(axisTopS, maxCount = 4)
    val area = drawFrame(BAND_AXIS, y, BAND_TICKS, y.ticks(4).map { Tick(it, decimals(it, 1)) }, xTitle, yTitle, style, text)
    val barHalf = (area.px(0.3) - area.px(0.0))
    model.bands.forEachIndexed { band, rt ->
        val alpha = if (rt.estimated) 0.4f else 1f
        val x = area.px(band.toDouble())
        val barTop = area.py(rt.meanS)
        drawRect(style.muted.copy(alpha = alpha), Offset(x - barHalf, barTop), Size(2 * barHalf, area.bottom - barTop))
        if (model.overTarget[band]) {
            // The part above the target, in amber: how much too long this pitch rings.
            val targetY = area.py(model.targetS)
            drawRect(ChartColors.TooLong.copy(alpha = alpha), Offset(x - barHalf, barTop), Size(2 * barHalf, targetY - barTop))
        }
        if (rt.spreadS > 0.0) {
            val whisker = 1.5.dp.toPx()
            val high = area.py(rt.meanS + rt.spreadS)
            val low = area.py(rt.meanS - rt.spreadS)
            drawLine(style.axis, Offset(x, high), Offset(x, low), whisker)
            drawLine(style.axis, Offset(x - barHalf / 2, high), Offset(x + barHalf / 2, high), whisker)
            drawLine(style.axis, Offset(x - barHalf / 2, low), Offset(x + barHalf / 2, low), whisker)
        }
    }
    drawReferenceLine(area, model.targetS, style.axis)
}
