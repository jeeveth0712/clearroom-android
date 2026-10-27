package com.clearroom.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.Session
import com.clearroom.app.daylog.DayLogState
import com.clearroom.app.daylog.LogSummary
import com.clearroom.app.permission.MicAccess
import com.clearroom.app.permission.rememberMicPermission
import com.clearroom.app.ui.AppTab
import com.clearroom.app.ui.DayLogView
import com.clearroom.app.ui.UiEvent
import com.clearroom.app.viz.BAND_NAMES
import com.clearroom.app.viz.DayTimelineChart
import com.clearroom.app.viz.LandscapeLabels
import com.clearroom.app.viz.NoiseLandscape3D
import com.clearroom.app.viz.TimelineWords
import com.clearroom.app.viz.approxDba
import com.clearroom.app.viz.clockTime
import com.clearroom.app.viz.decimals
import com.clearroom.app.viz.formatDuration
import com.clearroom.app.viz.nameRes

/** How many of the latest events the list shows; the rest are in the timeline marks. */
const val EVENTS_SHOWN = 12

/** Log tab (doc "Screens"): start and stop the day log, then its summary, V5, V11 and the events. */
@Composable
fun LogScreen(
    session: Session?,
    measureBusy: Boolean,
    view: DayLogView?,
    liveLevelDb: () -> Double,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mic = rememberMicPermission()
    LogContent(mic.access, session, measureBusy, view, liveLevelDb, mic.request, mic.openSettings, onEvent, modifier)
}

/** The Log tab without the permission plumbing, so it can be shown in tests and previews with any state. */
@Composable
fun LogContent(
    micAccess: MicAccess,
    session: Session?,
    measureBusy: Boolean,
    view: DayLogView?,
    liveLevelDb: () -> Double,
    onAllowMic: () -> Unit,
    onOpenSettings: () -> Unit,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(AppTab.LOG, R.string.log_intro)
        when {
            session == null -> InfoCard { Body(R.string.log_no_room) }
            session.room.id == SampleData.ROOM_ID -> InfoCard { Body(R.string.log_sample_room) }
            else -> {
                val log = view?.log?.takeIf { it.roomId == session.room.id } ?: DayLogState(roomId = session.room.id)
                when (micAccess) {
                    MicAccess.NOT_ASKED -> MicAccessCard(R.string.mic_why, R.string.mic_allow, onAllowMic)
                    MicAccess.DENIED_CAN_ASK_AGAIN -> MicAccessCard(R.string.mic_denied_can_ask_again, R.string.mic_allow, onAllowMic)
                    MicAccess.DENIED_PERMANENTLY -> MicAccessCard(R.string.mic_denied_permanently, R.string.mic_open_settings, onOpenSettings)
                    MicAccess.GRANTED -> ControlCard(log, view?.logSpot?.let { seatLabel(it.row, it.col) }, measureBusy, liveLevelDb, onEvent)
                }
                val summary = view?.summary?.takeIf { log.seconds.isNotEmpty() }
                if (summary != null && log.startMillis != null) {
                    SummaryCard(summary, log.startMillis)
                    TimelineCard(summary, log.startMillis)
                    LandscapeCard(summary)
                    EventsCard(summary)
                } else if (!log.running) {
                    InfoCard { Body(R.string.log_empty) }
                }
            }
        }
    }
}

@Composable
private fun ControlCard(log: DayLogState, spot: String?, measureBusy: Boolean, liveLevelDb: () -> Double, onEvent: (UiEvent) -> Unit) {
    if (log.running) {
        // The banner the risk table asks for: visible the whole time the microphone is on.
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.log_banner), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.log_banner_detail), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    InfoCard {
        spot?.let { Text(stringResource(R.string.log_spot, it), style = MaterialTheme.typography.bodyMedium) }
        if (log.running) {
            Text(stringResource(R.string.log_running, formatDuration(log.seconds.size * 1_000L)), style = MaterialTheme.typography.titleMedium)
            LevelBar(liveLevelDb)
            Body(R.string.log_how)
            OutlinedButton(onClick = { onEvent(UiEvent.StopLog) }) { Text(stringResource(R.string.log_stop)) }
        } else {
            Body(R.string.log_how)
            Button(onClick = { onEvent(UiEvent.StartLog) }, enabled = !measureBusy) { Text(stringResource(R.string.log_start)) }
        }
        if (!log.labelsAvailable) Body(R.string.log_no_labels)
        if (log.saveFailed) Body(R.string.log_save_failed)
        log.problem?.let { Body(it.messageRes()) }
        if (!log.running && log.seconds.isNotEmpty()) ClearLogsButton { onEvent(UiEvent.ClearLogs) }
    }
}

/** Deletes the room's day logs, after asking: it can't be undone. Measurements are never touched. */
@Composable
private fun ClearLogsButton(onConfirm: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    TextButton(onClick = { asking = true }) { Text(stringResource(R.string.log_clear)) }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.log_clear_title)) },
            text = { Text(stringResource(R.string.log_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    onConfirm()
                }) { Text(stringResource(R.string.log_clear_confirm)) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.log_clear_keep)) } },
        )
    }
}

@Composable
private fun SummaryCard(summary: LogSummary, startMillis: Long) {
    ChartCard(R.string.log_summary_title) {
        Text(stringResource(R.string.log_summary_started, clockTime(startMillis), formatDuration(summary.spanMillis)), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.log_summary_levels, decimals(approxDba(summary.leqDba), 0), decimals(approxDba(summary.l90Dba), 0)),
            style = MaterialTheme.typography.bodyMedium,
        )
        summary.loudestEvent?.let { event ->
            Text(
                stringResource(R.string.log_summary_loudest, stringResource(event.label.nameRes()), clockTime(event.startMillis), decimals(approxDba(event.maxDba), 0)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TimelineCard(summary: LogSummary, startMillis: Long) {
    ChartCard(R.string.log_timeline_title) {
        val caption = buildString {
            append(stringResource(R.string.log_timeline_caption_noise))
            val best = summary.bestPeriodStartMillis
            if (best != null) {
                append(' ')
                if (summary.periodsBelowTarget == 0) {
                    append(stringResource(R.string.log_timeline_caption_ok))
                } else {
                    append(stringResource(R.string.log_timeline_caption_low, summary.periodsBelowTarget, summary.periodsWithClarity, clockTime(best)))
                }
            }
        }
        val words = TimelineWords(
            noiseTitle = stringResource(R.string.log_timeline_noise),
            clarityTitle = stringResource(R.string.log_timeline_clarity),
            background = stringResource(R.string.log_timeline_background),
            notMeasured = stringResource(R.string.log_timeline_not_measured),
        )
        DayTimelineChart(summary, startMillis, words, caption)
        Text(caption, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LandscapeCard(summary: LogSummary) {
    ChartCard(R.string.log_landscape_title) {
        val first = clockTime(summary.periods.first().startMillis)
        val last = clockTime(summary.periods.last().startMillis)
        val labels = LandscapeLabels(BAND_NAMES.first(), BAND_NAMES[3], BAND_NAMES.last(), first, last)
        NoiseLandscape3D(summary.periods, labels, stringResource(R.string.log_landscape_description, first, last))
        Text(stringResource(R.string.log_landscape_caption), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EventsCard(summary: LogSummary) {
    ChartCard(R.string.log_events_title) {
        if (summary.events.isEmpty()) {
            Body(R.string.log_events_none)
        } else {
            summary.events.takeLast(EVENTS_SHOWN).asReversed().forEach { event ->
                val (start, end) = clockTime(event.startMillis) to clockTime(event.endMillis)
                // Within one minute, "01:51" reads better than "01:51–01:51".
                val time = if (start == end) start else stringResource(R.string.log_event_span, start, end)
                Text(
                    stringResource(R.string.log_event, time, stringResource(event.label.nameRes()), decimals(approxDba(event.maxDba), 0)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(stringResource(R.string.log_events_latest, EVENTS_SHOWN), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun seatLabel(row: Int, col: Int): String = stringResource(R.string.seat_label, row + 1, col + 1)
