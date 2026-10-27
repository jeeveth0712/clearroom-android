package com.clearroom.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.clearroom.app.data.ListeningScore
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.Session
import com.clearroom.app.ui.AppTab
import com.clearroom.app.ui.Evaluation
import com.clearroom.app.ui.ReportState
import com.clearroom.app.ui.UiEvent
import com.clearroom.app.ui.formatDateTime
import com.clearroom.app.ui.formatMetres
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
import com.clearroom.app.viz.Rt60Bars
import com.clearroom.app.viz.Rt60Chart
import com.clearroom.app.viz.Rt60ChartModel
import com.clearroom.app.viz.Rt60Legend
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
fun worstSeat(clarity: RoomClarity, row: Int): SeatClarity = clarity.worstSeat(row)

/**
 * Results tab (doc "Screens"): verdict with the report button, before and after (while comparing),
 * the 3D room / seat map / cause map, seat details, then the charts.
 */
@Composable
fun ResultsScreen(session: Session?, evaluation: Evaluation?, report: ReportState, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    val clarity = evaluation?.result
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(AppTab.RESULTS, R.string.results_intro)
        when {
            session == null -> InfoCard { Body(R.string.results_no_room) }
            evaluation == null || clarity == null -> InfoCard { Body(R.string.results_working) }
            clarity is ClarityResult.NeedsMore -> {
                InfoCard {
                    Body(R.string.results_needs_more)
                    if (MissingInput.ECHO in clarity.missing) Body(R.string.results_missing_echo)
                    if (MissingInput.NOISE in clarity.missing) Body(R.string.results_missing_noise)
                }
                // Re-measuring after a change: "before" stays in view while "after" fills in.
                evaluation.before?.let { CompareCard(evaluation, it, after = null, onEvent) }
            }
            clarity is ClarityResult.Ready -> ReadyResults(evaluation, clarity.room, report, onEvent)
        }
    }
}

private val SeatSaver = listSaver<SeatId?, Int>(
    save = { seat -> seat?.let { listOf(it.row, it.col) } ?: emptyList() },
    restore = { saved -> if (saved.size == 2) SeatId(saved[0], saved[1]) else null },
)

@Composable
private fun ReadyResults(evaluation: Evaluation, clarity: RoomClarity, report: ReportState, onEvent: (UiEvent) -> Unit) {
    val session = evaluation.session
    var picked by rememberSaveable(session.room.id, stateSaver = SeatSaver) { mutableStateOf<SeatId?>(null) }
    val seat = clarity.seats.firstOrNull { it.seat == picked } ?: worstSeat(clarity, clarity.rows - 1)
    val seatLabel = seatLabel(seat.seat)

    VerdictCard(clarity, report, onEvent)
    evaluation.before?.let { CompareCard(evaluation, it, clarity, onEvent) }
    ClarityViewsCard(evaluation, clarity, seat.seat) { picked = it }
    // The sample's readings are made up, so it takes no field-test scores.
    val ownRoom = session.room.id != SampleData.ROOM_ID
    SeatDetailsCard(seat, seatLabel, session.listening.firstOrNull { it.seat == seat.seat }, editable = ownRoom) { words ->
        onEvent(UiEvent.SetListeningScore(seat.seat, words))
    }
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
    if (session.listening.isNotEmpty()) ListeningCheckCard(session)
    // The sample can't be measured into, so it can't be re-measured either.
    if (evaluation.before == null && session.room.id != SampleData.ROOM_ID) RemeasureCard(onEvent)
}

@Composable
private fun VerdictCard(clarity: RoomClarity, report: ReportState, onEvent: (UiEvent) -> Unit) {
    val back = rowVerdict(clarity, clarity.rows - 1)
    HeroCard {
        val headline = if (clarity.rows > 1) R.string.results_verdict_back else R.string.results_verdict_all
        Text(stringResource(headline, stringResource(back.band.phraseRes())), style = MaterialTheme.typography.titleLarge)
        BandRange(back.band, back.lowSti, back.highSti, large = true)
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
        HeroButton(R.string.results_share, onClick = { onEvent(UiEvent.ShareReport) }, enabled = report != ReportState.Writing)
        when (report) {
            ReportState.Writing -> Text(stringResource(R.string.results_share_writing), style = MaterialTheme.typography.bodySmall)
            ReportState.Failed -> Text(stringResource(R.string.results_share_failed), style = MaterialTheme.typography.bodySmall)
            ReportState.Idle, is ReportState.Ready -> Unit
        }
    }
}

/**
 * FR-13: the room before and after a change, on one colour scale and one echo-time scale. While
 * "after" has too few readings ([after] is null) it shows "before" and says what to measure.
 */
@Composable
private fun CompareCard(evaluation: Evaluation, before: Evaluation, after: RoomClarity?, onEvent: (UiEvent) -> Unit) {
    val session = evaluation.session
    val beforeClarity = (before.result as? ClarityResult.Ready)?.room
    val beforeEcho = Rt60ChartModel.of(before.session)
    val afterEcho = Rt60ChartModel.of(session).takeIf { after != null }
    ChartCard(R.string.compare_title) {
        session.baseline?.let { Text(stringResource(R.string.compare_kept, formatDateTime(it.takenAtMillis)), style = MaterialTheme.typography.bodySmall) }
        if (beforeClarity != null && after != null) {
            val was = rowVerdict(beforeClarity, beforeClarity.rows - 1)
            val now = rowVerdict(after, after.rows - 1)
            Text(
                stringResource(R.string.fixes_summary_back, stringResource(was.band.phraseRes()), stringResource(now.band.phraseRes())),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.compare_before_line, rangeText(was.band, was.lowSti, was.highSti)), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.compare_after_line, rangeText(now.band, now.lowSti, now.highSti)), style = MaterialTheme.typography.bodyMedium)
        } else {
            Body(R.string.compare_measure_again)
        }
        if (beforeEcho != null && afterEcho != null) {
            Text(
                stringResource(R.string.fixes_summary_echo, decimals(beforeEcho.speechRt60S, 1), decimals(afterEcho.speechRt60S, 1)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (beforeClarity != null && after != null) CurtainCheckSection(before.session, session)
        if (beforeClarity != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComparedMap(R.string.compare_before, SeatMapModel(session.room, beforeClarity, before.grid), Modifier.weight(1f))
                if (after != null) ComparedMap(R.string.compare_after, SeatMapModel(session.room, after, evaluation.grid), Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            }
            BandLegend()
        }
        if (beforeEcho != null) {
            // One scale for both, so equal bars before and after look equal.
            val axisTopS = maxOf(beforeEcho.axisTopS, afterEcho?.axisTopS ?: 0.0)
            Text(stringResource(R.string.compare_echo_before), style = MaterialTheme.typography.titleSmall)
            Rt60Bars(beforeEcho, axisTopS, Modifier.fillMaxWidth().height(COMPARED_CHART_HEIGHT))
            afterEcho?.let {
                Text(stringResource(R.string.compare_echo_after), style = MaterialTheme.typography.titleSmall)
                Rt60Bars(it, axisTopS, Modifier.fillMaxWidth().height(COMPARED_CHART_HEIGHT))
            }
            Rt60Legend(*listOfNotNull(beforeEcho, afterEcho).toTypedArray())
        }
        Text(stringResource(R.string.compare_caption), style = MaterialTheme.typography.bodySmall)
        val body = stringResource(if (session.seats.isEmpty()) R.string.compare_stop_body_restore else R.string.compare_stop_body_new)
        ConfirmFirst(R.string.compare_stop_title, body, R.string.compare_stop, R.string.compare_keep, onConfirm = { onEvent(UiEvent.StopComparing) }) { ask ->
            TextButton(onClick = ask) { Text(stringResource(R.string.compare_stop)) }
        }
    }
}

@Composable
private fun ComparedMap(@StringRes title: Int, model: SeatMapModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val name = stringResource(title)
        Text(name, style = MaterialTheme.typography.titleSmall)
        val description = name + ". " + pluralStringResource(R.plurals.results_map_description, model.clarity.seats.size, easySeatCount(model.clarity), model.clarity.seats.size)
        SeatMap(model, SeatMapMode.CLARITY, selected = null, onSeatSelected = {}, description = description)
    }
}

/** FR-13's way in: keep today's results as "before", then measure again after a change. */
@Composable
private fun RemeasureCard(onEvent: (UiEvent) -> Unit) {
    ChartCard(R.string.compare_offer_title) {
        Body(R.string.compare_offer_body)
        ConfirmFirst(
            R.string.compare_start_title,
            stringResource(R.string.compare_start_body),
            R.string.compare_start_confirm,
            R.string.compare_cancel,
            onConfirm = { onEvent(UiEvent.StartRemeasuring) },
        ) { ask -> OutlinedButton(onClick = ask) { Text(stringResource(R.string.compare_start)) } }
    }
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
private fun SeatDetailsCard(seat: SeatClarity, seatLabel: String, score: ListeningScore?, editable: Boolean, onScore: (Int?) -> Unit) {
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
        if (editable) {
            Text(stringResource(R.string.field_listening_title), style = MaterialTheme.typography.titleSmall)
            // Keyed by seat, so picking another seat shows its own score, not the last one typed.
            key(seat.seat) { ListeningScoreField(seat.seat, score, onScore) }
        }
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

@StringRes
private fun Cause.legendRes(): Int = when (this) {
    Cause.ECHO -> R.string.cause_echo_legend
    Cause.NOISE -> R.string.cause_noise_legend
    Cause.DISTANCE -> R.string.cause_distance_legend
}

@Composable
private fun seatLabel(seat: SeatId): String = stringResource(R.string.seat_label, seat.row + 1, seat.col + 1)

private val COMPARED_CHART_HEIGHT = 170.dp
