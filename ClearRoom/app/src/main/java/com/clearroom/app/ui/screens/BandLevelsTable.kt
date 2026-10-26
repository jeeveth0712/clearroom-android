package com.clearroom.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.clearroom.app.R
import com.clearroom.app.data.BandLevels
import com.clearroom.app.dsp.OctaveBank
import com.clearroom.app.dsp.OctaveBands
import kotlin.math.roundToInt

/** Octave-band column heading: 125, 250, 500, 1k, 2k, 4k, 8k. */
fun bandLabel(centreHz: Int): String =
    if (centreHz >= 1_000 && centreHz % 1_000 == 0) "${centreHz / 1_000}k" else centreHz.toString()

/** A reading rounded to whole dB: one decimal would suggest more precision than a phone has. */
fun wholeDb(db: Double): String = db.roundToInt().toString()

/** The seven band levels of one reading, plus its approximate A-weighted total. */
@Composable
fun BandLevelsTable(levels: BandLevels, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            OctaveBands.CENTRES_HZ.forEachIndexed { band, hz ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(bandLabel(hz), style = MaterialTheme.typography.labelSmall)
                    Text(wholeDb(levels[band]), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text(
            stringResource(R.string.levels_overall, OctaveBank.aWeightedDb(levels.valuesDb)),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(stringResource(R.string.levels_caption), style = MaterialTheme.typography.bodySmall)
    }
}
