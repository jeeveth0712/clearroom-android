package com.clearroom.app.ui.screens

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.acoustics.Rt60Summary
import com.clearroom.app.audio.CaptureProblem
import com.clearroom.app.audio.ClipQuality
import com.clearroom.app.data.FanState
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.Session
import com.clearroom.app.measure.ClapProblem
import com.clearroom.app.measure.ClapTake
import com.clearroom.app.measure.MeasureKind
import com.clearroom.app.permission.MicAccess
import com.clearroom.app.permission.rememberMicPermission
import com.clearroom.app.ui.AppTab
import com.clearroom.app.ui.MeasurePhase
import com.clearroom.app.ui.MeasureUiState
import com.clearroom.app.ui.UiEvent
import com.clearroom.app.ui.theme.ClearRoomTheme

/** Measure tab: microphone access, then where the phone is and what to measure there. */
@Composable
fun MeasureScreen(
    session: Session?,
    measure: MeasureUiState,
    liveLevelDb: () -> Double,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mic = rememberMicPermission()
    MeasureContent(
        micAccess = mic.access,
        session = session,
        measure = measure,
        liveLevelDb = liveLevelDb,
        onAllowMic = mic.request,
        onOpenSettings = mic.openSettings,
        onEvent = onEvent,
        modifier = modifier,
    )
}

@Composable
fun MeasureContent(
    micAccess: MicAccess,
    session: Session?,
    measure: MeasureUiState,
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
        ScreenHeader(AppTab.MEASURE, R.string.measure_intro)
        when (micAccess) {
            MicAccess.NOT_ASKED -> MicAccessCard(R.string.mic_why, R.string.mic_allow, onAllowMic)
            MicAccess.DENIED_CAN_ASK_AGAIN -> MicAccessCard(R.string.mic_denied_can_ask_again, R.string.mic_allow, onAllowMic)
            MicAccess.DENIED_PERMANENTLY ->
                MicAccessCard(R.string.mic_denied_permanently, R.string.mic_open_settings, onOpenSettings)
            MicAccess.GRANTED -> when {
                session == null -> InfoCard { BodyText(R.string.measure_no_room) }
                session.room.id == SampleData.ROOM_ID -> InfoCard { BodyText(R.string.measure_sample_room) }
                else -> Measurements(session, measure, liveLevelDb, onEvent)
            }
        }
    }
}

@Composable
private fun Measurements(session: Session, measure: MeasureUiState, liveLevelDb: () -> Double, onEvent: (UiEvent) -> Unit) {
    val busy = measure.phase.isBusy
    if (session.baseline != null) InfoCard { BodyText(R.string.measure_remeasuring) }
    StatusCard(measure.phase, liveLevelDb, onCancel = { onEvent(UiEvent.CancelRecording) })

    InfoCard {
        Text(stringResource(R.string.measure_seat_title), style = MaterialTheme.typography.titleMedium)
        BodyText(R.string.measure_seat_hint)
        SeatPicker(
            rows = session.room.rows,
            cols = session.room.cols,
            measured = session.seats.filter { it.hasReadings }.map { it.seat }.toSet(),
            selected = measure.selectedSeat,
            onSelect = { onEvent(UiEvent.SelectSeat(it)) },
        )
        Text(
            measure.selectedSeat?.let { seatLabel(it) } ?: stringResource(R.string.measure_seat_none),
            style = MaterialTheme.typography.titleSmall,
        )
        val selected = measure.selectedSeat
        if (selected != null && session.measureAt(selected)?.hasReadings == true) {
            ClearSeatButton(selected, enabled = !busy, onConfirm = { onEvent(UiEvent.ClearSeat(selected)) })
        }
    }

    EchoCard(session, measure, onEvent)

    InfoCard {
        Text(stringResource(R.string.measure_noise_title), style = MaterialTheme.typography.titleMedium)
        BodyText(R.string.measure_noise_hint)
        Text(stringResource(R.string.measure_fans_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FanState.entries.forEach { fans ->
                FilterChip(
                    selected = fans == measure.fans,
                    onClick = { onEvent(UiEvent.SetFans(fans)) },
                    label = { Text(stringResource(fans.labelRes())) },
                )
            }
        }
        Button(
            onClick = { onEvent(UiEvent.StartMeasurement(MeasureKind.NOISE)) },
            enabled = measure.selectedSeat != null && !busy,
        ) { Text(stringResource(R.string.measure_noise_start, MeasureKind.NOISE.durationS)) }
        val seatMeasure = measure.selectedSeat?.let(session::measureAt)
        val noise = seatMeasure?.noise
        if (noise != null) {
            val fans = seatMeasure.fans?.let { stringResource(it.labelRes()) } ?: "?"
            Text(stringResource(R.string.measure_noise_result, fans), style = MaterialTheme.typography.titleSmall)
            BandLevelsTable(noise)
        }
    }

    InfoCard {
        Text(stringResource(R.string.measure_voice_title), style = MaterialTheme.typography.titleMedium)
        BodyText(R.string.measure_voice_hint)
        Text(stringResource(R.string.measure_voice_paragraph), style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
        Button(onClick = { onEvent(UiEvent.StartMeasurement(MeasureKind.VOICE)) }, enabled = !busy) {
            Text(stringResource(R.string.measure_voice_start, MeasureKind.VOICE.durationS))
        }
        session.voice?.let { voice ->
            Text(stringResource(R.string.measure_voice_result), style = MaterialTheme.typography.titleSmall)
            BandLevelsTable(voice)
        }
    }
}

/** Deleting readings can't be undone, so it asks first. */
@Composable
private fun ClearSeatButton(seat: SeatId, enabled: Boolean, onConfirm: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    TextButton(onClick = { asking = true }, enabled = enabled) { Text(stringResource(R.string.measure_seat_clear)) }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.measure_seat_clear_title, seatLabel(seat))) },
            text = { Text(stringResource(R.string.measure_seat_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    onConfirm()
                }) { Text(stringResource(R.string.measure_seat_clear_confirm)) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.measure_seat_clear_keep)) } },
        )
    }
}

/** FR-2/FR-3: three claps per seat, then the seat's echo time per band against the classroom target. */
@Composable
private fun EchoCard(session: Session, measure: MeasureUiState, onEvent: (UiEvent) -> Unit) {
    val seat = measure.selectedSeat
    val takes = seat?.let(session::measureAt)?.rtTakes.orEmpty()
    val summary = Rt60Summary.of(takes)
    InfoCard {
        Text(stringResource(R.string.measure_clap_title), style = MaterialTheme.typography.titleMedium)
        BodyText(R.string.measure_clap_hint)
        Text(stringResource(R.string.measure_clap_delay_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MeasureUiState.CLAP_DELAYS_S.forEach { seconds ->
                FilterChip(
                    selected = seconds == measure.clapDelayS,
                    onClick = { onEvent(UiEvent.SetClapDelay(seconds)) },
                    label = { Text(stringResource(R.string.measure_clap_delay_option, seconds)) },
                )
            }
        }
        Button(onClick = { onEvent(UiEvent.StartMeasurement(MeasureKind.CLAP)) }, enabled = seat != null && !measure.phase.isBusy) {
            Text(stringResource(R.string.measure_clap_start, takes.size, ClapTake.TAKES_PER_SEAT))
        }
        if (seat != null && summary != null) {
            Text(stringResource(R.string.measure_rt_title), style = MaterialTheme.typography.titleSmall)
            Rt60Table(summary)
            Text(
                stringResource(R.string.measure_rt_speech, summary.speechRt60S, Rt60Summary.CLASSROOM_TARGET_S),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (summary.bands.any { it.estimated }) {
                Text(stringResource(R.string.measure_rt_estimated), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { onEvent(UiEvent.ClearClaps(seat)) }, enabled = !measure.phase.isBusy) {
                Text(stringResource(R.string.measure_rt_clear))
            }
        }
    }
}

@Composable
private fun StatusCard(phase: MeasurePhase, liveLevelDb: () -> Double, onCancel: () -> Unit) {
    InfoCard {
        Text(stringResource(R.string.measure_level_label), style = MaterialTheme.typography.labelMedium)
        LevelBar(liveLevelDb)
        val status = when (phase) {
            MeasurePhase.Idle -> stringResource(R.string.measure_ready)
            is MeasurePhase.Countdown -> stringResource(R.string.measure_countdown, phase.secondsLeft)
            is MeasurePhase.Recording -> when (phase.kind) {
                MeasureKind.NOISE -> stringResource(R.string.measure_recording_noise, phase.kind.durationS)
                MeasureKind.VOICE -> stringResource(R.string.measure_recording_voice, phase.kind.durationS)
                MeasureKind.CLAP -> stringResource(R.string.measure_recording_clap)
            }
            is MeasurePhase.Saved -> when (phase.kind) {
                MeasureKind.VOICE -> stringResource(R.string.measure_saved_voice)
                // Claps report through ClapAccepted; NOISE and CLAP both name the seat.
                MeasureKind.NOISE, MeasureKind.CLAP ->
                    stringResource(R.string.measure_saved_noise, phase.seat?.let { seatLabel(it) }.orEmpty())
            }
            is MeasurePhase.ClapAccepted -> stringResource(
                R.string.measure_clap_saved,
                phase.takes,
                ClapTake.TAKES_PER_SEAT,
                seatLabel(phase.seat),
                phase.speechRt60S,
            )
            is MeasurePhase.ClapRejected -> stringResource(phase.problem.messageRes())
            is MeasurePhase.Rejected -> stringResource(phase.quality.messageRes(phase.kind))
            is MeasurePhase.Failed -> stringResource(phase.problem.messageRes())
            MeasurePhase.SaveFailed -> stringResource(R.string.measure_save_failed)
        }
        Text(status, style = MaterialTheme.typography.bodyLarge)
        if (phase.isBusy) OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.measure_cancel)) }
    }
}

@Composable
internal fun MicAccessCard(@StringRes message: Int, @StringRes action: Int, onAction: () -> Unit) {
    InfoCard {
        BodyText(message)
        Button(onClick = onAction) { Text(stringResource(action)) }
    }
}

@Composable
private fun BodyText(@StringRes text: Int) = Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)

/** People count rows and seats from 1, the front row being row 1. */
@Composable
private fun seatLabel(seat: SeatId): String = stringResource(R.string.seat_label, seat.row + 1, seat.col + 1)

@StringRes
internal fun FanState.labelRes(): Int = when (this) {
    FanState.OFF -> R.string.fans_off
    FanState.SLOW -> R.string.fans_slow
    FanState.FAST -> R.string.fans_fast
}

@StringRes
private fun ClipQuality.messageRes(kind: MeasureKind): Int = when (this) {
    ClipQuality.CLIPPED -> if (kind == MeasureKind.CLAP) R.string.quality_clipped_clap else R.string.quality_clipped
    // A measurement is never rejected as OK; listed so the `when` stays exhaustive.
    ClipQuality.SILENT, ClipQuality.OK -> R.string.quality_silent
}

@StringRes
private fun ClapProblem.messageRes(): Int = when (this) {
    ClapProblem.NO_CLAP -> R.string.clap_problem_no_clap
    ClapProblem.CLAP_LOUDER -> R.string.clap_problem_louder
    ClapProblem.TOO_NOISY -> R.string.clap_problem_noisy
    ClapProblem.UNCLEAR -> R.string.clap_problem_unclear
}

@StringRes
internal fun CaptureProblem.messageRes(): Int = when (this) {
    CaptureProblem.MIC_UNAVAILABLE -> R.string.problem_mic_unavailable
    CaptureProblem.PERMISSION_REVOKED -> R.string.problem_permission_revoked
    CaptureProblem.READ_FAILED -> R.string.problem_read_failed
    CaptureProblem.IN_USE_BY_DAY_LOG -> R.string.problem_day_log_running
}

private class MeasurePreviews : PreviewParameterProvider<Pair<MicAccess, MeasureUiState>> {
    override val values = sequenceOf(
        MicAccess.NOT_ASKED to MeasureUiState(),
        MicAccess.GRANTED to MeasureUiState(selectedSeat = SeatId(2, 3), fans = FanState.FAST),
        MicAccess.GRANTED to MeasureUiState(selectedSeat = SeatId(0, 0), phase = MeasurePhase.Countdown(MeasureKind.NOISE, 2)),
    )
}

@Preview(showBackground = true, heightDp = 1600)
@Preview(showBackground = true, heightDp = 1600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MeasureContentPreview(@PreviewParameter(MeasurePreviews::class) case: Pair<MicAccess, MeasureUiState>) {
    ClearRoomTheme {
        MeasureContent(
            micAccess = case.first,
            session = SampleData.session.withRoom(SampleData.room.copy(id = "mine")),
            measure = case.second,
            liveLevelDb = { -40.0 },
            onAllowMic = {},
            onOpenSettings = {},
            onEvent = {},
        )
    }
}
