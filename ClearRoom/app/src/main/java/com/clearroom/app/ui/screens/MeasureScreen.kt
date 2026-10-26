package com.clearroom.app.ui.screens

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.audio.AudioSourceKind
import com.clearroom.app.audio.CaptureProblem
import com.clearroom.app.audio.ClipQuality
import com.clearroom.app.audio.RecordingSummary
import com.clearroom.app.permission.MicAccess
import com.clearroom.app.permission.rememberMicPermission
import com.clearroom.app.ui.AppTab
import com.clearroom.app.ui.MeasurePhase
import com.clearroom.app.ui.MeasureUiState
import com.clearroom.app.ui.UiEvent
import com.clearroom.app.ui.theme.ClearRoomTheme

/** Measure tab: microphone access first, then the microphone test (clap tests arrive in M5). */
@Composable
fun MeasureScreen(
    measure: MeasureUiState,
    liveLevelDb: () -> Double,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mic = rememberMicPermission()
    MeasureContent(
        micAccess = mic.access,
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
    measure: MeasureUiState,
    liveLevelDb: () -> Double,
    onAllowMic: () -> Unit,
    onOpenSettings: () -> Unit,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(AppTab.MEASURE.labelRes), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.measure_intro), style = MaterialTheme.typography.bodyLarge)
        when (micAccess) {
            MicAccess.GRANTED -> RecorderCard(measure.phase, liveLevelDb, onEvent)
            MicAccess.NOT_ASKED -> MicAccessCard(R.string.mic_why, R.string.mic_allow, onAllowMic)
            MicAccess.DENIED_CAN_ASK_AGAIN -> MicAccessCard(R.string.mic_denied_can_ask_again, R.string.mic_allow, onAllowMic)
            MicAccess.DENIED_PERMANENTLY ->
                MicAccessCard(R.string.mic_denied_permanently, R.string.mic_open_settings, onOpenSettings)
        }
    }
}

@Composable
private fun MicAccessCard(@StringRes message: Int, @StringRes action: Int, onAction: () -> Unit) {
    InfoCard {
        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onAction) { Text(stringResource(action)) }
    }
}

@Composable
private fun RecorderCard(phase: MeasurePhase, liveLevelDb: () -> Double, onEvent: (UiEvent) -> Unit) {
    val start = { onEvent(UiEvent.StartTestRecording) }
    val cancel = { onEvent(UiEvent.CancelRecording) }
    InfoCard {
        Text(stringResource(R.string.recorder_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.recorder_level_label), style = MaterialTheme.typography.labelMedium)
        LevelBar(liveLevelDb)
        when (phase) {
            MeasurePhase.Idle -> {
                Text(stringResource(R.string.recorder_idle_hint), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = start) { Text(stringResource(R.string.recorder_start)) }
            }
            is MeasurePhase.Countdown -> {
                Text(stringResource(R.string.recorder_countdown, phase.secondsLeft), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = cancel) { Text(stringResource(R.string.recorder_cancel)) }
            }
            is MeasurePhase.Recording -> {
                Text(stringResource(R.string.recorder_recording, phase.durationS), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = cancel) { Text(stringResource(R.string.recorder_cancel)) }
            }
            is MeasurePhase.Done -> {
                SummaryText(phase.summary)
                Button(onClick = start) { Text(stringResource(R.string.recorder_again)) }
            }
            is MeasurePhase.Failed -> {
                Text(stringResource(phase.problem.messageRes()), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = start) { Text(stringResource(R.string.recorder_again)) }
            }
        }
    }
}

@Composable
private fun SummaryText(summary: RecordingSummary) {
    Text(stringResource(summary.quality.messageRes()), style = MaterialTheme.typography.bodyLarge)
    Text(
        pluralStringResource(
            R.plurals.recorder_summary,
            summary.sampleRate,
            summary.durationS,
            summary.sampleRate,
            stringResource(summary.source.nameRes()),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        stringResource(R.string.recorder_loudness, summary.peakDbFs, summary.averageDbFs),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@StringRes
private fun ClipQuality.messageRes(): Int = when (this) {
    ClipQuality.OK -> R.string.quality_ok
    ClipQuality.CLIPPED -> R.string.quality_clipped
    ClipQuality.SILENT -> R.string.quality_silent
}

@StringRes
private fun CaptureProblem.messageRes(): Int = when (this) {
    CaptureProblem.MIC_UNAVAILABLE -> R.string.problem_mic_unavailable
    CaptureProblem.PERMISSION_REVOKED -> R.string.problem_permission_revoked
    CaptureProblem.READ_FAILED -> R.string.problem_read_failed
}

@StringRes
private fun AudioSourceKind.nameRes(): Int = when (this) {
    AudioSourceKind.UNPROCESSED -> R.string.source_unprocessed
    AudioSourceKind.VOICE_RECOGNITION -> R.string.source_voice_recognition
}

private class MeasurePreviews : PreviewParameterProvider<Pair<MicAccess, MeasurePhase>> {
    override val values = sequenceOf(
        MicAccess.NOT_ASKED to MeasurePhase.Idle,
        MicAccess.DENIED_PERMANENTLY to MeasurePhase.Idle,
        MicAccess.GRANTED to MeasurePhase.Idle,
        MicAccess.GRANTED to MeasurePhase.Countdown(secondsLeft = 2),
        MicAccess.GRANTED to MeasurePhase.Done(
            RecordingSummary(5.0, 48_000, AudioSourceKind.UNPROCESSED, peakDbFs = -9.0, averageDbFs = -42.0, ClipQuality.OK),
        ),
        MicAccess.GRANTED to MeasurePhase.Failed(CaptureProblem.MIC_UNAVAILABLE),
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MeasureContentPreview(@PreviewParameter(MeasurePreviews::class) case: Pair<MicAccess, MeasurePhase>) {
    ClearRoomTheme {
        MeasureContent(
            micAccess = case.first,
            measure = MeasureUiState(phase = case.second),
            liveLevelDb = { -40.0 },
            onAllowMic = {},
            onOpenSettings = {},
            onEvent = {},
        )
    }
}
