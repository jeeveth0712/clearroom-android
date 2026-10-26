package com.clearroom.app.ui.screens

import androidx.annotation.StringRes
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.acoustics.ClarityResult
import com.clearroom.app.acoustics.MissingInput
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.data.Session
import com.clearroom.app.ui.AppTab
import com.clearroom.app.ui.Evaluation
import com.clearroom.app.ui.theme.color
import com.clearroom.app.viz.ClarityRoom3D

/** Results tab. M7: the 3D clarity room (V8); the verdict card, heatmaps and charts follow in M8. */
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
            clarity is ClarityResult.Ready -> InfoCard {
                Text(stringResource(R.string.results_3d_title), style = MaterialTheme.typography.titleMedium)
                val description = stringResource(
                    R.string.results_3d_description,
                    stringResource(StiBand.from(clarity.room.frontRowSti).labelRes()),
                    stringResource(StiBand.from(clarity.room.backRowSti).labelRes()),
                )
                ClarityRoom3D(evaluation.session.room, clarity.room, description)
                BandLegend()
                Text(stringResource(R.string.results_3d_caption), style = MaterialTheme.typography.bodySmall)
                if (!clarity.room.voiceMeasured) Body(R.string.results_uncalibrated)
            }
        }
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

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun Body(@StringRes text: Int) = Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
