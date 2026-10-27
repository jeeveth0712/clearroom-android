package com.clearroom.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.acoustics.FieldChecks
import com.clearroom.app.acoustics.Fixes
import com.clearroom.app.acoustics.Repeatability
import com.clearroom.app.data.ListeningScore
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.Session
import com.clearroom.app.measure.MeasureKind
import com.clearroom.app.ui.MeasureUiState
import com.clearroom.app.ui.UiEvent
import com.clearroom.app.ui.formatMetres
import com.clearroom.app.viz.decimals
import kotlin.math.roundToInt

// The field tests of the doc's testing table that the app works out itself (M13). Each shows the
// test's own pass rule, so the owner can fill in the table from the screen.

/** Echo repeatability, under a seat's echo times: how far its claps spread at speech pitches. */
@Composable
internal fun RepeatabilityLine(repeatability: Repeatability) {
    val percent = (repeatability.worstVariation * 100).roundToInt()
    val text = if (repeatability.passed) R.string.field_repeat_ok else R.string.field_repeat_spread
    Text(stringResource(text, repeatability.claps, percent), style = MaterialTheme.typography.bodyMedium)
}

/** Distance model: the voice recorded 3, 5 and 8 m away against the room model's prediction. */
@Composable
internal fun DistanceCheckCard(session: Session, measure: MeasureUiState, onEvent: (UiEvent) -> Unit) {
    val check = remember(session) { FieldChecks.distanceCheck(session) }
    ChartCard(R.string.field_distance_title) {
        Body(R.string.field_distance_hint)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldChecks.CHECK_DISTANCES_M.forEach { distance ->
                FilterChip(
                    selected = distance == measure.checkDistanceM,
                    onClick = { onEvent(UiEvent.SetCheckDistance(distance)) },
                    label = { Text(stringResource(R.string.field_distance_option, distance.formatMetres())) },
                )
            }
        }
        Button(
            onClick = { onEvent(UiEvent.StartMeasurement(MeasureKind.VOICE_AT_DISTANCE)) },
            enabled = session.voice != null && !measure.phase.isBusy,
        ) { Text(stringResource(R.string.field_distance_start, measure.checkDistanceM.formatMetres(), MeasureKind.VOICE_AT_DISTANCE.durationS.roundToInt())) }
        if (session.voice == null) Body(R.string.field_distance_needs_voice)
        when {
            check != null -> {
                check.points.forEach { point ->
                    Text(
                        stringResource(
                            R.string.field_distance_row,
                            point.distanceM.formatMetres(),
                            decimals(point.predictedDb, 0),
                            decimals(point.measuredDb, 0),
                            signedDb(point.errorDb),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val summary = if (check.passed) R.string.field_distance_ok else R.string.field_distance_off
                Text(stringResource(summary, decimals(check.meanErrorDb, 1)), style = MaterialTheme.typography.titleSmall)
            }
            session.distanceReadings.isNotEmpty() -> Body(R.string.field_distance_needs_room)
        }
    }
}

/** What-if realism, inside the before/after card: the echo the Fixes model predicted for the curtains hung, against the echo measured. */
@Composable
internal fun CurtainCheckSection(before: Session, after: Session) {
    var hungM2 by rememberSaveable(after.room.id) { mutableFloatStateOf(0f) }
    val check = remember(before, after, hungM2) { FieldChecks.curtainCheck(before, after, hungM2.toDouble()) }
    val max = Fixes.maxCurtainM2(after.room).toFloat()
    Text(stringResource(R.string.field_curtain_title), style = MaterialTheme.typography.titleSmall)
    val label = stringResource(R.string.field_curtain_hung, hungM2.roundToInt())
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = hungM2,
        onValueChange = { hungM2 = it.roundToInt().toFloat() },
        valueRange = 0f..max,
        steps = (max.roundToInt() - 1).coerceAtLeast(0),
        colors = SliderDefaults.colors(
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.semantics { contentDescription = label },
    )
    if (check == null) {
        Body(R.string.field_curtain_hint)
    } else {
        Text(
            stringResource(R.string.field_curtain_row, decimals(check.beforeS, 2), decimals(check.predictedS, 2), decimals(check.measuredS, 2)),
            style = MaterialTheme.typography.bodyMedium,
        )
        val summary = if (check.passed) R.string.field_curtain_ok else R.string.field_curtain_off
        Text(stringResource(summary, decimals(check.predictedChangeS, 2), decimals(check.measuredChangeS, 2)), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.field_curtain_note), style = MaterialTheme.typography.bodySmall)
    }
}

/** Listening check, in the seat details: how many of 20 words a listener here wrote down right. */
@Composable
internal fun ListeningScoreField(seat: SeatId, score: ListeningScore?, onScore: (Int?) -> Unit) {
    var typed by rememberSaveable(seat.row, seat.col) { mutableStateOf(score?.wordsRight?.toString().orEmpty()) }
    val number = typed.toIntOrNull()
    val valid = typed.isBlank() || (number != null && number in 0..ListeningScore.WORDS)
    OutlinedTextField(
        value = typed,
        onValueChange = { text ->
            typed = text.filter(Char::isDigit).take(2)
            val value = typed.toIntOrNull()
            when {
                typed.isBlank() -> onScore(null)
                value != null && value in 0..ListeningScore.WORDS -> onScore(value)
            }
        },
        label = { Text(stringResource(R.string.field_listening_field, ListeningScore.WORDS)) },
        singleLine = true,
        isError = !valid,
        supportingText = { Text(stringResource(if (valid) R.string.field_listening_hint else R.string.field_listening_invalid, ListeningScore.WORDS)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/** Listening check: the scored seats against the app's clarity, and whether they agree. */
@Composable
internal fun ListeningCheckCard(session: Session) {
    val check = remember(session) { FieldChecks.listeningCheck(session) }
    ChartCard(R.string.field_listening_title) {
        session.listening.forEach { score ->
            val sti = check?.seats?.firstOrNull { it.score == score }?.sti
            val words = stringResource(R.string.field_listening_row, seatLabelOf(score.seat), score.wordsRight, ListeningScore.WORDS)
            Text(if (sti == null) words else stringResource(R.string.field_listening_row_sti, words, decimals(sti, 2)), style = MaterialTheme.typography.bodyMedium)
        }
        when {
            check == null -> Body(R.string.field_listening_needs_two)
            check.comparedPairs == 0 -> Body(R.string.field_listening_too_close)
            check.passed -> Text(stringResource(R.string.field_listening_ok), style = MaterialTheme.typography.titleSmall)
            else -> Text(stringResource(R.string.field_listening_off, check.disagreements, check.comparedPairs), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun seatLabelOf(seat: SeatId): String = stringResource(R.string.seat_label, seat.row + 1, seat.col + 1)

private fun signedDb(value: Double): String = (if (value > 0) "+" else "") + decimals(value, 0)
