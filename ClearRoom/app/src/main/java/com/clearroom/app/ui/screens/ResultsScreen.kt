package com.clearroom.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.acoustics.Cause
import com.clearroom.app.acoustics.ClarityGrid
import com.clearroom.app.acoustics.ClarityResult
import com.clearroom.app.acoustics.MissingInput
import com.clearroom.app.acoustics.RoomClarity
import com.clearroom.app.acoustics.SeatClarity
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.data.ClapCurves
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.Session
import com.clearroom.app.ui.AppTab
import com.clearroom.app.ui.Evaluation
import com.clearroom.app.ui.formatMetres
import com.clearroom.app.ui.theme.color
import com.clearroom.app.viz.BAND_NAMES
import com.clearroom.app.viz.ClarityRoom3D
import com.clearroom.app.viz.ClarityTerrain3D
import com.clearroom.app.viz.DecayChart
import com.clearroom.app.viz.DistanceChart
import com.clearroom.app.viz.DistanceModel
import com.clearroom.app.viz.EchoWaterfall3D
import com.clearroom.app.viz.EchoWaterfallScene
import com.clearroom.app.viz.MtfChart
import com.clearroom.app.viz.MtfModel
import com.clearroom.app.viz.Rt60Chart
import com.clearroom.app.viz.Rt60ChartModel
import com.clearroom.app.viz.SeatMap
import com.clearroom.app.viz.SeatMapMode
import com.clearroom.app.viz.SeatMapModel
import com.clearroom.app.viz.SpectrumChart
import com.clearroom.app.viz.SpectrumModel
import com.clearroom.app.viz.TerrainLabels
import com.clearroom.app.viz.WaterfallLabels
import com.clearroom.app.viz.causeCounts
import com.clearroom.app.viz.decaySource
import com.clearroom.app.viz.decimals
import com.clearroom.app.viz.drawCauseBadge
import com.clearroom.app.viz.easySeatCount
import com.clearroom.app.viz.longestRingingBand

/** One row's verdict: its worst seat's band, uncertainty range (NFR-10) and main cause. */
data class RowVerdict(val band: StiBand, val lowSti: Double, val highSti: Double, val cause: Cause)

/** A row is judged by its worst seat: the goal is that every seat can follow. */
fun rowVerdict(clarity: RoomClarity, row: Int): RowVerdict {
    val worst = worstSeat(clarity, row)
    return RowVerdict(worst.band, worst.stiLow, worst.stiHigh, worst.cause)
}

/** The least clear seat in [row]; the Results tab opens on the worst seat of the back row. */
fun worstSeat(clarity: RoomClarity, row: Int): SeatClarity = clarity.seats.filter { it.seat.row == row }.minBy { it.sti }

/** Results tab (doc "Screens"): verdict, the 3D room / seat map / cause map, seat details, then the charts. */
@Composable
fun ResultsScreen(session: Session?, evaluation: Evaluation?, modifier: Modifier = Modifier) {
    val clarity = evaluation?.result
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(AppTab.RESULTS.labelRes), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.results_intro), style = MaterialTheme.typography.bodyLarge)
        when {
            session == null -> InfoCard { Body(R.string.results_no_room) }
            evaluation == null || clarity == null -> InfoCard { Body(R.string.results_working) }
            clarity is ClarityResult.NeedsMore -> InfoCard {
                Body(R.string.results_needs_more)
                if (MissingInput.ECHO in clarity.missing) Body(R.string.results_missing_echo)
                if (MissingInput.NOISE in clarity.missing) Body(R.string.results_missing_noise)
            }
            clarity is ClarityResult.Ready -> ReadyResults(evaluation, clarity.room)
        }
    }
}

private val SeatSaver = listSaver<SeatId?, Int>(
    save = { seat -> seat?.let { listOf(it.row, it.col) } ?: emptyList() },
    restore = { saved -> if (saved.size == 2) SeatId(saved[0], saved[1]) else null },
)

@Composable
private fun ReadyResults(evaluation: Evaluation, clarity: RoomClarity) {
    val session = evaluation.session
    var picked by rememberSaveable(session.room.id, stateSaver = SeatSaver) { mutableStateOf<SeatId?>(null) }
    val seat = clarity.seats.firstOrNull { it.seat == picked } ?: worstSeat(clarity, clarity.rows - 1)
    val seatLabel = seatLabel(seat.seat)

    VerdictCard(clarity)
    ClarityViewsCard(evaluation, clarity, seat.seat) { picked = it }
    SeatDetailsCard(seat, seatLabel)
    evaluation.grid?.let { TerrainCard(it, clarity) }
    val decay = decaySource(session, seat.seat)
    ChartCard(R.string.results_decay_title) {
        if (decay == null) Body(R.string.results_decay_empty) else DecayChart(decay, seatLabel(decay.seat))
    }
    Rt60ChartModel.of(session)?.let { model -> ChartCard(R.string.results_rt_title) { Rt60Chart(model) } }
    ChartCard(R.string.results_waterfall_title) {
        if (decay == null || !EchoWaterfallScene.canDraw(decay.curves)) Body(R.string.results_decay_empty) else WaterfallContent(decay.curves, seatLabel(decay.seat))
    }
    ChartCard(R.string.results_mtf_title) {
        Text(seatLabel, style = MaterialTheme.typography.bodySmall)
        MtfChart(MtfModel.of(clarity, seat), seatLabel)
    }
    ChartCard(R.string.results_distance_title) { DistanceChart(DistanceModel.of(clarity)) }
}

@Composable
private fun VerdictCard(clarity: RoomClarity) {
    val back = rowVerdict(clarity, clarity.rows - 1)
    InfoCard {
        val headline = if (clarity.rows > 1) R.string.results_verdict_back else R.string.results_verdict_all
        Text(stringResource(headline, stringResource(back.band.phraseRes())), style = MaterialTheme.typography.titleLarge)
        BandRange(back.band, back.lowSti, back.highSti)
        Text(stringResource(R.string.results_verdict_cause, stringResource(back.cause.nameRes())), style = MaterialTheme.typography.titleSmall)
        Body(back.cause.whyRes())
        if (clarity.rows > 1) {
            val front = rowVerdict(clarity, 0)
            Text(
                stringResource(R.string.results_verdict_front, stringResource(front.band.phraseRes()), rangeText(front.band, front.lowSti, front.highSti)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!clarity.voiceMeasured) Body(R.string.results_uncalibrated)
    }
}

/** "Fair · 0.55–0.61" next to the band's colour: the verdict with its uncertainty range (NFR-10). */
@Composable
private fun BandRange(band: StiBand, low: Double, high: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(14.dp).background(band.color(), RoundedCornerShape(3.dp)))
        Text(rangeText(band, low, high), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

/** "Fair · 0.55–0.61", or "Fair · 0.59" when the range is narrower than the digits shown. */
@Composable
private fun rangeText(band: StiBand, low: Double, high: Double): String {
    val (lowText, highText) = decimals(low, 2) to decimals(high, 2)
    val name = stringResource(band.labelRes())
    return if (lowText == highText) stringResource(R.string.results_band_value, name, lowText) else stringResource(R.string.results_band_range, name, lowText, highText)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClarityViewsCard(evaluation: Evaluation, clarity: RoomClarity, selected: SeatId, onSeatSelected: (SeatId) -> Unit) {
    var view by rememberSaveable { mutableIntStateOf(0) }
    val views = listOf(R.string.results_view_3d, R.string.results_view_map, R.string.results_view_causes)
    val room = evaluation.session.room
    InfoCard {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            views.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = index == view,
                    onClick = { view = index },
                    shape = SegmentedButtonDefaults.itemShape(index, views.size),
                    label = { Text(stringResource(label)) },
                )
            }
        }
        when (view) {
            0 -> {
                val description = stringResource(
                    R.string.results_3d_description,
                    stringResource(StiBand.from(clarity.frontRowSti).labelRes()),
                    stringResource(StiBand.from(clarity.backRowSti).labelRes()),
                )
                ClarityRoom3D(room, clarity, description)
                BandLegend()
                Text(stringResource(R.string.results_3d_caption), style = MaterialTheme.typography.bodySmall)
            }
            1 -> {
                val description = pluralStringResource(R.plurals.results_map_description, clarity.seats.size, easySeatCount(clarity), clarity.seats.size)
                SeatMap(SeatMapModel(room, clarity, evaluation.grid), SeatMapMode.CLARITY, selected, onSeatSelected, description)
                BandLegend()
                Text(stringResource(R.string.results_map_caption), style = MaterialTheme.typography.bodySmall)
            }
            else -> {
                val counts = causeCounts(clarity)
                val description = stringResource(R.string.results_causes_description, counts.getValue(Cause.ECHO), counts.getValue(Cause.NOISE), counts.getValue(Cause.DISTANCE))
                SeatMap(SeatMapModel(room, clarity, evaluation.grid), SeatMapMode.CAUSE, selected, onSeatSelected, description)
                CauseLegend()
                Text(stringResource(R.string.results_causes_caption), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CauseLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Cause.entries.forEach { cause ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Canvas(Modifier.size(22.dp)) { drawCauseBadge(cause, center, size.minDimension / 2) }
                Text(stringResource(cause.legendRes()), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SeatDetailsCard(seat: SeatClarity, seatLabel: String) {
    InfoCard {
        Text(stringResource(R.string.results_seat_title, seatLabel), style = MaterialTheme.typography.titleMedium)
        BandRange(seat.band, seat.stiLow, seat.stiHigh)
        Text(stringResource(seat.band.phraseRes()).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.results_verdict_cause, stringResource(seat.cause.nameRes())), style = MaterialTheme.typography.titleSmall)
        Body(seat.cause.whyRes())
        Text(
            stringResource(R.string.results_seat_distance, seat.distanceM.formatMetres()) + " · " +
                stringResource(if (seat.isMeasured) R.string.results_seat_measured else R.string.results_seat_estimated),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(stringResource(R.string.results_spectrum_title), style = MaterialTheme.typography.titleSmall)
        SpectrumChart(SpectrumModel.of(seat))
        Text(stringResource(R.string.results_seat_hint), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TerrainCard(grid: ClarityGrid, clarity: RoomClarity) {
    ChartCard(R.string.results_terrain_title) {
        val cells = (0 until grid.rows).flatMap { j -> (0 until grid.columns).map { i -> grid.cell(i, j).coerceIn(0.0, 1.0) } }
        val description = stringResource(
            R.string.results_terrain_description,
            stringResource(StiBand.from(cells.min()).labelRes()),
            stringResource(StiBand.from(cells.max()).labelRes()),
        )
        val labels = TerrainLabels(stringResource(R.string.results_3d_label_speaker), stringResource(R.string.results_3d_label_back))
        ClarityTerrain3D(grid, clarity.speaker, labels, description)
        BandLegend()
        Text(stringResource(R.string.results_terrain_caption), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WaterfallContent(curves: ClapCurves, seatLabel: String) {
    val labels = WaterfallLabels(
        lowBand = BAND_NAMES.first(),
        midBand = BAND_NAMES[3],
        highBand = BAND_NAMES.last(),
        start = stringResource(R.string.chart_seconds, 0),
        oneSecond = stringResource(R.string.chart_seconds, 1),
        twoSeconds = stringResource(R.string.chart_seconds, 2),
    )
    val longest = longestRingingBand(curves)
    val caption = stringResource(R.string.results_waterfall_caption) + if (longest == null) "" else " " + stringResource(
        R.string.results_waterfall_longest,
        BAND_NAMES[longest],
        decimals(requireNotNull(curves.bands[longest].fit).t60S, 1),
    )
    Text(stringResource(R.string.results_decay_source, seatLabel), style = MaterialTheme.typography.bodySmall)
    EchoWaterfall3D(curves, labels, caption)
    Text(caption, style = MaterialTheme.typography.bodySmall)
}

/** The four clarity bands with their fixed colours; colour is never the only cue (names shown too). */
@Composable
fun BandLegend(modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StiBand.entries.forEach { band ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(12.dp).background(band.color(), RoundedCornerShape(2.dp)))
                Text(stringResource(band.labelRes()), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@StringRes
fun StiBand.labelRes(): Int = when (this) {
    StiBand.POOR -> R.string.band_poor
    StiBand.FAIR -> R.string.band_fair
    StiBand.GOOD -> R.string.band_good
    StiBand.EXCELLENT -> R.string.band_excellent
}

/** The band in plain words (NFR-2): what listening there is like. */
@StringRes
fun StiBand.phraseRes(): Int = when (this) {
    StiBand.POOR -> R.string.band_phrase_poor
    StiBand.FAIR -> R.string.band_phrase_fair
    StiBand.GOOD -> R.string.band_phrase_good
    StiBand.EXCELLENT -> R.string.band_phrase_excellent
}

@StringRes
fun Cause.nameRes(): Int = when (this) {
    Cause.ECHO -> R.string.cause_echo
    Cause.NOISE -> R.string.cause_noise
    Cause.DISTANCE -> R.string.cause_distance
}

/** Why this cause hurts, and the change that helps most. */
@StringRes
fun Cause.whyRes(): Int = when (this) {
    Cause.ECHO -> R.string.cause_echo_why
    Cause.NOISE -> R.string.cause_noise_why
    Cause.DISTANCE -> R.string.cause_distance_why
}

@StringRes
private fun Cause.legendRes(): Int = when (this) {
    Cause.ECHO -> R.string.cause_echo_legend
    Cause.NOISE -> R.string.cause_noise_legend
    Cause.DISTANCE -> R.string.cause_distance_legend
}

@Composable
private fun seatLabel(seat: SeatId): String = stringResource(R.string.seat_label, seat.row + 1, seat.col + 1)

@Composable
private fun ChartCard(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    InfoCard {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun Body(@StringRes text: Int) = Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
