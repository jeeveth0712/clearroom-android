package com.clearroom.app.viz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.acoustics.RoomClarity
import com.clearroom.app.acoustics.SeatClarity
import com.clearroom.app.acoustics.SpeechPropagation
import com.clearroom.app.dsp.OctaveBands
import com.clearroom.app.dsp.Sti
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Doc: speech is comfortable to follow when the voice is about 15 dB above the noise. */
const val COMFORTABLE_SNR_DB = 15.0

/** V14 and V13's one-number summary use the 1 kHz band, the middle of speech. */
private const val MID_BAND = 3

// --- V13 MTF curves ---------------------------------------------------------------------------------

/** V13: how much of the speech rhythm (modulation m, 0–1) survives at each rhythm speed, per band, at one seat. */
data class MtfModel(val curves: List<List<Double>>) {
    /** The mean m at 1 kHz: "about this much of the rhythm survives". */
    val midBandMean: Double get() = curves[MID_BAND].average()

    companion object {
        fun of(clarity: RoomClarity, seat: SeatClarity): MtfModel = MtfModel(
            List(OctaveBands.COUNT) { band ->
                val snrDb = seat.speechDb[band] - seat.noiseDb[band]
                val direct = SpeechPropagation.directToReverberant(seat.distanceM, clarity.roomConstantM2[band])
                Sti.MOD_FREQS_HZ.map { f -> Sti.modulationIndex(f, clarity.rt60S[band], snrDb, direct) }
            },
        )
    }
}

private val MTF_TICKS = listOf(0.63, 1.0, 2.0, 4.0, 8.0, 12.5)

@Composable
fun MtfChart(model: MtfModel, seatLabel: String, modifier: Modifier = Modifier) {
    val text = rememberTextMeasurer()
    val style = ChartStyle.fromTheme()
    val xTitle = stringResource(R.string.chart_rhythm_hz)
    val yTitle = stringResource(R.string.chart_rhythm_kept)
    val description = stringResource(R.string.results_mtf_description, seatLabel, (model.midBandMean * 100).roundToInt())
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = description }) {
            drawMtfChart(model, xTitle, yTitle, style, text)
        }
        BandColourLegend()
        Text(stringResource(R.string.results_mtf_caption), style = MaterialTheme.typography.bodySmall)
    }
}

fun DrawScope.drawMtfChart(model: MtfModel, xTitle: String, yTitle: String, style: ChartStyle, text: TextMeasurer) {
    val x = Axis(Sti.MOD_FREQS_HZ.first(), Sti.MOD_FREQS_HZ.last(), log = true)
    val y = Axis(0.0, 1.0)
    val xTicks = MTF_TICKS.map { Tick(it, if (it < 1) decimals(it, 2) else if (it % 1.0 == 0.0) decimals(it, 0) else decimals(it, 1)) }
    val area = drawFrame(x, y, xTicks, y.ticks(4).map { Tick(it, decimals(it, 1)) }, xTitle, yTitle, style, text)
    model.curves.forEachIndexed { band, curve ->
        val path = Path()
        curve.forEachIndexed { k, m ->
            val point = Offset(area.px(Sti.MOD_FREQS_HZ[k]), area.py(m))
            if (k == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, ChartColors.Bands[band], style = Stroke(2.dp.toPx()))
    }
}

/** The seven band colours of V13 with their names, so colour is never the only cue. */
@Composable
fun BandColourLegend(modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BAND_LABELS.forEachIndexed { band, label -> Swatch(ChartColors.Bands[band], label) }
    }
}

@Composable
fun Swatch(colour: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).background(colour, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// --- V14 Speech vs noise by distance ----------------------------------------------------------------

/**
 * V14 at 1 kHz, in dB relative to the voice at 1 m: the speech level against distance, the room's
 * average noise, where echo takes over (critical distance), and where the seats are.
 */
data class DistanceModel(
    val distancesM: List<Double>,
    val speechRelDb: List<Double>,
    val noiseRelDb: Double,
    val criticalDistanceM: Double,
    val seatDistancesM: List<Double>,
    /** How far the voice stands above the noise at the worst back-row seat. */
    val backRowMarginDb: Double,
) {
    val backRowComfortable: Boolean get() = backRowMarginDb >= COMFORTABLE_SNR_DB

    companion object {
        private const val POINTS = 60

        fun of(clarity: RoomClarity): DistanceModel {
            val voice = clarity.voiceAt1mDb[MID_BAND]
            val roomConstant = clarity.roomConstantM2[MID_BAND]
            val farthest = max(clarity.seats.maxOf { it.distanceM } * 1.1, 2.0)
            val start = SpeechPropagation.MIN_DISTANCE_M
            val distances = List(POINTS) { start + (farthest - start) * it / (POINTS - 1) }
            val noiseEnergy = clarity.seats.map { 10.0.pow(it.noiseDb[MID_BAND] / 10) }.average()
            val backRow = clarity.seats.filter { it.seat.row == clarity.rows - 1 }
            return DistanceModel(
                distancesM = distances,
                speechRelDb = distances.map { SpeechPropagation.levelDb(it, voice, roomConstant) - voice },
                noiseRelDb = 10 * log10(noiseEnergy) - voice,
                criticalDistanceM = clarity.criticalDistanceM,
                seatDistancesM = clarity.seats.map { it.distanceM }.distinct().sorted(),
                backRowMarginDb = backRow.minOf { it.speechDb[MID_BAND] - it.noiseDb[MID_BAND] },
            )
        }
    }
}

@Composable
fun DistanceChart(model: DistanceModel, modifier: Modifier = Modifier) {
    val text = rememberTextMeasurer()
    val style = ChartStyle.fromTheme()
    val xTitle = stringResource(R.string.chart_distance_m)
    val yTitle = stringResource(R.string.chart_level_1k_db)
    val echoLabel = stringResource(R.string.results_distance_echo_takes_over)
    val caption = stringResource(
        if (model.backRowComfortable) R.string.results_distance_caption_ok else R.string.results_distance_caption_masked,
        decimals(model.criticalDistanceM, 1),
        model.backRowMarginDb.roundToInt(),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = caption }) {
            drawDistanceChart(model, xTitle, yTitle, echoLabel, style, text)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Swatch(ChartColors.Voice, stringResource(R.string.chart_voice))
            Swatch(ChartColors.Noise, stringResource(R.string.chart_noise))
            Swatch(ChartColors.Noise.copy(alpha = 0.25f), stringResource(R.string.chart_masked_zone))
        }
        Text(caption, style = MaterialTheme.typography.bodySmall)
    }
}

fun DrawScope.drawDistanceChart(model: DistanceModel, xTitle: String, yTitle: String, echoLabel: String, style: ChartStyle, text: TextMeasurer) {
    val highest = max(model.speechRelDb.max(), model.noiseRelDb + COMFORTABLE_SNR_DB) + 3
    val lowest = min(model.speechRelDb.min(), model.noiseRelDb) - 5
    val step = Axis.niceStep(highest - lowest, 4)
    val y = Axis(floor(lowest / step) * step, ceil(highest / step) * step)
    val x = Axis.upTo(model.distancesM.last(), maxCount = 5)
    val area = drawFrame(x, y, x.ticks(5).map { Tick(it, decimals(it, 0)) }, y.ticks(4).map { Tick(it, decimals(it, 0)) }, xTitle, yTitle, style, text)

    // Where the voice is less than 15 dB above the noise: words get masked.
    val zoneTop = area.py(model.noiseRelDb + COMFORTABLE_SNR_DB)
    drawRect(ChartColors.Noise.copy(alpha = 0.25f), Offset(area.left, zoneTop), Size(area.right - area.left, area.py(model.noiseRelDb) - zoneTop))
    drawLine(ChartColors.Noise, Offset(area.left, area.py(model.noiseRelDb)), Offset(area.right, area.py(model.noiseRelDb)), 2.dp.toPx())

    val path = Path()
    model.distancesM.zip(model.speechRelDb).forEachIndexed { k, (d, level) ->
        val point = Offset(area.px(d), area.py(level))
        if (k == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    drawPath(path, ChartColors.Voice, style = Stroke(2.5.dp.toPx()))

    if (model.criticalDistanceM in x.min..x.max) {
        val px = area.px(model.criticalDistanceM)
        drawLine(style.axis, Offset(px, area.top), Offset(px, area.bottom), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        val label = text.measure(echoLabel, style.label)
        drawText(label, topLeft = Offset(min(px + 4.dp.toPx(), area.right - label.size.width), area.top))
    }
    // Seats as small marks along the bottom, so "the back row is here" is visible.
    val mark = 5.dp.toPx()
    model.seatDistancesM.forEach { d ->
        val px = area.px(d)
        drawLine(ChartColors.Voice, Offset(px, area.bottom - mark), Offset(px, area.bottom), 2.dp.toPx())
    }
}

// --- V15 Octave spectrum at a seat -----------------------------------------------------------------

/** V15: the voice and the noise at one seat per band, and how far the voice is above the noise (SNR). */
data class SpectrumModel(val voiceDb: List<Double>, val noiseDb: List<Double>) {
    val snrDb: List<Double> get() = voiceDb.zip(noiseDb) { voice, noise -> voice - noise }

    /** Mean SNR over the speech bands, 500 Hz–2 kHz. */
    val speechSnrDb: Double get() = OctaveBands.SPEECH_BANDS.map { snrDb[it] }.average()

    companion object {
        fun of(seat: SeatClarity) = SpectrumModel(seat.speechDb, seat.noiseDb)
    }
}

@Composable
fun SpectrumChart(model: SpectrumModel, modifier: Modifier = Modifier) {
    val text = rememberTextMeasurer()
    val style = ChartStyle.fromTheme()
    val xTitle = stringResource(R.string.chart_pitch_hz)
    val yTitle = stringResource(R.string.chart_level_relative_db)
    val description = stringResource(R.string.results_spectrum_description, model.speechSnrDb.roundToInt())
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = description }) {
            drawSpectrumChart(model, xTitle, yTitle, style, text)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Swatch(ChartColors.Voice, stringResource(R.string.chart_voice))
            Swatch(ChartColors.Noise, stringResource(R.string.chart_noise))
        }
        Text(stringResource(R.string.results_spectrum_caption), style = MaterialTheme.typography.bodySmall)
    }
}

/** Levels relative to the loudest bar (0 dB at the top); the SNR is printed above each band. */
fun DrawScope.drawSpectrumChart(model: SpectrumModel, xTitle: String, yTitle: String, style: ChartStyle, text: TextMeasurer) {
    val loudest = max(model.voiceDb.max(), model.noiseDb.max())
    val voice = model.voiceDb.map { it - loudest }
    val noise = model.noiseDb.map { it - loudest }
    val step = Axis.niceStep(-min(voice.min(), noise.min()) + 5, 4)
    val y = Axis(floor((min(voice.min(), noise.min()) - 5) / step) * step, SNR_LABEL_ROOM_DB)
    val area = drawFrame(BAND_AXIS, y, BAND_TICKS, y.ticks(4).filter { it <= 0.0 }.map { Tick(it, decimals(it, 0)) }, xTitle, yTitle, style, text)
    val barWidth = area.px(0.32) - area.px(0.0)
    model.snrDb.forEachIndexed { band, snr ->
        val centre = area.px(band.toDouble())
        drawRect(ChartColors.Voice, Offset(centre - barWidth, area.py(voice[band])), Size(barWidth, area.bottom - area.py(voice[band])))
        drawRect(ChartColors.Noise, Offset(centre, area.py(noise[band])), Size(barWidth, area.bottom - area.py(noise[band])))
        val label = text.measure((if (snr >= 0) "+" else "") + snr.roundToInt(), style.label.copy(color = if (snr < COMFORTABLE_SNR_DB) style.emphasis else style.axis))
        drawText(label, topLeft = Offset(centre - label.size.width / 2f, area.py(SNR_LABEL_ROOM_DB)))
    }
}

/** Head room above 0 dB for the SNR numbers. */
private const val SNR_LABEL_ROOM_DB = 8.0
