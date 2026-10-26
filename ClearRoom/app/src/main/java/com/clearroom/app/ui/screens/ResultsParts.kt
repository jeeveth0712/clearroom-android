package com.clearroom.app.ui.screens

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.acoustics.Cause
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.ui.theme.color
import com.clearroom.app.viz.decimals

// Pieces the Results and Fixes tabs share: cards, the band-with-range line, the band legend, and
// the plain-word names of bands and causes (NFR-2).

/** "Fair · 0.55–0.61" next to the band's colour: the verdict with its uncertainty range (NFR-10). */
@Composable
internal fun BandRange(band: StiBand, low: Double, high: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(14.dp).background(band.color(), RoundedCornerShape(3.dp)))
        Text(rangeText(band, low, high), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

/** "Fair · 0.55–0.61", or "Fair · 0.59" when the range is narrower than the digits shown. */
@Composable
internal fun rangeText(band: StiBand, low: Double, high: Double): String = rangeText(LocalResources.current, band, low, high)

/** [rangeText] outside Compose, for the report (FR-12). */
fun rangeText(resources: Resources, band: StiBand, low: Double, high: Double): String {
    val (lowText, highText) = decimals(low, 2) to decimals(high, 2)
    val name = resources.getString(band.labelRes())
    return if (lowText == highText) {
        resources.getString(R.string.results_band_value, name, lowText)
    } else {
        resources.getString(R.string.results_band_range, name, lowText, highText)
    }
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

@Composable
internal fun ChartCard(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    InfoCard {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
internal fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun Body(@StringRes text: Int) = Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)

/**
 * A button that asks before it acts: [button] draws it with the click that opens the question,
 * and [onConfirm] runs only on [confirm]. For changes that are hard to undo.
 */
@Composable
internal fun ConfirmFirst(
    @StringRes title: Int,
    body: String,
    @StringRes confirm: Int,
    @StringRes dismiss: Int,
    onConfirm: () -> Unit,
    button: @Composable (onClick: () -> Unit) -> Unit,
) {
    var asking by remember { mutableStateOf(false) }
    button { asking = true }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(title)) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    onConfirm()
                }) { Text(stringResource(confirm)) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(dismiss)) } },
        )
    }
}
