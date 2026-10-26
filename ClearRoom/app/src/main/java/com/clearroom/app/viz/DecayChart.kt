package com.clearroom.app.viz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.data.BandCurve
import com.clearroom.app.data.ClapCurves
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.Session
import com.clearroom.app.dsp.DspConstants
import kotlin.math.max

/** How straight a band's decay was (ISO 3382 non-linearity ξ): above 40 ‰ ClearRoom refuses the band. */
enum class FitQuality { CLEAN, USABLE, ROUGH }

fun fitQuality(xiPermille: Double): FitQuality = when {
    xiPermille <= CLEAN_FIT_PERMILLE -> FitQuality.CLEAN
    xiPermille <= DspConstants.MAX_NONLINEARITY_PERMILLE -> FitQuality.USABLE
    else -> FitQuality.ROUGH
}

/** V3 data: the latest clap kept at [seat]. */
data class DecaySource(val seat: SeatId, val curves: ClapCurves)

/** The latest clap at [preferred] if it has one, otherwise at the first seat that does; null before any clap. */
fun decaySource(session: Session, preferred: SeatId?): DecaySource? {
    val withCurves = session.seats.filter { it.lastClap != null }
    val measure = withCurves.firstOrNull { it.seat == preferred } ?: withCurves.firstOrNull() ?: return null
    return DecaySource(measure.seat, requireNotNull(measure.lastClap))
}

/** How far the chart's time axis runs: the curve's end, or where the fit line reaches −60 dB, whichever is later. */
fun decayEndS(band: BandCurve): Double {
    val curveEnd = band.schroederDb.size * ClapCurves.STEP_S
    val fitEnd = band.fit?.let { (FIT_END_DB - it.interceptDb) / it.slopeDbPerS } ?: 0.0
    return max(max(curveEnd, fitEnd), MIN_SPAN_S)
}

@Composable
fun DecayChart(source: DecaySource, seatLabel: String, modifier: Modifier = Modifier) {
    var band by rememberSaveable { mutableIntStateOf(DEFAULT_BAND) }
    val curve = source.curves.bands[band]
    val text = rememberTextMeasurer()
    val style = ChartStyle.fromTheme()
    val xTitle = stringResource(R.string.chart_time_after_clap_s)
    val yTitle = stringResource(R.string.chart_level_db)
    val bandName = BAND_NAMES[band]
    val fit = curve.fit
    val value = if (fit == null) {
        stringResource(R.string.results_decay_band_rejected)
    } else {
        stringResource(R.string.results_decay_value, bandName, decimals(fit.t60S, 2), stringResource(fitQuality(fit.xiPermille).textRes()))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.results_decay_source, seatLabel), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BAND_LABELS.forEachIndexed { index, label ->
                FilterChip(selected = index == band, onClick = { band = index }, label = { Text(label) })
            }
        }
        Canvas(Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = value }) {
            drawDecayChart(curve, xTitle, yTitle, style, text)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.results_decay_caption), style = MaterialTheme.typography.bodySmall)
    }
}

private fun FitQuality.textRes(): Int = when (this) {
    FitQuality.CLEAN -> R.string.results_decay_fit_clean
    FitQuality.USABLE -> R.string.results_decay_fit_usable
    FitQuality.ROUGH -> R.string.results_decay_fit_rough
}

fun DrawScope.drawDecayChart(band: BandCurve, xTitle: String, yTitle: String, style: ChartStyle, text: TextMeasurer) {
    val x = Axis.upTo(decayEndS(band), maxCount = 4)
    val y = Axis(BOTTOM_DB, 0.0)
    val area = drawFrame(x, y, x.ticks(4).map { Tick(it, decimals(it, 1)) }, y.ticks(4).map { Tick(it, decimals(it, 0)) }, xTitle, yTitle, style, text)
    // The range the echo time is fitted over (−5 to −25 dB), shaded so it's clear which part counts.
    val fitTop = area.py(DspConstants.T20_FROM_DB)
    drawRect(style.emphasis.copy(alpha = 0.12f), Offset(area.left, fitTop), Size(area.right - area.left, area.py(DspConstants.T20_TO_DB) - fitTop))
    band.fit?.let { fit ->
        val endS = (FIT_END_DB - fit.interceptDb) / fit.slopeDbPerS
        drawLine(style.emphasis, Offset(area.px(0.0), area.py(fit.interceptDb)), Offset(area.px(endS), area.py(FIT_END_DB)), 2.dp.toPx(), StrokeCap.Round)
    }
    if (band.schroederDb.isNotEmpty()) {
        val path = Path()
        band.schroederDb.forEachIndexed { k, db ->
            val point = Offset(area.px(k * ClapCurves.STEP_S), area.py(max(db, BOTTOM_DB)))
            if (k == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, style.axis, style = Stroke(1.5.dp.toPx()))
    }
}

private const val DEFAULT_BAND = 3 // 1 kHz, the middle of speech
private const val CLEAN_FIT_PERMILLE = 10.0 // ISO 3382-1: below this the decay counts as straight
private const val FIT_END_DB = -60.0
private const val BOTTOM_DB = -70.0
private const val MIN_SPAN_S = 0.2
