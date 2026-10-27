package com.clearroom.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.dsp.Sti
import com.clearroom.app.selftest.ClapOutcome
import com.clearroom.app.selftest.RefusalKind
import com.clearroom.app.selftest.SelfTestCase
import com.clearroom.app.selftest.SelfTestResult
import com.clearroom.app.ui.SelfTestState
import com.clearroom.app.viz.decimals
import kotlin.math.roundToInt

/**
 * The self-test (doc "Analysis methods" step 4): the measuring maths run on made-up claps and rooms
 * with known answers, on this phone, with no microphone. Failures always show; the passes on request.
 */
@Composable
internal fun SelfTestCard(state: SelfTestState, onRun: () -> Unit) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    ChartCard(R.string.selftest_title) {
        Body(R.string.selftest_intro)
        when {
            state.running -> {
                Text(stringResource(R.string.selftest_running, state.results.size, state.total), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { state.results.size / state.total.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    strokeCap = StrokeCap.Round,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
            state.finished -> {
                val summary = if (state.failed == 0) R.string.selftest_all_passed else R.string.selftest_some_failed
                Text(stringResource(summary, state.total - state.failed, state.total), style = MaterialTheme.typography.titleMedium)
                state.slowestClapMillis?.let { Text(stringResource(R.string.selftest_speed, it.toInt()), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        if (!state.running) {
            if (state.results.isEmpty()) {
                Button(onClick = onRun) { Text(stringResource(R.string.selftest_run)) }
            } else {
                OutlinedButton(onClick = onRun) { Text(stringResource(R.string.selftest_again)) }
            }
        }
        if (state.results.isNotEmpty()) {
            val shown = if (showAll) state.results else state.results.filterNot { it.passed }
            SelfTestGroups(shown)
            TextButton(onClick = { showAll = !showAll }) {
                Text(stringResource(if (showAll) R.string.selftest_hide_passed else R.string.selftest_show_all))
            }
        }
    }
}

@Composable
private fun SelfTestGroups(results: List<SelfTestResult>) {
    val groups = listOf(
        R.string.selftest_group_clarity to results.filter { it.case is SelfTestCase.Clarity },
        R.string.selftest_group_echo to results.filter { it.case is SelfTestCase.Echo },
        R.string.selftest_group_refusal to results.filter { it.case is SelfTestCase.Refusal },
    )
    groups.filter { it.second.isNotEmpty() }.forEach { (title, rows) ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            rows.forEach { SelfTestRow(it) }
        }
    }
}

@Composable
private fun SelfTestRow(result: SelfTestResult) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // A word as well as a mark, so the verdict never rests on colour alone.
        val verdict = stringResource(if (result.passed) R.string.selftest_pass else R.string.selftest_fail)
        val colour = if (result.passed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
        Text(verdict, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = colour)
        Text(selfTestRowText(result), style = MaterialTheme.typography.bodyMedium)
    }
}

/** One result in words: what was checked, what came out, and what was expected. */
@Composable
internal fun selfTestRowText(result: SelfTestResult): String = when (val case = result.case) {
    is SelfTestCase.Clarity -> {
        val (got, expected) = decimals(requireNotNull(result.value), 3) to decimals(case.expectedSti, 3)
        if (case.sameInEveryBand) {
            val snr = case.snrDb.first()
            val noise = if (snr == Sti.NO_NOISE_DB) stringResource(R.string.selftest_no_noise) else stringResource(R.string.selftest_snr, signedDb(snr))
            stringResource(R.string.selftest_clarity_row, decimals(case.t60S.first(), 1), noise, got, expected)
        } else {
            stringResource(R.string.selftest_clarity_mixed, got, expected)
        }
    }
    is SelfTestCase.Echo -> {
        val measured = result.value?.let { decimals(it, 2) } ?: stringResource(R.string.selftest_no_value)
        stringResource(R.string.selftest_echo_row, decimals(case.t60S, 1), case.floorBelowPeakDb.roundToInt(), measured)
    }
    is SelfTestCase.Refusal -> stringResource(
        R.string.selftest_refusal_row,
        stringResource(case.kind.textRes()),
        stringResource(requireNotNull(result.outcome).textRes()),
    )
}

private fun signedDb(value: Double): String = (if (value > 0) "+" else "") + decimals(value, 0)

@StringRes
private fun RefusalKind.textRes(): Int = when (this) {
    RefusalKind.CLIPPED -> R.string.selftest_kind_clipped
    RefusalKind.SILENCE -> R.string.selftest_kind_silence
    RefusalKind.STEADY_NOISE -> R.string.selftest_kind_steady_noise
    RefusalKind.BARELY_ABOVE_NOISE -> R.string.selftest_kind_barely_above_noise
    RefusalKind.ECHO_TOO_LONG -> R.string.selftest_kind_echo_too_long
}

@StringRes
private fun ClapOutcome.textRes(): Int = when (this) {
    ClapOutcome.ACCEPTED -> R.string.selftest_outcome_accepted
    ClapOutcome.CLIPPED -> R.string.selftest_outcome_clipped
    ClapOutcome.SILENT -> R.string.selftest_outcome_silent
    ClapOutcome.NO_CLAP -> R.string.selftest_outcome_no_clap
    ClapOutcome.CLAP_LOUDER -> R.string.selftest_outcome_clap_louder
    ClapOutcome.TOO_NOISY -> R.string.selftest_outcome_too_noisy
    ClapOutcome.UNCLEAR -> R.string.selftest_outcome_unclear
}
